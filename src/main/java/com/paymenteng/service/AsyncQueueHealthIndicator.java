package com.paymenteng.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Exposes queue and drain-mode status in actuator health output.
 */
@Component("paymentQueue")
public class AsyncQueueHealthIndicator implements HealthIndicator {

    private final AsyncPaymentEventService asyncPaymentEventService;
    private final AvailabilityGateService availabilityGateService;

    public AsyncQueueHealthIndicator(AsyncPaymentEventService asyncPaymentEventService,
                                     AvailabilityGateService availabilityGateService) {
        this.asyncPaymentEventService = asyncPaymentEventService;
        this.availabilityGateService = availabilityGateService;
    }

    @Override
    public Health health() {
        Map<String, Object> queue = asyncPaymentEventService.queueStats();
        boolean ready = Boolean.TRUE.equals(queue.get("ready"));

        Health.Builder builder = ready ? Health.up() : Health.down();
        return builder
                .withDetail("acceptingTraffic", availabilityGateService.isAcceptingTraffic())
                .withDetail("queue", queue)
                .build();
    }
}
