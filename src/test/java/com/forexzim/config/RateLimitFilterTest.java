package com.forexzim.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final String LOGIN = "/admin/login";
    private static final String ALERTS_SUBSCRIBE = "/api/alerts/subscribe";
    private static final String NEWSLETTER_SUBSCRIBE = "/api/newsletter/subscribe";

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter();
    }

    @Test
    void loginRequestsUnderLimitPassThrough() throws Exception {
        for (int i = 1; i <= 10; i++) {
            Result result = doFilter("POST", LOGIN, "10.0.0.1", null);
            assertThat(result.response.getStatus()).as("request %d", i).isEqualTo(200);
            assertThat(result.chainInvoked).as("request %d reaches the chain", i).isTrue();
        }
    }

    @Test
    void eleventhLoginRequestReturns429WithJsonBody() throws Exception {
        for (int i = 0; i < 10; i++) {
            doFilter("POST", LOGIN, "10.0.0.1", null);
        }

        Result result = doFilter("POST", LOGIN, "10.0.0.1", null);

        assertThat(result.response.getStatus()).isEqualTo(429);
        assertThat(result.response.getContentType()).isEqualTo("application/json");
        assertThat(result.response.getContentAsString()).contains("Too many requests");
        assertThat(result.chainInvoked).isFalse();
    }

    @Test
    void ninthAlertsSubscribeRequestReturns429() throws Exception {
        for (int i = 1; i <= 8; i++) {
            Result result = doFilter("POST", ALERTS_SUBSCRIBE, "10.0.0.1", null);
            assertThat(result.response.getStatus()).as("request %d", i).isEqualTo(200);
        }

        Result blocked = doFilter("POST", ALERTS_SUBSCRIBE, "10.0.0.1", null);
        assertThat(blocked.response.getStatus()).isEqualTo(429);
        assertThat(blocked.chainInvoked).isFalse();
    }

    @Test
    void ninthNewsletterSubscribeRequestReturns429() throws Exception {
        for (int i = 1; i <= 8; i++) {
            Result result = doFilter("POST", NEWSLETTER_SUBSCRIBE, "10.0.0.1", null);
            assertThat(result.response.getStatus()).as("request %d", i).isEqualTo(200);
        }

        Result blocked = doFilter("POST", NEWSLETTER_SUBSCRIBE, "10.0.0.1", null);
        assertThat(blocked.response.getStatus()).isEqualTo(429);
    }

    @Test
    void subscribeEndpointsAreCountedSeparately() throws Exception {
        // Exhaust alerts subscribe for this IP
        for (int i = 0; i < 9; i++) {
            doFilter("POST", ALERTS_SUBSCRIBE, "10.0.0.1", null);
        }
        assertThat(doFilter("POST", ALERTS_SUBSCRIBE, "10.0.0.1", null).response.getStatus()).isEqualTo(429);

        // Newsletter subscribe from the same IP is still allowed
        Result newsletter = doFilter("POST", NEWSLETTER_SUBSCRIBE, "10.0.0.1", null);
        assertThat(newsletter.response.getStatus()).isEqualTo(200);
        assertThat(newsletter.chainInvoked).isTrue();
    }

    @Test
    void differentIpsAreTrackedSeparately() throws Exception {
        for (int i = 0; i < 11; i++) {
            doFilter("POST", LOGIN, "10.0.0.1", null);
        }
        assertThat(doFilter("POST", LOGIN, "10.0.0.1", null).response.getStatus()).isEqualTo(429);

        Result otherIp = doFilter("POST", LOGIN, "10.0.0.2", null);
        assertThat(otherIp.response.getStatus()).isEqualTo(200);
        assertThat(otherIp.chainInvoked).isTrue();
    }

    @Test
    void firstXForwardedForEntryIdentifiesTheClient() throws Exception {
        // All requests share the same remote address (e.g. a reverse proxy)
        for (int i = 0; i < 11; i++) {
            doFilter("POST", LOGIN, "172.17.0.1", "203.0.113.5, 172.17.0.1");
        }
        assertThat(doFilter("POST", LOGIN, "172.17.0.1", "203.0.113.5, 172.17.0.1")
                .response.getStatus()).isEqualTo(429);

        // A different forwarded client behind the same proxy is unaffected
        Result other = doFilter("POST", LOGIN, "172.17.0.1", "198.51.100.7");
        assertThat(other.response.getStatus()).isEqualTo(200);
    }

    @Test
    void getRequestsToLimitedPathsAreNeverLimited() throws Exception {
        for (int i = 1; i <= 30; i++) {
            Result result = doFilter("GET", LOGIN, "10.0.0.1", null);
            assertThat(result.response.getStatus()).as("request %d", i).isEqualTo(200);
            assertThat(result.chainInvoked).isTrue();
        }
    }

    @Test
    void unrelatedPostPathsAreNeverLimited() throws Exception {
        for (int i = 1; i <= 30; i++) {
            Result result = doFilter("POST", "/api/rates/refresh", "10.0.0.1", null);
            assertThat(result.response.getStatus()).as("request %d", i).isEqualTo(200);
            assertThat(result.chainInvoked).isTrue();
        }
    }

    private Result doFilter(String method, String uri, String remoteAddr, String xForwardedFor) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        request.setRemoteAddr(remoteAddr);
        if (xForwardedFor != null) {
            request.addHeader("X-Forwarded-For", xForwardedFor);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        return new Result(response, chain.getRequest() != null);
    }

    private record Result(MockHttpServletResponse response, boolean chainInvoked) {
    }
}
