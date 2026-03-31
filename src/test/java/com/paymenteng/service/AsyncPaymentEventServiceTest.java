package com.paymenteng.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AsyncPaymentEventServiceTest {

    @Test
    void publishSettlementRequested_returnsFalseWhenQueueIsFull() {
        RailPaymentService railPaymentService = mock(RailPaymentService.class);
        AsyncPaymentEventService service = new AsyncPaymentEventService(
                railPaymentService,
                1,
                5,
                100,
                2.0,
                1,
                100,
                1
        );

        boolean first = service.publishSettlementRequested(1L);
        boolean second = service.publishSettlementRequested(2L);

        assertThat(first).isTrue();
        assertThat(second).isFalse();
        assertThat(service.queueStats().get("queueDepth")).isEqualTo(1);
        assertThat(service.queueStats().get("queueCapacity")).isEqualTo(1);
    }
}
