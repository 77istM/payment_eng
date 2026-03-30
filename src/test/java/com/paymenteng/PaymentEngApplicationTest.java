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

    static final String VALID_PAIN001 =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
            "<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.03\">" +
            "<CstmrCdtTrfInitn>" +
            "<GrpHdr><MsgId>MSG-1001</MsgId></GrpHdr>" +
            "<PmtInf><PmtInfId>PMTINF-1</PmtInfId>" +
            "<DbtrAcct><Id><IBAN>DE89370400440532013000</IBAN></Id></DbtrAcct>" +
            "<CdtTrfTxInf>" +
            "<PmtId><InstrId>INST-1001</InstrId><EndToEndId>E2E-1001</EndToEndId></PmtId>" +
            "<Amt><InstdAmt Ccy=\"EUR\">250.00</InstdAmt></Amt>" +
            "<CdtrAcct><Id><IBAN>DE44500105175407324931</IBAN></Id></CdtrAcct>" +
            "</CdtTrfTxInf>" +
            "</PmtInf>" +
            "</CstmrCdtTrfInitn>" +
            "</Document>";

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

        @Test
        void rails_initiation_acceptsPain001_andSetsPendingState() throws Exception {
        mockMvc.perform(post("/rails/payments")
                .param("rail", "SEPA")
                .header("Idempotency-Key", "idem-001")
                .contentType(MediaType.APPLICATION_XML)
                .content(VALID_PAIN001))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paymentReference").value("MSG-1001"))
            .andExpect(jsonPath("$.state").value("PENDING"))
            .andExpect(jsonPath("$.rail").value("SEPA"));
        }

        @Test
        void rails_duplicateIdempotencyKey_returnsSamePayment() throws Exception {
        String first = mockMvc.perform(post("/rails/payments")
                .param("rail", "SEPA")
                .header("Idempotency-Key", "idem-dup")
                .contentType(MediaType.APPLICATION_XML)
                .content(VALID_PAIN001))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/rails/payments")
                .param("rail", "SEPA")
                .header("Idempotency-Key", "idem-dup")
                .contentType(MediaType.APPLICATION_XML)
                .content(VALID_PAIN001))
            .andExpect(status().isOk())
            .andExpect(content().json(first));
        }

        @Test
        void rails_settlementAndReconciliation_succeeds() throws Exception {
        mockMvc.perform(post("/rails/payments")
                .param("rail", "FPS")
                .header("Idempotency-Key", "idem-fps-001")
                .contentType(MediaType.APPLICATION_XML)
                .content(VALID_PAIN001))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.rail").value("FASTER_PAYMENTS"));

        mockMvc.perform(post("/rails/settlements/run"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processedCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/rails/payments"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[*].state", org.hamcrest.Matchers.hasItem("COMPLETED")));

        mockMvc.perform(post("/rails/reconciliation/run"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountsChecked").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
            .andExpect(jsonPath("$.mismatches").value(0));
        }

        @Test
        void rails_auditTrail_containsLifecycleEntries() throws Exception {
        String response = mockMvc.perform(post("/rails/payments")
                .header("Idempotency-Key", "idem-audit-001")
                .param("rail", "SEPA")
                .contentType(MediaType.APPLICATION_XML)
                .content(VALID_PAIN001))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id").toString();

        mockMvc.perform(get("/rails/payments/" + id + "/audit"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].eventType").exists())
            .andExpect(jsonPath("$[*].eventType", org.hamcrest.Matchers.hasItem("STATE_CHANGE")));
        }

    @Test
    void rails_forceLowFundsEndpoint_forcesFailedBranch() throws Exception {
        String response = mockMvc.perform(post("/rails/payments")
                        .header("Idempotency-Key", "idem-force-fail-001")
                        .param("rail", "SEPA")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(VALID_PAIN001))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id").toString();

        mockMvc.perform(post("/rails/payments/" + id + "/force-failure/low-funds")
                        .param("debtorBalance", "1.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Insufficient funds in debtor account"));
    }
}
