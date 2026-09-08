package com.example.walletprocessor.exception;

public class DuplicateTransactionInProgressException extends RuntimeException {
    
    public DuplicateTransactionInProgressException(String message) {
        super(message);
    }
}
