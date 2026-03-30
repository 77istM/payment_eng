package com.paymenteng.service;

import com.paymenteng.converter.Pacs008Converter;
import com.paymenteng.model.MT103Message;
import com.paymenteng.model.PaymentRecord;
import com.paymenteng.parser.MT103Parser;
import com.paymenteng.repository.PaymentRepository;
import com.paymenteng.validator.MT103Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Application service that orchestrates parsing, validation, conversion, and storage
 * of SWIFT MT103 messages.
 */
@Service
public class PaymentService {

    private final MT103Parser parser;
    private final MT103Validator validator;
    private final Pacs008Converter converter;
    private final PaymentRepository repository;

    public PaymentService(MT103Parser parser,
                          MT103Validator validator,
                          Pacs008Converter converter,
                          PaymentRepository repository) {
        this.parser     = parser;
        this.validator  = validator;
        this.converter  = converter;
        this.repository = repository;
    }

    /** Parses a raw MT103 message and returns the structured model. */
    public MT103Message parse(String rawMessage) {
        return parser.parse(rawMessage);
    }

    /** Validates a parsed MT103 message; returns a list of error strings (empty = valid). */
    public List<String> validate(MT103Message message) {
        return validator.validate(message);
    }

    /** Converts a parsed MT103 message to ISO 20022 pacs.008 XML. */
    public String convert(MT103Message message) {
        return converter.convert(message);
    }

    /**
     * Parses, validates, converts, and stores a raw MT103 message.
     *
     * @param rawMessage the raw SWIFT MT103 text
     * @return the persisted {@link PaymentRecord}
     * @throws IllegalArgumentException if validation fails
     */
    @Transactional
    public PaymentRecord store(String rawMessage) {
        MT103Message msg = parser.parse(rawMessage);
        List<String> errors = validator.validate(msg);

        PaymentRecord record = new PaymentRecord();
        record.setRawMessage(rawMessage);
        record.setTransactionReference(
                msg.getTransactionReference() != null ? msg.getTransactionReference() : "UNKNOWN");

        if (!errors.isEmpty()) {
            record.setStatus("INVALID");
            record.setIso20022Xml(null);
            return repository.save(record);
        }

        String xml = converter.convert(msg);
        record.setIso20022Xml(xml);
        record.setStatus("CONVERTED");
        return repository.save(record);
    }

    /** Returns all stored payment records. */
    public List<PaymentRecord> findAll() {
        return repository.findAll();
    }

    /** Returns a single stored payment record by its database id. */
    public PaymentRecord findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No record found with id: " + id));
    }
}
