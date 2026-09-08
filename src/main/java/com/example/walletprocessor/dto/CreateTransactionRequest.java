package com.example.walletprocessor.dto;

import com.example.walletprocessor.entity.enums.TransactionType;
import java.math.BigDecimal;

public class CreateTransactionRequest {

    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private TransactionType type;

    public CreateTransactionRequest() {
    }

    public CreateTransactionRequest(String transactionId, String userId, BigDecimal amount, TransactionType type) {
        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.type = type;
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
}
