package com.paymenteng.converter;

import com.paymenteng.model.MT103Message;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Converts a parsed {@link MT103Message} into an ISO 20022 pacs.008 XML document.
 * <p>
 * The generated XML conforms to the pacs.008.001.08 schema and covers the
 * essential fields mapped from the MT103 source message.
 */
@Component
public class Pacs008Converter {

    /**
     * Converts the given MT103 message to a pacs.008 XML string.
     *
     * @param msg a parsed (and ideally validated) MT103 message
     * @return pacs.008-compliant XML string
     */
    public String convert(MT103Message msg) {
        String msgId = UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
        String endToEndId = msg.getTransactionReference() != null ? msg.getTransactionReference() : "NOTPROVIDED";
        String instrId    = endToEndId;

        // Reformat value date from YYMMDD → YYYY-MM-DD (assume 2000s)
        String isoDate = formatDate(msg.getValueDate());

        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pacs.008.001.08\">\n");
        xml.append("  <FIToFICstmrCdtTrf>\n");
        xml.append("    <GrpHdr>\n");
        xml.append("      <MsgId>").append(escapeXml(msgId)).append("</MsgId>\n");
        xml.append("      <CreDtTm>").append(java.time.LocalDateTime.now()
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS)).append("</CreDtTm>\n");
        xml.append("      <NbOfTxs>1</NbOfTxs>\n");
        xml.append("      <SttlmInf>\n");
        xml.append("        <SttlmMtd>CLRG</SttlmMtd>\n");
        xml.append("      </SttlmInf>\n");
        xml.append("    </GrpHdr>\n");
        xml.append("    <CdtTrfTxInf>\n");
        xml.append("      <PmtId>\n");
        xml.append("        <InstrId>").append(escapeXml(instrId)).append("</InstrId>\n");
        xml.append("        <EndToEndId>").append(escapeXml(endToEndId)).append("</EndToEndId>\n");
        xml.append("      </PmtId>\n");
        xml.append("      <IntrBkSttlmAmt Ccy=\"").append(escapeXml(nullSafe(msg.getCurrency()))).append("\">")
           .append(escapeXml(nullSafe(msg.getAmount()))).append("</IntrBkSttlmAmt>\n");
        xml.append("      <IntrBkSttlmDt>").append(escapeXml(isoDate)).append("</IntrBkSttlmDt>\n");
        xml.append("      <ChrgBr>").append(escapeXml(mapCharges(msg.getDetailsOfCharges()))).append("</ChrgBr>\n");

        // Debtor (ordering customer / sender)
        xml.append("      <Dbtr>\n");
        xml.append("        <Nm>").append(escapeXml(extractName(msg.getOrderingCustomer()))).append("</Nm>\n");
        xml.append("      </Dbtr>\n");
        xml.append("      <DbtrAcct>\n");
        xml.append("        <Id><Othr><Id>").append(escapeXml(extractAccount(msg.getOrderingCustomer())))
           .append("</Id></Othr></Id>\n");
        xml.append("      </DbtrAcct>\n");

        // Creditor (beneficiary)
        xml.append("      <Cdtr>\n");
        xml.append("        <Nm>").append(escapeXml(extractName(msg.getBeneficiaryCustomer()))).append("</Nm>\n");
        xml.append("      </Cdtr>\n");
        xml.append("      <CdtrAcct>\n");
        xml.append("        <Id><Othr><Id>").append(escapeXml(extractAccount(msg.getBeneficiaryCustomer())))
           .append("</Id></Othr></Id>\n");
        xml.append("      </CdtrAcct>\n");

        // Remittance information (optional)
        if (msg.getRemittanceInformation() != null && !msg.getRemittanceInformation().isBlank()) {
            xml.append("      <RmtInf>\n");
            xml.append("        <Ustrd>").append(escapeXml(msg.getRemittanceInformation())).append("</Ustrd>\n");
            xml.append("      </RmtInf>\n");
        }

        xml.append("    </CdtTrfTxInf>\n");
        xml.append("  </FIToFICstmrCdtTrf>\n");
        xml.append("</Document>\n");

        return xml.toString();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Converts YYMMDD → YYYY-MM-DD (assumes 21st century). */
    private String formatDate(String yymmdd) {
        if (yymmdd == null || yymmdd.length() != 6) {
            return yymmdd != null ? yymmdd : "";
        }
        return "20" + yymmdd.substring(0, 2) + "-" + yymmdd.substring(2, 4) + "-" + yymmdd.substring(4, 6);
    }

    /** Maps MT103 :71A: charge bearer codes to ISO 20022 equivalents. */
    private String mapCharges(String mt103Charges) {
        if (mt103Charges == null) return "SHAR";
        return switch (mt103Charges.toUpperCase()) {
            case "OUR" -> "DEBT";
            case "BEN" -> "CRED";
            default    -> "SHAR"; // SHA → SHAR
        };
    }

    /**
     * Extracts the first line of an MT103 party field as the name.
     * MT103 party fields often start with an account on the first line (e.g. /12345678)
     * and the name on subsequent lines.
     */
    private String extractName(String partyField) {
        if (partyField == null || partyField.isBlank()) return "UNKNOWN";
        String[] lines = partyField.split("\\r?\\n");
        for (String line : lines) {
            if (!line.startsWith("/")) {
                return line.trim();
            }
        }
        return lines[0].trim();
    }

    /** Extracts the account number from a party field (line starting with /). */
    private String extractAccount(String partyField) {
        if (partyField == null || partyField.isBlank()) return "NOTPROVIDED";
        String[] lines = partyField.split("\\r?\\n");
        for (String line : lines) {
            if (line.startsWith("/")) {
                return line.substring(1).trim();
            }
        }
        return "NOTPROVIDED";
    }

    private String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
