package com.paymenteng.integration;

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
class Mt103EndpointsIT {

    static final String VALID_MT103 =
            ":20:TXREF20231001\n" +
            ":23B:CRED\n" +
            ":32A:231001USD12500,00\n" +
            ":50K:/123456789\nJOHN DOE\n123 MAIN ST\n" +
            ":59:/987654321\nJANE SMITH\n456 OAK AVE\n" +
            ":71A:SHA\n";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void parse_returnsFieldsJson() throws Exception {
        mockMvc.perform(post("/parse")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_MT103))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionReference").value("TXREF20231001"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.amount").value("12500.00"));
    }

    @Test
    void validate_returnsValidTrue_forValidMessage() throws Exception {
        mockMvc.perform(post("/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_MT103))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void validate_returns400_forInvalidMessage() throws Exception {
        String missingRef = VALID_MT103.replace(":20:TXREF20231001\n", "");

        mockMvc.perform(post("/validate")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(missingRef))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.valid").value(false));
    }

    @Test
    void convert_returnsXml() throws Exception {
        mockMvc.perform(post("/convert")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_MT103))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML));
    }

    @Test
    void store_persistsRecordAndListReturnsRecords() throws Exception {
        mockMvc.perform(post("/store")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(VALID_MT103))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONVERTED"))
                .andExpect(jsonPath("$.transactionReference").value("TXREF20231001"));

        mockMvc.perform(get("/store"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void parse_returns400_forBlankBody() throws Exception {
        mockMvc.perform(post("/parse")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("  "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
