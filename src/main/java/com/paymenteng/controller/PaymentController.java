package com.paymenteng.controller;

import com.paymenteng.model.MT103Message;
import com.paymenteng.model.PaymentRecord;
import com.paymenteng.service.PaymentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes SWIFT MT103 translation endpoints.
 * <ul>
 *   <li>{@code POST /parse}    – Parse raw MT103 text into structured fields</li>
 *   <li>{@code POST /validate} – Validate a raw MT103 message</li>
 *   <li>{@code POST /convert}  – Convert a raw MT103 message to pacs.008 XML</li>
 *   <li>{@code POST /store}    – Parse, validate, convert, and persist a message</li>
 *   <li>{@code GET  /store}    – List all stored payment records</li>
 *   <li>{@code GET  /store/{id}} – Retrieve a stored record by id</li>
 * </ul>
 */
@RestController
@RequestMapping(produces = MediaType.APPLICATION_JSON_VALUE)
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    /**
     * POST /parse
     * Parses the raw MT103 message body and returns the structured fields as JSON.
     */
    @PostMapping(value = "/parse", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> parse(@RequestBody String rawMessage) {
        try {
            MT103Message msg = service.parse(rawMessage);
            return ResponseEntity.ok(toMap(msg));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /validate
     * Validates the raw MT103 message and returns a list of errors (empty = valid).
     */
    @PostMapping(value = "/validate", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> validate(@RequestBody String rawMessage) {
        try {
            MT103Message msg    = service.parse(rawMessage);
            List<String> errors = service.validate(msg);
            if (errors.isEmpty()) {
                return ResponseEntity.ok(Map.of("valid", true, "errors", List.of()));
            }
            return ResponseEntity.badRequest().body(Map.of("valid", false, "errors", errors));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("valid", false, "errors", List.of(e.getMessage())));
        }
    }

    /**
     * POST /convert
     * Parses the raw MT103 message and returns the ISO 20022 pacs.008 XML.
     */
    @PostMapping(value = "/convert", consumes = MediaType.TEXT_PLAIN_VALUE,
                 produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<?> convert(@RequestBody String rawMessage) {
        try {
            MT103Message msg = service.parse(rawMessage);
            List<String> errors = service.validate(msg);
            if (!errors.isEmpty()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("valid", false, "errors", errors));
            }
            String xml = service.convert(msg);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(xml);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /store
     * Parses, validates, converts, and persists the raw MT103 message.
     * Returns the stored {@link PaymentRecord} as JSON.
     */
    @PostMapping(value = "/store", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<?> store(@RequestBody String rawMessage) {
        try {
            PaymentRecord record = service.store(rawMessage);
            return ResponseEntity.ok(record);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /store
     * Returns all stored payment records.
     */
    @GetMapping(value = "/store")
    public ResponseEntity<List<PaymentRecord>> listAll() {
        return ResponseEntity.ok(service.findAll());
    }

    /**
     * GET /store/{id}
     * Returns a single stored payment record by id.
     */
    @GetMapping(value = "/store/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.findById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, Object> toMap(MT103Message msg) {
        return Map.of(
                "transactionReference",   nullSafe(msg.getTransactionReference()),
                "bankOperationCode",      nullSafe(msg.getBankOperationCode()),
                "valueDate",              nullSafe(msg.getValueDate()),
                "currency",               nullSafe(msg.getCurrency()),
                "amount",                 nullSafe(msg.getAmount()),
                "orderingCustomer",       nullSafe(msg.getOrderingCustomer()),
                "beneficiaryCustomer",    nullSafe(msg.getBeneficiaryCustomer()),
                "detailsOfCharges",       nullSafe(msg.getDetailsOfCharges())
        );
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
