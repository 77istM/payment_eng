package com.paymenteng.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Persisted representation of a SEPA/Faster Payments simulated payment.
 */
@Entity
@Table(name = "rail_payments")
public class RailPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_reference", nullable = false)
    private String paymentReference;

    @Column(name = "instruction_id")
    private String instructionId;

    @Column(name = "end_to_end_id")
    private String endToEndId;

    @Enumerated(EnumType.STRING)
    @Column(name = "rail", nullable = false)
    private PaymentRail rail;

    @Column(name = "currency", nullable = false)
    private String currency;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "debtor_account", nullable = false)
    private String debtorAccount;

    @Column(name = "creditor_account", nullable = false)
    private String creditorAccount;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private PaymentLifecycleState state;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "expected_debtor_delta", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedDebtorDelta;

    @Column(name = "expected_creditor_delta", nullable = false, precision = 19, scale = 2)
    private BigDecimal expectedCreditorDelta;

    @Column(name = "actual_debtor_delta", nullable = false, precision = 19, scale = 2)
    private BigDecimal actualDebtorDelta;

    @Column(name = "actual_creditor_delta", nullable = false, precision = 19, scale = 2)
    private BigDecimal actualCreditorDelta;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public RailPayment() {
        this.state = PaymentLifecycleState.INITIATED;
        this.expectedDebtorDelta = BigDecimal.ZERO;
        this.expectedCreditorDelta = BigDecimal.ZERO;
        this.actualDebtorDelta = BigDecimal.ZERO;
        this.actualCreditorDelta = BigDecimal.ZERO;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getInstructionId() {
        return instructionId;
    }

    public void setInstructionId(String instructionId) {
        this.instructionId = instructionId;
    }

    public String getEndToEndId() {
        return endToEndId;
    }

    public void setEndToEndId(String endToEndId) {
        this.endToEndId = endToEndId;
    }

    public PaymentRail getRail() {
        return rail;
    }

    public void setRail(PaymentRail rail) {
        this.rail = rail;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDebtorAccount() {
        return debtorAccount;
    }

    public void setDebtorAccount(String debtorAccount) {
        this.debtorAccount = debtorAccount;
    }

    public String getCreditorAccount() {
        return creditorAccount;
    }

    public void setCreditorAccount(String creditorAccount) {
        this.creditorAccount = creditorAccount;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public PaymentLifecycleState getState() {
        return state;
    }

    public void setState(PaymentLifecycleState state) {
        this.state = state;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public BigDecimal getExpectedDebtorDelta() {
        return expectedDebtorDelta;
    }

    public void setExpectedDebtorDelta(BigDecimal expectedDebtorDelta) {
        this.expectedDebtorDelta = expectedDebtorDelta;
    }

    public BigDecimal getExpectedCreditorDelta() {
        return expectedCreditorDelta;
    }

    public void setExpectedCreditorDelta(BigDecimal expectedCreditorDelta) {
        this.expectedCreditorDelta = expectedCreditorDelta;
    }

    public BigDecimal getActualDebtorDelta() {
        return actualDebtorDelta;
    }

    public void setActualDebtorDelta(BigDecimal actualDebtorDelta) {
        this.actualDebtorDelta = actualDebtorDelta;
    }

    public BigDecimal getActualCreditorDelta() {
        return actualCreditorDelta;
    }

    public void setActualCreditorDelta(BigDecimal actualCreditorDelta) {
        this.actualCreditorDelta = actualCreditorDelta;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
