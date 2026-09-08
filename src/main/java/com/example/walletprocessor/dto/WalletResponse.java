package com.example.walletprocessor.dto;

import java.math.BigDecimal;
import java.time.Instant;

public class WalletResponse {

    private Long id;
    private String userId;
    private BigDecimal balance;
    private Instant createdAt;

    public WalletResponse() {
    }

    public WalletResponse(Long id, String userId, BigDecimal balance, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.balance = balance;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
