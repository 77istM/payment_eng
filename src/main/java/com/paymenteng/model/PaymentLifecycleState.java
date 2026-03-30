package com.paymenteng.model;

/**
 * State machine for the rail payment lifecycle.
 */
public enum PaymentLifecycleState {
    INITIATED,
    PENDING,
    SETTLED,
    COMPLETED,
    FAILED
}
