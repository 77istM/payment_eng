package com.paymenteng.converter;

import com.paymenteng.model.MT103Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class Pacs008ConverterTest {

    private final Pacs008Converter converter = new Pacs008Converter();

    @Test
    void convert_generatesPacs008PayloadWithExpectedFields() {
        MT103Message msg = validMessage();

        String xml = converter.convert(msg);

        assertThat(xml).contains("pacs.008.001.08");
        assertThat(xml).contains("<EndToEndId>TXREF20231001</EndToEndId>");
        assertThat(xml).contains("<IntrBkSttlmAmt Ccy=\"USD\">12500.00</IntrBkSttlmAmt>");
        assertThat(xml).contains("<IntrBkSttlmDt>2023-10-01</IntrBkSttlmDt>");
        assertThat(xml).contains("<ChrgBr>SHAR</ChrgBr>");
    }

    @Test
    void convert_mapsChargeBearerValues() {
        MT103Message msg = validMessage();
        msg.setDetailsOfCharges("OUR");

        String xml = converter.convert(msg);

        assertThat(xml).contains("<ChrgBr>DEBT</ChrgBr>");
    }

    @Test
    void convert_escapesRemittanceXmlCharacters() {
        MT103Message msg = validMessage();
        msg.setRemittanceInformation("invoice <123> & urgent");

        String xml = converter.convert(msg);

        assertThat(xml).contains("invoice &lt;123&gt; &amp; urgent");
    }

    private MT103Message validMessage() {
        MT103Message msg = new MT103Message();
        msg.setTransactionReference("TXREF20231001");
        msg.setValueDate("231001");
        msg.setCurrency("USD");
        msg.setAmount("12500.00");
        msg.setDetailsOfCharges("SHA");
        msg.setOrderingCustomer("/123456789\nJOHN DOE");
        msg.setBeneficiaryCustomer("/987654321\nJANE SMITH");
        return msg;
    }
}
