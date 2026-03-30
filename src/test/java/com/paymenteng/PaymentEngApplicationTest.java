package com.paymenteng;

import com.paymenteng.model.MT103Message;
import com.paymenteng.parser.MT103Parser;
import com.paymenteng.validator.MT103Validator;
import com.paymenteng.converter.Pacs008Converter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentEngApplicationTest {

    static final String VALID_MT103 =
            ":20:TXREF20231001\n" +
            ":23B:CRED\n" +
            ":32A:231001USD12500,00\n" +
            ":50K:/123456789\nJOHN DOE\n123 MAIN ST\n" +
            ":59:/987654321\nJANE SMITH\n456 OAK AVE\n" +
            ":71A:SHA\n";

    @Autowired private MockMvc mockMvc;
    @Autowired private MT103Parser parser;
    @Autowired private MT103Validator validator;
    @Autowired private Pacs008Converter converter;

    // -----------------------------------------------------------------------
    // Parser unit tests
    // -----------------------------------------------------------------------

    @Test
    void parser_parsesAllMandatoryFields() {
        MT103Message msg = parser.parse(VALID_MT103);
        assertThat(msg.getTransactionReference()).isEqualTo("TXREF20231001");
        assertThat(msg.getBankOperationCode()).isEqualTo("CRED");
        assertThat(msg.getValueDate()).isEqualTo("231001");
        assertThat(msg.getCurrency()).isEqualTo("USD");
        assertThat(msg.getAmount()).isEqualTo("12500.00");
        assertThat(msg.getBeneficiaryCustomer()).contains("JANE SMITH");
        assertThat(msg.getOrderingCustomer()).contains("JOHN DOE");
        assertThat(msg.getDetailsOfCharges()).isEqualTo("SHA");
    }

    @Test
    void parser_throwsOnBlankInput() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parser_throwsOnNullInput() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // -----------------------------------------------------------------------
    // Validator unit tests
    // -----------------------------------------------------------------------

    @Test
    void validator_acceptsValidMessage() {
        MT103Message msg = parser.parse(VALID_MT103);
        assertThat(validator.validate(msg)).isEmpty();
        assertThat(validator.isValid(msg)).isTrue();
    }

    @Test
    void validator_rejectsMissingReference() {
        MT103Message msg = parser.parse(VALID_MT103);
        msg.setTransactionReference(null);
        List<String> errors = validator.validate(msg);
        assertThat(errors).anyMatch(e -> e.contains(":20:"));
    }

    @Test
    void validator_rejectsMissingBeneficiary() {
        MT103Message msg = parser.parse(VALID_MT103);
        msg.setBeneficiaryCustomer(null);
        List<String> errors = validator.validate(msg);
        assertThat(errors).anyMatch(e -> e.contains(":59:"));
    }

    @Test
    void validator_rejectsInvalidCurrency() {
        MT103Message msg = parser.parse(VALID_MT103);
        msg.setCurrency("US");
        List<String> errors = validator.validate(msg);
        assertThat(errors).anyMatch(e -> e.contains("currency"));
    }

    // -----------------------------------------------------------------------
    // Converter unit tests
    // -----------------------------------------------------------------------

    @Test
    void converter_producesValidPacs008Xml() {
        MT103Message msg = parser.parse(VALID_MT103);
        String xml = converter.convert(msg);
        assertThat(xml).contains("urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08");
        assertThat(xml).contains("FIToFICstmrCdtTrf");
        assertThat(xml).contains("TXREF20231001");
        assertThat(xml).contains("USD");
        assertThat(xml).contains("12500.00");
        assertThat(xml).contains("2023-10-01");
    }

    @Test
    void converter_mapsChargeBearer_SHA_to_SHAR() {
        MT103Message msg = parser.parse(VALID_MT103);
        msg.setDetailsOfCharges("SHA");
        String xml = converter.convert(msg);
        assertThat(xml).contains("<ChrgBr>SHAR</ChrgBr>");
    }

    @Test
    void converter_mapsChargeBearer_OUR_to_DEBT() {
        MT103Message msg = parser.parse(VALID_MT103);
        msg.setDetailsOfCharges("OUR");
        String xml = converter.convert(msg);
        assertThat(xml).contains("<ChrgBr>DEBT</ChrgBr>");
    }

    // -----------------------------------------------------------------------
    // REST endpoint integration tests
    // -----------------------------------------------------------------------

    @Test
    void endpoint_parse_returnsFieldsJson() throws Exception {
        mockMvc.perform(post("/parse")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_MT103))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value("TXREF20231001"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value("12500.00"));
    }

    @Test
    void endpoint_validate_returnsValidTrue_forValidMessage() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_MT103))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void endpoint_validate_returns400_forInvalidMessage() throws Exception {
        String missingRef = VALID_MT103.replace(":20:TXREF20231001\n", "");
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(missingRef))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void endpoint_convert_returnsXml() throws Exception {
        mockMvc.perform(post("/convert")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_MT103))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("pacs.008.001.08")));
    }

    @Test
    void endpoint_store_persistsRecord() throws Exception {
        mockMvc.perform(post("/store")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_MT103))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"))
                .andExpect(jsonPath("$.transactionReference").value("TXREF20231001"));
    }

    @Test
    void endpoint_store_list_returnsRecords() throws Exception {
        // Store something first
        mockMvc.perform(post("/store")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_MT103))
                .andExpect(status().isOk());

        mockMvc.perform(get("/store"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void endpoint_parse_returns400_forBlankBody() throws Exception {
        mockMvc.perform(post("/parse")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
