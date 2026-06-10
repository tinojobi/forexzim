package com.forexzim.service;

import com.forexzim.model.AlertSubscription;
import com.forexzim.model.Rate;
import com.forexzim.repository.AlertSubscriptionRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertSubscriptionRepository subscriptionRepository;

    @Mock
    private JavaMailSender mailSender;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(subscriptionRepository, mailSender);
        ReflectionTestUtils.setField(alertService, "mailUsername", "alerts@zimrate.com");
        ReflectionTestUtils.setField(alertService, "fromEmail", "noreply@zimrate.com");
        ReflectionTestUtils.setField(alertService, "baseUrl", "https://zimrate.com");
        lenient().when(mailSender.createMimeMessage())
                .thenAnswer(inv -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void firesWhenRateCrossesAboveThreshold() {
        AlertSubscription sub = subscription("user@example.com", "USD/ZiG", "30.00", "above");
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "34.00")));

        verify(mailSender).send(any(MimeMessage.class));
        verify(subscriptionRepository).save(sub);
        assertThat(sub.getActive()).isFalse();
        assertThat(sub.getLastNotifiedAt()).isNotNull();
    }

    @Test
    void firesWhenRateEqualsThresholdExactly() {
        AlertSubscription sub = subscription("user@example.com", "USD/ZiG", "30.00", "above");
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "30.00")));

        verify(mailSender).send(any(MimeMessage.class));
        assertThat(sub.getActive()).isFalse();
    }

    @Test
    void firesWhenRateCrossesBelowThreshold() {
        AlertSubscription sub = subscription("user@example.com", "USD/ZiG", "30.00", "below");
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "26.50")));

        verify(mailSender).send(any(MimeMessage.class));
        assertThat(sub.getActive()).isFalse();
        assertThat(sub.getLastNotifiedAt()).isNotNull();
    }

    @Test
    void doesNotFireWhenAboveThresholdNotReached() {
        AlertSubscription sub = subscription("user@example.com", "USD/ZiG", "30.00", "above");
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "26.41")));

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(subscriptionRepository, never()).save(any());
        assertThat(sub.getActive()).isTrue();
        assertThat(sub.getLastNotifiedAt()).isNull();
    }

    @Test
    void doesNotFireWhenBelowThresholdNotReached() {
        AlertSubscription sub = subscription("user@example.com", "USD/ZiG", "30.00", "below");
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "34.00")));

        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(subscriptionRepository, never()).save(any());
        assertThat(sub.getActive()).isTrue();
    }

    @Test
    void comparesAgainstBuyRateNotSellRate() {
        AlertSubscription sub = subscription("user@example.com", "USD/ZiG", "30.00", "above");
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        // buy below threshold, sell above — must not fire
        alertService.checkAndNotify(List.of(rate("USD/ZiG", "26.00", "35.00")));

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(sub.getActive()).isTrue();
    }

    @Test
    void doesNotRefireOnceTriggeredAndDeactivated() {
        AlertSubscription sub = subscription("user@example.com", "USD/ZiG", "30.00", "above");
        // First run returns the active subscription; after it is deactivated,
        // findByActiveTrue no longer returns it.
        when(subscriptionRepository.findByActiveTrue())
                .thenReturn(List.of(sub))
                .thenReturn(List.of());

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "34.00")));
        assertThat(sub.getActive()).isFalse();

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "35.00")));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        verify(subscriptionRepository, times(1)).save(sub);
    }

    @Test
    void skipsEverythingWhenMailUsernameNotConfigured() {
        ReflectionTestUtils.setField(alertService, "mailUsername", "");

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "100.00")));

        verifyNoInteractions(subscriptionRepository, mailSender);
    }

    @Test
    void blackMarketSubscriptionUsesBlackMarketRateNotOfficialRate() {
        AlertSubscription officialSub = subscription("official@example.com", "USD/ZiG", "30.00", "above");
        AlertSubscription blackMarketSub = subscription("street@example.com", "USD/ZiG_InformalLow", "30.00", "above");
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(officialSub, blackMarketSub));

        // Official 26.41 (below threshold), black market 34.00 (above threshold)
        alertService.checkAndNotify(List.of(
                rate("USD/ZiG", "26.41"),
                rate("USD/ZiG_InformalLow", "34.00")));

        verify(mailSender, times(1)).send(any(MimeMessage.class));
        ArgumentCaptor<AlertSubscription> saved = ArgumentCaptor.forClass(AlertSubscription.class);
        verify(subscriptionRepository).save(saved.capture());
        assertThat(saved.getValue().getEmail()).isEqualTo("street@example.com");
        assertThat(blackMarketSub.getActive()).isFalse();
        assertThat(officialSub.getActive()).isTrue();
    }

    @Test
    void skipsSubscriptionWhenItsPairIsMissingFromLatestRates() {
        AlertSubscription sub = subscription("user@example.com", "USD/ZiG_Cash", "30.00", "above");
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "99.00")));

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(sub.getActive()).isTrue();
    }

    @Test
    void skipsRateWithNullBuyRate() {
        AlertSubscription sub = subscription("user@example.com", "USD/ZiG", "30.00", "above");
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of(sub));

        Rate nullBuy = rate("USD/ZiG", "99.00");
        nullBuy.setBuyRate(null);

        alertService.checkAndNotify(List.of(nullBuy));

        verify(mailSender, never()).send(any(MimeMessage.class));
        assertThat(sub.getActive()).isTrue();
    }

    @Test
    void doesNothingWhenNoActiveSubscriptions() {
        when(subscriptionRepository.findByActiveTrue()).thenReturn(List.of());

        alertService.checkAndNotify(List.of(rate("USD/ZiG", "99.00")));

        verifyNoInteractions(mailSender);
        verify(subscriptionRepository, never()).save(any());
    }

    private static AlertSubscription subscription(String email, String pair, String threshold, String direction) {
        AlertSubscription sub = new AlertSubscription();
        sub.setId(42L);
        sub.setEmail(email);
        sub.setCurrencyPair(pair);
        sub.setThreshold(new BigDecimal(threshold));
        sub.setDirection(direction);
        sub.setActive(true);
        return sub;
    }

    private static Rate rate(String pair, String buy) {
        return rate(pair, buy, buy);
    }

    private static Rate rate(String pair, String buy, String sell) {
        Rate rate = new Rate();
        rate.setCurrencyPair(pair);
        rate.setBuyRate(new BigDecimal(buy));
        rate.setSellRate(new BigDecimal(sell));
        return rate;
    }
}
