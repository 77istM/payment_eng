package com.paymenteng.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Ledger account with opening and current balance for settlement simulation.
 */
@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "opening_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal openingBalance;

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance;

    public LedgerAccount() {
    }

    public LedgerAccount(String accountId, BigDecimal openingBalance) {
        this.accountId = accountId;
        this.openingBalance = openingBalance;
        this.currentBalance = openingBalance;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public void setCurrentBalance(BigDecimal currentBalance) {
        this.currentBalance = currentBalance;
    }
}
