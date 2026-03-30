package com.paymenteng.model;

/**
 * Represents the parsed fields of a SWIFT MT103 message.
 */
public class MT103Message {

    /** :20: Sender's Reference */
    private String transactionReference;

    /** :23B: Bank Operation Code */
    private String bankOperationCode;

    /** :32A: Value Date (YYMMDD) */
    private String valueDate;

    /** :32A: Currency (3-letter ISO code) */
    private String currency;

    /** :32A: Amount */
    private String amount;

    /** :50K: Ordering Customer (sender) */
    private String orderingCustomer;

    /** :52A or :52D: Ordering Institution (optional) */
    private String orderingInstitution;

    /** :57A or :57D: Account With Institution (optional) */
    private String accountWithInstitution;

    /** :59: Beneficiary Customer */
    private String beneficiaryCustomer;

    /** :70: Remittance Information (optional) */
    private String remittanceInformation;

    /** :71A: Details of Charges (BEN/OUR/SHA) */
    private String detailsOfCharges;

    /** Raw message text */
    private String rawMessage;

    public MT103Message() {}

    public String getTransactionReference() { return transactionReference; }
    public void setTransactionReference(String transactionReference) { this.transactionReference = transactionReference; }

    public String getBankOperationCode() { return bankOperationCode; }
    public void setBankOperationCode(String bankOperationCode) { this.bankOperationCode = bankOperationCode; }

    public String getValueDate() { return valueDate; }
    public void setValueDate(String valueDate) { this.valueDate = valueDate; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getOrderingCustomer() { return orderingCustomer; }
    public void setOrderingCustomer(String orderingCustomer) { this.orderingCustomer = orderingCustomer; }

    public String getOrderingInstitution() { return orderingInstitution; }
    public void setOrderingInstitution(String orderingInstitution) { this.orderingInstitution = orderingInstitution; }

    public String getAccountWithInstitution() { return accountWithInstitution; }
    public void setAccountWithInstitution(String accountWithInstitution) { this.accountWithInstitution = accountWithInstitution; }

    public String getBeneficiaryCustomer() { return beneficiaryCustomer; }
    public void setBeneficiaryCustomer(String beneficiaryCustomer) { this.beneficiaryCustomer = beneficiaryCustomer; }

    public String getRemittanceInformation() { return remittanceInformation; }
    public void setRemittanceInformation(String remittanceInformation) { this.remittanceInformation = remittanceInformation; }

    public String getDetailsOfCharges() { return detailsOfCharges; }
    public void setDetailsOfCharges(String detailsOfCharges) { this.detailsOfCharges = detailsOfCharges; }

    public String getRawMessage() { return rawMessage; }
    public void setRawMessage(String rawMessage) { this.rawMessage = rawMessage; }
}
