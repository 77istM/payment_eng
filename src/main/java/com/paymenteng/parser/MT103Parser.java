package com.paymenteng.parser;

import com.paymenteng.model.MT103Message;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw SWIFT MT103 message text into an {@link MT103Message}.
 * <p>
 * MT103 fields use the format {@code :tag:value}.  Field 32A (value date /
 * currency / amount) is a compound field with the format {@code YYMMDDISOAMT}.
 */
@Component
public class MT103Parser {

    // Matches a tagged field block: :TAG: followed by content until the next :TAG: or end
    private static final Pattern FIELD_PATTERN =
            Pattern.compile(":([0-9]{2}[A-Z]?):(.*?)(?=:[0-9]{2}[A-Z]?:|$)", Pattern.DOTALL);

    // :32A: format  YYMMDD + 3-letter currency + amount (comma as decimal separator)
    private static final Pattern FIELD_32A_PATTERN =
            Pattern.compile("^(\\d{6})([A-Z]{3})([\\d,]+)$");

    /**
     * Parses a raw MT103 message string.
     *
     * @param rawMessage the full SWIFT MT103 text
     * @return populated {@link MT103Message}
     * @throws IllegalArgumentException if the message is null/blank or contains no recognisable fields
     */
    public MT103Message parse(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new IllegalArgumentException("MT103 message must not be null or blank");
        }

        Map<String, String> fields = extractFields(rawMessage);
        if (fields.isEmpty()) {
            throw new IllegalArgumentException("No SWIFT tagged fields found in message");
        }

        MT103Message msg = new MT103Message();
        msg.setRawMessage(rawMessage);
        msg.setTransactionReference(fields.get("20"));
        msg.setBankOperationCode(fields.get("23B"));
        msg.setBeneficiaryCustomer(fields.get("59"));
        msg.setOrderingCustomer(fields.get("50K"));
        msg.setOrderingInstitution(fields.get("52A") != null ? fields.get("52A") : fields.get("52D"));
        msg.setAccountWithInstitution(fields.get("57A") != null ? fields.get("57A") : fields.get("57D"));
        msg.setRemittanceInformation(fields.get("70"));
        msg.setDetailsOfCharges(fields.get("71A"));

        String field32a = fields.get("32A");
        if (field32a != null) {
            parse32A(field32a.trim(), msg);
        }

        return msg;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Map<String, String> extractFields(String rawMessage) {
        Map<String, String> fields = new HashMap<>();
        Matcher matcher = FIELD_PATTERN.matcher(rawMessage);
        while (matcher.find()) {
            String tag   = matcher.group(1).trim();
            String value = matcher.group(2).trim();
            fields.put(tag, value);
        }
        return fields;
    }

    private void parse32A(String value, MT103Message msg) {
        Matcher m = FIELD_32A_PATTERN.matcher(value);
        if (m.matches()) {
            msg.setValueDate(m.group(1));
            msg.setCurrency(m.group(2));
            msg.setAmount(m.group(3).replace(",", "."));
        } else {
            // Store whatever is there as-is so validation can reject it properly
            msg.setValueDate(value);
        }
    }
}
