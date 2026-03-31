package com.paymenteng.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AsyncPaymentEventServiceTest {

    @Test
    void publishSettlementRequested_returnsFalseWhenQueueIsFull() {
        RailPaymentService railPaymentService = mock(RailPaymentService.class);
        AvailabilityGateService availabilityGateService = mock(AvailabilityGateService.class);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        AsyncPaymentEventService service = new AsyncPaymentEventService(
                railPaymentService,
                availabilityGateService,
                meterRegistry,
                1,
                5,
                100,
                2.0,
                1,
                100,
                1,
                0.8
        );

        for (long i = 1; i <= 100; i++) {
            assertThat(service.publishSettlementRequested(i)).isTrue();
        }
        boolean next = service.publishSettlementRequested(101L);

        assertThat(next).isFalse();
        assertThat(service.queueStats().get("queueDepth")).isEqualTo(100);
        assertThat(service.queueStats().get("queueCapacity")).isEqualTo(100);
    }
}
