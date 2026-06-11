package com.forexzim.service;

import com.forexzim.model.PushSubscription;
import com.forexzim.model.Rate;
import com.forexzim.model.Source;
import com.forexzim.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebPushServiceTest {

    @Mock
    private PushSubscriptionRepository subscriptionRepository;

    private WebPushService webPushService;

    @BeforeEach
    void setUp() {
        webPushService = new WebPushService(subscriptionRepository);
        ReflectionTestUtils.setField(webPushService, "vapidPublicKey", "test-public");
        ReflectionTestUtils.setField(webPushService, "vapidPrivateKey", "test-private");
        ReflectionTestUtils.setField(webPushService, "baseUrl", "https://zimrate.com");
    }

    private static Rate officialRate(String value) {
        Source source = new Source();
        source.setName("ZimPriceCheck");
        Rate rate = new Rate();
        rate.setSource(source);
        rate.setCurrencyPair("USD/ZiG");
        rate.setBuyRate(new BigDecimal(value));
        return rate;
    }

    @Test
    void notConfiguredWithoutKeys() {
        ReflectionTestUtils.setField(webPushService, "vapidPublicKey", "");
        assertThat(webPushService.isConfigured()).isFalse();
        // and notifyRateMove must be a silent no-op
        webPushService.notifyRateMove(List.of(officialRate("27.00")));
        verify(subscriptionRepository, never()).findByActiveTrue();
    }

    @Test
    void subscribeCreatesNewSubscription() {
        when(subscriptionRepository.findByEndpoint("https://push.example/ep1"))
                .thenReturn(Optional.empty());

        webPushService.subscribe("https://push.example/ep1", "key-p256dh", "key-auth");

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(captor.capture());
        PushSubscription saved = captor.getValue();
        assertThat(saved.getEndpoint()).isEqualTo("https://push.example/ep1");
        assertThat(saved.getP256dh()).isEqualTo("key-p256dh");
        assertThat(saved.getAuth()).isEqualTo("key-auth");
        assertThat(saved.getActive()).isTrue();
    }

    @Test
    void subscribeReactivatesExistingEndpoint() {
        PushSubscription existing = new PushSubscription();
        existing.setEndpoint("https://push.example/ep1");
        existing.setP256dh("old");
        existing.setAuth("old");
        existing.setActive(false);
        when(subscriptionRepository.findByEndpoint("https://push.example/ep1"))
                .thenReturn(Optional.of(existing));

        webPushService.subscribe("https://push.example/ep1", "new-p256dh", "new-auth");

        verify(subscriptionRepository).save(existing);
        assertThat(existing.getActive()).isTrue();
        assertThat(existing.getP256dh()).isEqualTo("new-p256dh");
    }

    @Test
    void unsubscribeDeactivates() {
        PushSubscription existing = new PushSubscription();
        existing.setEndpoint("https://push.example/ep1");
        existing.setActive(true);
        when(subscriptionRepository.findByEndpoint("https://push.example/ep1"))
                .thenReturn(Optional.of(existing));

        assertThat(webPushService.unsubscribe("https://push.example/ep1")).isTrue();
        assertThat(existing.getActive()).isFalse();
        assertThat(webPushService.unsubscribe("https://push.example/unknown")).isFalse();
    }

    @Test
    void firstScrapeOnlySetsBaselineWithoutPushing() {
        webPushService.notifyRateMove(List.of(officialRate("27.00")));
        verify(subscriptionRepository, never()).findByActiveTrue();
    }

    @Test
    void smallMoveDoesNotPush() {
        webPushService.notifyRateMove(List.of(officialRate("27.00")));   // baseline
        webPushService.notifyRateMove(List.of(officialRate("27.10")));   // +0.37% < 1%
        verify(subscriptionRepository, never()).findByActiveTrue();
    }

    @Test
    void bigMoveTriggersSendToActiveSubscriptions() {
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of());

        webPushService.notifyRateMove(List.of(officialRate("27.00")));   // baseline
        webPushService.notifyRateMove(List.of(officialRate("27.50")));   // +1.85% ≥ 1%

        verify(subscriptionRepository).findByActiveTrue();
    }
}
