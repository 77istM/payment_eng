package com.paymenteng.controller;

import com.paymenteng.model.PaymentRail;
import com.paymenteng.model.RailPayment;
import com.paymenteng.service.AsyncPaymentEventService;
import com.paymenteng.service.AvailabilityGateService;
import com.paymenteng.service.RailPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RailSimulatorControllerTest {

    private static final String VALID_PAIN001 =
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

    private RailPaymentService railPaymentService;
    private AsyncPaymentEventService asyncPaymentEventService;
    private AvailabilityGateService availabilityGateService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        railPaymentService = org.mockito.Mockito.mock(RailPaymentService.class);
        asyncPaymentEventService = org.mockito.Mockito.mock(AsyncPaymentEventService.class);
        availabilityGateService = org.mockito.Mockito.mock(AvailabilityGateService.class);

        RailSimulatorController controller = new RailSimulatorController(
                railPaymentService,
                asyncPaymentEventService,
                availabilityGateService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void initiatePayment_returns503WhenQueueIsFull() throws Exception {
        RailPayment payment = new RailPayment();
        payment.setId(101L);
        payment.setRail(PaymentRail.SEPA);
        payment.setPaymentReference("MSG-1001");

        when(availabilityGateService.isAcceptingTraffic()).thenReturn(true);
        when(railPaymentService.initiatePayment(anyString(), anyString(), any(PaymentRail.class))).thenReturn(payment);
        when(asyncPaymentEventService.publishSettlementRequested(101L)).thenReturn(false);

        mockMvc.perform(post("/rails/payments")
                        .param("rail", "SEPA")
                        .header("Idempotency-Key", "idem-queue-full")
                        .contentType("application/xml")
                        .content(VALID_PAIN001))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("Settlement queue is full, retry later"));
    }
}
