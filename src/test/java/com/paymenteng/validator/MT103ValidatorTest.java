package com.paymenteng.validator;

import com.paymenteng.model.MT103Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MT103ValidatorTest {

    private final MT103Validator validator = new MT103Validator();

    @Test
    void validate_returnsNoErrorsForValidMessage() {
        MT103Message message = validMessage();

        assertThat(validator.validate(message)).isEmpty();
        assertThat(validator.isValid(message)).isTrue();
    }

    @Test
    void validate_rejectsInvalidCurrency() {
        MT103Message message = validMessage();
        message.setCurrency("US");

        assertThat(validator.validate(message))
                .anyMatch(error -> error.contains("currency must be a 3-letter ISO code"));
    }

    @Test
    void validate_rejectsMissingBeneficiary() {
        MT103Message message = validMessage();
        message.setBeneficiaryCustomer(" ");

        assertThat(validator.validate(message))
                .anyMatch(error -> error.contains("Field :59:"));
    }

    private MT103Message validMessage() {
        MT103Message msg = new MT103Message();
        msg.setTransactionReference("REF123456789");
        msg.setBankOperationCode("CRED");
        msg.setValueDate("231001");
        msg.setCurrency("USD");
        msg.setAmount("12500.00");
        msg.setOrderingCustomer("/123456789\nJOHN DOE");
        msg.setBeneficiaryCustomer("/987654321\nJANE SMITH");
        return msg;
    }
}
