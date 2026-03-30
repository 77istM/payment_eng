package com.paymenteng.service;

/**
 * Signals a retriable/transient settlement processing failure.
 */
public class TransientSettlementException extends RuntimeException {

    public TransientSettlementException(String message) {
        super(message);
    }
}
