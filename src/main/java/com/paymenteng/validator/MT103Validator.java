package com.paymenteng.validator;

import com.paymenteng.model.MT103Message;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates mandatory fields of a parsed {@link MT103Message}.
 * <p>
 * Mandatory MT103 fields: 20, 23B, 32A (valueDate + currency + amount), 50K, 59.
 */
@Component
public class MT103Validator {

    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");
    private static final Pattern DATE_PATTERN      = Pattern.compile("^\\d{6}$");
    private static final Pattern AMOUNT_PATTERN    = Pattern.compile("^\\d+(\\.\\d+)?$");
    private static final Pattern REF_PATTERN       = Pattern.compile("^[A-Za-z0-9/\\-?:().,'+ ]{1,16}$");

    /**
     * Validates the message and returns a list of error messages.
     * An empty list means the message is valid.
     */
    public List<String> validate(MT103Message msg) {
        List<String> errors = new ArrayList<>();

        // Field :20: Transaction Reference
        if (isBlank(msg.getTransactionReference())) {
            errors.add("Field :20: (Transaction Reference) is mandatory");
        } else if (!REF_PATTERN.matcher(msg.getTransactionReference()).matches()) {
            errors.add("Field :20: contains invalid characters or exceeds 16 characters");
        }

        // Field :23B: Bank Operation Code
        if (isBlank(msg.getBankOperationCode())) {
            errors.add("Field :23B: (Bank Operation Code) is mandatory");
        }

        // Field :32A: Value Date
        if (isBlank(msg.getValueDate())) {
            errors.add("Field :32A: (Value Date) is mandatory");
        } else if (!DATE_PATTERN.matcher(msg.getValueDate()).matches()) {
            errors.add("Field :32A: value date must be in YYMMDD format");
        }

        // Field :32A: Currency
        if (isBlank(msg.getCurrency())) {
            errors.add("Field :32A: (Currency) is mandatory");
        } else if (!CURRENCY_PATTERN.matcher(msg.getCurrency()).matches()) {
            errors.add("Field :32A: currency must be a 3-letter ISO code");
        }

        // Field :32A: Amount
        if (isBlank(msg.getAmount())) {
            errors.add("Field :32A: (Amount) is mandatory");
        } else if (!AMOUNT_PATTERN.matcher(msg.getAmount()).matches()) {
            errors.add("Field :32A: amount must be a positive numeric value");
        }

        // Field :50K: Ordering Customer (sender)
        if (isBlank(msg.getOrderingCustomer())) {
            errors.add("Field :50K: (Ordering Customer / Sender) is mandatory");
        }

        // Field :59: Beneficiary Customer
        if (isBlank(msg.getBeneficiaryCustomer())) {
            errors.add("Field :59: (Beneficiary Customer) is mandatory");
        }

        return errors;
    }

    /** Returns {@code true} if the message is valid (no errors). */
    public boolean isValid(MT103Message msg) {
        return validate(msg).isEmpty();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
