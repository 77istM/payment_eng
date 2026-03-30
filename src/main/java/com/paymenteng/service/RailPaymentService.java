package com.paymenteng.service;

import com.paymenteng.model.LedgerAccount;
import com.paymenteng.model.Pain001Instruction;
import com.paymenteng.model.PaymentAuditLog;
import com.paymenteng.model.PaymentLifecycleState;
import com.paymenteng.model.PaymentRail;
import com.paymenteng.model.RailPayment;
import com.paymenteng.parser.Pain001Parser;
import com.paymenteng.repository.LedgerAccountRepository;
import com.paymenteng.repository.PaymentAuditLogRepository;
import com.paymenteng.repository.RailPaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RailPaymentService {

    private static final BigDecimal DEFAULT_DEBTOR_OPENING_BALANCE = new BigDecimal("100000.00");
    private static final BigDecimal DEFAULT_CREDITOR_OPENING_BALANCE = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final RailPaymentRepository railPaymentRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final PaymentAuditLogRepository auditLogRepository;
    private final Pain001Parser pain001Parser;

    public RailPaymentService(RailPaymentRepository railPaymentRepository,
                              LedgerAccountRepository ledgerAccountRepository,
                              PaymentAuditLogRepository auditLogRepository,
                              Pain001Parser pain001Parser) {
        this.railPaymentRepository = railPaymentRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.auditLogRepository = auditLogRepository;
        this.pain001Parser = pain001Parser;
    }

    @Transactional
    public RailPayment initiatePayment(String pain001Xml, String idempotencyKey, PaymentRail rail) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency key is required");
        }

        RailPayment existing = railPaymentRepository.findByIdempotencyKey(idempotencyKey.trim()).orElse(null);
        if (existing != null) {
            logEvent(existing.getId(), "DUPLICATE_DETECTED",
                    "Replayed request with idempotency key " + idempotencyKey.trim());
            return existing;
        }

        Pain001Instruction instruction = pain001Parser.parse(pain001Xml);
        validateInstruction(instruction);

        BigDecimal amount = instruction.getAmount().setScale(2, RoundingMode.HALF_UP);

        RailPayment payment = new RailPayment();
        payment.setIdempotencyKey(idempotencyKey.trim());
        payment.setRail(rail);
        payment.setPaymentReference(nonBlank(instruction.getMessageId(), instruction.getInstructionId(), "UNKNOWN-REF"));
        payment.setInstructionId(nonBlank(instruction.getInstructionId(), "N/A"));
        payment.setEndToEndId(nonBlank(instruction.getEndToEndId(), "N/A"));
        payment.setAmount(amount);
        payment.setCurrency(instruction.getCurrency().toUpperCase());
        payment.setDebtorAccount(instruction.getDebtorAccount());
        payment.setCreditorAccount(instruction.getCreditorAccount());
        payment.setExpectedDebtorDelta(amount.negate());
        payment.setExpectedCreditorDelta(amount);
        payment = railPaymentRepository.save(payment);

        logEvent(payment.getId(), "INITIATED", "Payment initiated on rail " + rail.name());
        transitionState(payment, PaymentLifecycleState.PENDING, "Queued for settlement");
        return payment;
    }

    @Transactional
    public List<RailPayment> processPendingSettlements() {
        List<RailPayment> pendingPayments = railPaymentRepository.findByState(PaymentLifecycleState.PENDING);
        List<RailPayment> processed = new ArrayList<>();
        for (RailPayment payment : pendingPayments) {
            processed.add(processSettlement(payment.getId()));
        }
        return processed;
    }

    @Transactional
    public RailPayment processSettlement(Long paymentId) {
        RailPayment payment = railPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getState() != PaymentLifecycleState.PENDING) {
            return payment;
        }

        LedgerAccount debtor = loadOrCreateDebtor(payment.getDebtorAccount());
        LedgerAccount creditor = loadOrCreateCreditor(payment.getCreditorAccount());

        BigDecimal amount = payment.getAmount().setScale(2, RoundingMode.HALF_UP);
        if (debtor.getCurrentBalance().compareTo(amount) < 0) {
            payment.setExpectedDebtorDelta(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            payment.setExpectedCreditorDelta(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            payment.setActualDebtorDelta(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            payment.setActualCreditorDelta(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            payment.setFailureReason("Insufficient funds in debtor account");
            transitionState(payment, PaymentLifecycleState.FAILED, payment.getFailureReason());
            return payment;
        }

        debtor.setCurrentBalance(debtor.getCurrentBalance().subtract(amount));
        creditor.setCurrentBalance(creditor.getCurrentBalance().add(amount));
        ledgerAccountRepository.save(debtor);
        ledgerAccountRepository.save(creditor);

        payment.setActualDebtorDelta(amount.negate());
        payment.setActualCreditorDelta(amount);
        railPaymentRepository.save(payment);

        logEvent(payment.getId(), "SETTLEMENT_POSTED",
                "Debited " + payment.getDebtorAccount() + " and credited " + payment.getCreditorAccount());

        transitionState(payment, PaymentLifecycleState.SETTLED, "Settlement completed");
        transitionState(payment, PaymentLifecycleState.COMPLETED, "Payment completed");
        return payment;
    }

    @Transactional
    public RailPayment forceLowFundsFailure(Long paymentId, BigDecimal debtorBalance) {
        RailPayment payment = railPaymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (payment.getState() != PaymentLifecycleState.PENDING) {
            throw new IllegalStateException("Payment must be in PENDING state to force failure");
        }

        BigDecimal safeBalance = debtorBalance == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : debtorBalance.setScale(2, RoundingMode.HALF_UP);

        if (safeBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Debtor balance cannot be negative");
        }
        if (safeBalance.compareTo(payment.getAmount()) >= 0) {
            throw new IllegalArgumentException("Debtor balance must be lower than payment amount to force low-funds failure");
        }

        LedgerAccount debtor = loadOrCreateDebtor(payment.getDebtorAccount());
        debtor.setOpeningBalance(safeBalance);
        debtor.setCurrentBalance(safeBalance);
        ledgerAccountRepository.save(debtor);

        logEvent(payment.getId(), "FORCED_LOW_FUNDS",
                "Debtor account " + payment.getDebtorAccount() + " set to balance " + safeBalance);
        return processSettlement(paymentId);
    }

    @Transactional(readOnly = true)
    public RailPayment getPayment(Long id) {
        return railPaymentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<RailPayment> listPayments() {
        return railPaymentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PaymentAuditLog> listAuditLogs(Long paymentId) {
        return auditLogRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> reconcileBalances() {
        List<RailPayment> payments = railPaymentRepository.findAll();
        List<LedgerAccount> accounts = ledgerAccountRepository.findAll();

        Map<String, BigDecimal> expectedDeltaByAccount = new HashMap<>();
        for (RailPayment payment : payments) {
            expectedDeltaByAccount.merge(payment.getDebtorAccount(),
                    normalize(payment.getExpectedDebtorDelta()), BigDecimal::add);
            expectedDeltaByAccount.merge(payment.getCreditorAccount(),
                    normalize(payment.getExpectedCreditorDelta()), BigDecimal::add);
        }

        List<Map<String, Object>> accountResults = new ArrayList<>();
        for (LedgerAccount account : accounts) {
            BigDecimal expectedBalance = account.getOpeningBalance()
                    .add(expectedDeltaByAccount.getOrDefault(account.getAccountId(), BigDecimal.ZERO))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal actualBalance = account.getCurrentBalance().setScale(2, RoundingMode.HALF_UP);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("accountId", account.getAccountId());
            result.put("expectedBalance", expectedBalance);
            result.put("actualBalance", actualBalance);
            result.put("match", expectedBalance.compareTo(actualBalance) == 0);
            accountResults.add(result);
        }

        long mismatches = accountResults.stream()
                .filter(item -> !(Boolean) item.get("match"))
                .count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("timestamp", LocalDateTime.now());
        summary.put("accountsChecked", accountResults.size());
        summary.put("mismatches", mismatches);
        summary.put("results", accountResults);
        return summary;
    }

    private void validateInstruction(Pain001Instruction instruction) {
        if (instruction.getAmount() == null || instruction.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("pain.001 amount must be positive");
        }
        if (instruction.getCurrency() == null || instruction.getCurrency().isBlank()) {
            throw new IllegalArgumentException("pain.001 currency is required");
        }
        if (instruction.getDebtorAccount() == null || instruction.getDebtorAccount().isBlank()) {
            throw new IllegalArgumentException("pain.001 debtor account is required");
        }
        if (instruction.getCreditorAccount() == null || instruction.getCreditorAccount().isBlank()) {
            throw new IllegalArgumentException("pain.001 creditor account is required");
        }
    }

    private LedgerAccount loadOrCreateDebtor(String accountId) {
        return ledgerAccountRepository.findById(accountId)
                .orElseGet(() -> ledgerAccountRepository.save(new LedgerAccount(accountId, DEFAULT_DEBTOR_OPENING_BALANCE)));
    }

    private LedgerAccount loadOrCreateCreditor(String accountId) {
        return ledgerAccountRepository.findById(accountId)
                .orElseGet(() -> ledgerAccountRepository.save(new LedgerAccount(accountId, DEFAULT_CREDITOR_OPENING_BALANCE)));
    }

    private void transitionState(RailPayment payment, PaymentLifecycleState targetState, String details) {
        if (!isValidTransition(payment.getState(), targetState)) {
            throw new IllegalStateException("Invalid transition: " + payment.getState() + " -> " + targetState);
        }

        payment.setState(targetState);
        payment.setUpdatedAt(LocalDateTime.now());
        railPaymentRepository.save(payment);
        logEvent(payment.getId(), "STATE_CHANGE", payment.getState().name() + " - " + details);
    }

    private boolean isValidTransition(PaymentLifecycleState from, PaymentLifecycleState to) {
        return (from == PaymentLifecycleState.INITIATED && to == PaymentLifecycleState.PENDING)
                || (from == PaymentLifecycleState.PENDING && to == PaymentLifecycleState.SETTLED)
                || (from == PaymentLifecycleState.PENDING && to == PaymentLifecycleState.FAILED)
                || (from == PaymentLifecycleState.SETTLED && to == PaymentLifecycleState.COMPLETED);
    }

    private void logEvent(Long paymentId, String eventType, String details) {
        PaymentAuditLog event = new PaymentAuditLog();
        event.setPaymentId(paymentId);
        event.setEventType(eventType);
        event.setDetails(details);
        auditLogRepository.save(event);
    }

    private BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String nonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return fallback;
    }

    private String nonBlank(String first, String fallback) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        return fallback;
    }
}
