package com.paymenteng.parser;

import com.paymenteng.model.Pain001Instruction;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

/**
 * Parses a minimal subset of pain.001 fields required by the simulator.
 */
@Component
public class Pain001Parser {

    public Pain001Instruction parse(String xml) {
        if (xml == null || xml.isBlank()) {
            throw new IllegalArgumentException("pain.001 payload must not be empty");
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);

            Document document = factory.newDocumentBuilder()
                    .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            XPath xPath = XPathFactory.newInstance().newXPath();

            Pain001Instruction instruction = new Pain001Instruction();
            instruction.setMessageId(text(xPath, document, "//*[local-name()='GrpHdr']/*[local-name()='MsgId']"));
            instruction.setInstructionId(text(xPath, document, "//*[local-name()='CdtTrfTxInf']/*[local-name()='PmtId']/*[local-name()='InstrId']"));
            instruction.setEndToEndId(text(xPath, document, "//*[local-name()='CdtTrfTxInf']/*[local-name()='PmtId']/*[local-name()='EndToEndId']"));
            instruction.setDebtorAccount(text(xPath, document, "//*[local-name()='DbtrAcct']/*[local-name()='Id']/*[local-name()='IBAN']"));
            instruction.setCreditorAccount(text(xPath, document, "//*[local-name()='CdtrAcct']/*[local-name()='Id']/*[local-name()='IBAN']"));
            instruction.setCurrency(attribute(xPath, document,
                    "//*[local-name()='CdtTrfTxInf']/*[local-name()='Amt']/*[local-name()='InstdAmt']", "Ccy"));

            String amountValue = text(xPath, document,
                    "//*[local-name()='CdtTrfTxInf']/*[local-name()='Amt']/*[local-name()='InstdAmt']");
            if (!amountValue.isBlank()) {
                instruction.setAmount(new BigDecimal(amountValue.trim()));
            }
            return instruction;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid pain.001 payload: " + e.getMessage(), e);
        }
    }

    private String text(XPath xPath, Document document, String expression) throws Exception {
        String value = (String) xPath.evaluate(expression, document, XPathConstants.STRING);
        return value != null ? value.trim() : "";
    }

    private String attribute(XPath xPath, Document document, String expression, String attributeName) throws Exception {
        String value = (String) xPath.evaluate(expression + "/@" + attributeName, document, XPathConstants.STRING);
        return value != null ? value.trim() : "";
    }
}
