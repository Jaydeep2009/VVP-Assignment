package com.example.walletprocessor.controller;

import com.example.walletprocessor.dto.CreateTransactionRequest;
import com.example.walletprocessor.dto.TransactionResponse;
import com.example.walletprocessor.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/process")
    public ResponseEntity<TransactionResponse> processTransaction(@Valid @RequestBody CreateTransactionRequest request) {
        TransactionResponse response = transactionService.process(request);
        return ResponseEntity.ok(response);
    }
}
