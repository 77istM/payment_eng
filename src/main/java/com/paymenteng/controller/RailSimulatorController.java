package com.paymenteng.controller;

import com.paymenteng.model.PaymentAuditLog;
import com.paymenteng.model.PaymentEventMessage;
import com.paymenteng.model.PaymentRail;
import com.paymenteng.model.RailPayment;
import com.paymenteng.service.AsyncPaymentEventService;
import com.paymenteng.service.AvailabilityGateService;
import com.paymenteng.service.RailPaymentService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rails")
public class RailSimulatorController {

    private final RailPaymentService railPaymentService;
    private final AsyncPaymentEventService asyncPaymentEventService;
    private final AvailabilityGateService availabilityGateService;

    public RailSimulatorController(RailPaymentService railPaymentService,
                                   AsyncPaymentEventService asyncPaymentEventService,
                                   AvailabilityGateService availabilityGateService) {
        this.railPaymentService = railPaymentService;
        this.asyncPaymentEventService = asyncPaymentEventService;
        this.availabilityGateService = availabilityGateService;
    }

    @PostMapping(value = "/payments", consumes = MediaType.APPLICATION_XML_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> initiatePayment(@RequestBody String pain001Xml,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             @RequestParam(name = "rail", defaultValue = "SEPA") String rail) {
        if (!availabilityGateService.isAcceptingTraffic()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Service is draining and refusing new traffic"));
        }

        try {
            PaymentRail selectedRail = parseRail(rail);
            RailPayment payment = railPaymentService.initiatePayment(pain001Xml, idempotencyKey, selectedRail);
            asyncPaymentEventService.publishSettlementRequested(payment.getId());
            return ResponseEntity.accepted().body(payment);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(value = "/settlements/run", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> runSettlementBatch() {
        List<RailPayment> settled = railPaymentService.processPendingSettlements();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("processedCount", settled.size());
        response.put("payments", settled);
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/reconciliation/run", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> runReconciliation() {
        return ResponseEntity.ok(railPaymentService.reconcileBalances());
    }

    @GetMapping(value = "/payments", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<RailPayment>> listPayments() {
        return ResponseEntity.ok(railPaymentService.listPayments());
    }

    @GetMapping(value = "/payments/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPayment(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(railPaymentService.getPayment(id));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping(value = "/payments/{id}/audit", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PaymentAuditLog>> getAuditTrail(@PathVariable Long id) {
        return ResponseEntity.ok(railPaymentService.listAuditLogs(id));
    }

    @PostMapping(value = "/payments/{id}/force-failure/low-funds", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> forceLowFundsFailure(@PathVariable Long id,
                                                  @RequestParam(name = "debtorBalance", defaultValue = "0.00") BigDecimal debtorBalance) {
        try {
            RailPayment payment = railPaymentService.forceLowFundsFailure(id, debtorBalance);
            return ResponseEntity.ok(payment);
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping(value = "/payments/{id}/simulate-transient-failures", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> simulateTransientFailures(@PathVariable Long id,
                                                       @RequestParam(name = "count", defaultValue = "1") int count) {
        if (count < 0) {
            return ResponseEntity.badRequest().body(Map.of("error", "count must be >= 0"));
        }
        railPaymentService.planTransientFailures(id, count);
        return ResponseEntity.ok(Map.of("paymentId", id, "configuredFailures", count));
    }

    @GetMapping(value = "/ops/queue/stats", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> queueStats() {
        return ResponseEntity.ok(asyncPaymentEventService.queueStats());
    }

    @GetMapping(value = "/ops/queue/dlq", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<PaymentEventMessage>> deadLetters() {
        return ResponseEntity.ok(asyncPaymentEventService.deadLetters());
    }

    @PostMapping(value = "/ops/drain/start", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> startDrain() {
        availabilityGateService.startDrain();
        return ResponseEntity.ok(Map.of("acceptingTraffic", availabilityGateService.isAcceptingTraffic()));
    }

    @PostMapping(value = "/ops/drain/stop", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> stopDrain() {
        availabilityGateService.stopDrain();
        return ResponseEntity.ok(Map.of("acceptingTraffic", availabilityGateService.isAcceptingTraffic()));
    }

    @GetMapping(value = "/ops/health", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> operationalHealth() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("acceptingTraffic", availabilityGateService.isAcceptingTraffic());
        response.put("queue", asyncPaymentEventService.queueStats());
        return ResponseEntity.ok(response);
    }

    private PaymentRail parseRail(String rail) {
        if (rail == null || rail.isBlank()) {
            return PaymentRail.SEPA;
        }
        String normalized = rail.trim().toUpperCase();
        if ("FPS".equals(normalized) || "FASTER_PAYMENTS".equals(normalized)) {
            return PaymentRail.FASTER_PAYMENTS;
        }
        if ("SEPA".equals(normalized)) {
            return PaymentRail.SEPA;
        }
        throw new IllegalArgumentException("Unsupported rail: " + rail);
    }
}
