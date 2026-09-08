package com.example.walletprocessor.dto;

import com.example.walletprocessor.entity.enums.TransactionStatus;
import com.example.walletprocessor.entity.enums.TransactionType;
import java.math.BigDecimal;

public class TransactionResponse {

    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private TransactionType type;
    private TransactionStatus status;
    private BigDecimal newBalance;

    public TransactionResponse() {
    }

    public TransactionResponse(String transactionId, String userId, BigDecimal amount, 
                              TransactionType type, TransactionStatus status, BigDecimal newBalance) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.status = status;
        this.newBalance = newBalance;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public TransactionType getType() {
        return type;
    }

    public void setType(TransactionType type) {
        this.type = type;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public BigDecimal getNewBalance() {
        return newBalance;
    }

    public void setNewBalance(BigDecimal newBalance) {
        this.newBalance = newBalance;
    }
}
