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
    private final String correlationId;
    private final LocalDateTime createdAt;

    public PaymentEventMessage(Long paymentId, PaymentEventType eventType, int attempt) {
        this(paymentId, eventType, attempt, null);
    }

    public PaymentEventMessage(Long paymentId, PaymentEventType eventType, int attempt, String correlationId) {
        this.eventId = UUID.randomUUID().toString();
        this.paymentId = paymentId;
        this.eventType = eventType;
        this.attempt = attempt;
        this.correlationId = correlationId;
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

    public String getCorrelationId() {
        return correlationId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
