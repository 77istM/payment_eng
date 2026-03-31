package com.paymenteng.integration;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RailsFlowIT {

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

    @Autowired
    private MockMvc mockMvc;

    @Test
    void initiation_acceptsPain001_andSetsPendingState() throws Exception {
        mockMvc.perform(post("/rails/payments")
                        .param("rail", "SEPA")
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(VALID_PAIN001))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.paymentReference").value("MSG-1001"))
                .andExpect(jsonPath("$.state").value("PENDING"))
                .andExpect(jsonPath("$.rail").value("SEPA"));
    }

    @Test
    void duplicateIdempotencyKey_returnsSamePayment() throws Exception {
        String first = mockMvc.perform(post("/rails/payments")
                        .param("rail", "SEPA")
                        .header("Idempotency-Key", "idem-dup")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(VALID_PAIN001))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/rails/payments")
                        .param("rail", "SEPA")
                        .header("Idempotency-Key", "idem-dup")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(VALID_PAIN001))
                .andExpect(status().isAccepted())
                .andExpect(content().json(first));
    }

    @Test
    void settlementAndReconciliation_succeeds() throws Exception {
        mockMvc.perform(post("/rails/payments")
                        .param("rail", "FPS")
                        .header("Idempotency-Key", "idem-fps-001")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(VALID_PAIN001))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.rail").value("FASTER_PAYMENTS"));

        mockMvc.perform(post("/rails/settlements/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processedCount").value(Matchers.greaterThanOrEqualTo(1)));

        mockMvc.perform(get("/rails/payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].state", Matchers.hasItem("COMPLETED")));

        mockMvc.perform(post("/rails/reconciliation/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountsChecked").value(Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.mismatches").value(0));
    }

    @Test
    void auditTrail_containsLifecycleEntries() throws Exception {
        String response = mockMvc.perform(post("/rails/payments")
                        .header("Idempotency-Key", "idem-audit-001")
                        .param("rail", "SEPA")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(VALID_PAIN001))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id").toString();

        mockMvc.perform(get("/rails/payments/" + id + "/audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").exists())
                .andExpect(jsonPath("$[*].eventType", Matchers.hasItem("STATE_CHANGE")));
    }

    @Test
    void forceLowFundsEndpoint_forcesFailedBranch() throws Exception {
        String response = mockMvc.perform(post("/rails/payments")
                        .header("Idempotency-Key", "idem-force-fail-001")
                        .param("rail", "SEPA")
                        .contentType(MediaType.APPLICATION_XML)
                        .content(VALID_PAIN001))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        String id = com.jayway.jsonpath.JsonPath.read(response, "$.id").toString();

        mockMvc.perform(post("/rails/payments/" + id + "/force-failure/low-funds")
                        .param("debtorBalance", "1.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Insufficient funds in debtor account"));
    }
}
