package com.paymenteng.model;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Immutable message envelope for the simulated Kafka-like queue.
 */
public class PaymentEventMessage {

    private final String eventId;
    private final Long paymentId;
    private final PaymentEventType eventType;
    private final int attempt;
    private final LocalDateTime createdAt;

    public PaymentEventMessage(Long paymentId, PaymentEventType eventType, int attempt) {
        this.eventId = UUID.randomUUID().toString();
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.attempt = attempt;
        this.createdAt = LocalDateTime.now();
    }

    public String getEventId() {
        return eventId;
    }

    public Long getPaymentId() {
        return paymentId;
    }

    public PaymentEventType getEventType() {
        return eventType;
    }

    public int getAttempt() {
        return attempt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
