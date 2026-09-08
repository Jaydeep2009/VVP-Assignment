package com.example.walletprocessor.service;

import com.example.walletprocessor.dto.CreateTransactionRequest;
import com.example.walletprocessor.dto.TransactionResponse;
import com.example.walletprocessor.entity.Transaction;
import com.example.walletprocessor.entity.Wallet;
import com.example.walletprocessor.entity.enums.TransactionStatus;
import com.example.walletprocessor.entity.enums.TransactionType;
import com.example.walletprocessor.exception.DuplicateTransactionInProgressException;
import com.example.walletprocessor.exception.InsufficientFundsException;
import com.example.walletprocessor.exception.WalletNotFoundException;
import com.example.walletprocessor.repository.TransactionRepository;
import com.example.walletprocessor.repository.WalletRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * TransactionService implements database-level concurrency control with two critical fixes:
 * 
 * (a) IDEMPOTENCY: Comes from the unique constraint on transactionId + isolated claim transaction.
 *     The TransactionClaimService.claimTransaction() call uses saveAndFlush() to force immediate
 *     constraint violation detection within an isolated transaction.
 * 
 * (b) BALANCE-SAFETY: walletRepository.findByUserIdForUpdate() acquires PESSIMISTIC_WRITE lock,
 *     serializing concurrent debits against the same wallet to prevent race conditions.
 * 
 * (c) WHY REQUIRES_NEW: The claim uses Propagation.REQUIRES_NEW so that if the unique constraint
 *     is violated, only the inner transaction rolls back, leaving the outer transaction's
 *     Hibernate session clean for the follow-up lookup query. Catching a flush exception in
 *     the same transaction can leave the session in an unusable state.
 * 
 * (d) WHY noRollbackFor: InsufficientFundsException must NOT roll back the transaction, because
 *     we need the FAILED Transaction row to persist permanently. This ensures idempotency —
 *     without it, a rolled-back FAILED attempt would leave no trace, and a retry of the same
 *     transactionId could succeed fresh, violating the 'one resolution per transactionId' guarantee.
 */
@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final TransactionClaimService transactionClaimService;
    private final ObjectMapper objectMapper;

    public TransactionService(TransactionRepository transactionRepository, 
                            WalletRepository walletRepository,
                            TransactionClaimService transactionClaimService,
                            ObjectMapper objectMapper) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.transactionClaimService = transactionClaimService;
        this.objectMapper = objectMapper;
    }

    @Transactional(noRollbackFor = InsufficientFundsException.class)
    public TransactionResponse process(CreateTransactionRequest request) {
        // Step 1: Build a new Transaction entity with status=PENDING
        Transaction transaction = new Transaction(
            request.getTransactionId(),
            request.getUserId(),
            request.getAmount(),
            request.getType(),
            TransactionStatus.PENDING
        );

        // Step 2: Attempt to claim the transaction in an isolated transaction
        // This enforces idempotency via unique constraint on transactionId
        // IMPORTANT: Capture the returned managed entity for all subsequent operations
        try {
            transaction = transactionClaimService.claimTransaction(transaction);
        } catch (DataIntegrityViolationException e) {
            // Step 3: Constraint violation - transaction already exists
            Transaction existing = transactionRepository.findByTransactionId(request.getTransactionId())
                .orElseThrow(() -> new RuntimeException("Transaction constraint violated but not found"));

            if (existing.getStatus() == TransactionStatus.COMPLETED) {
                // Idempotent replay: return stored response (HTTP 200)
                return deserializeResponse(existing.getResponseSnapshot());
            } else {
                // PENDING or FAILED - concurrent or retry of failed transaction (HTTP 409)
                throw new DuplicateTransactionInProgressException(
                    "Transaction " + request.getTransactionId() + " is " + existing.getStatus()
                );
            }
        }

        // Step 4: Claim succeeded, acquire pessimistic write lock on wallet
        // This implements balance-safety by serializing concurrent access
        Wallet wallet = walletRepository.findByUserIdForUpdate(request.getUserId())
            .orElseThrow(() -> new WalletNotFoundException("Wallet not found for user: " + request.getUserId()));

        // Step 5: Check sufficient funds for debit
        if (request.getType() == TransactionType.DEBIT && wallet.getBalance().compareTo(request.getAmount()) < 0) {
            transaction.setStatus(TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            // This exception does NOT roll back thanks to noRollbackFor
            // The FAILED transaction persists permanently for idempotency
            throw new InsufficientFundsException(
                "Insufficient funds. Balance: " + wallet.getBalance() + ", Required: " + request.getAmount()
            );
        }

        // Step 6: Update wallet balance
        BigDecimal newBalance;
        if (request.getType() == TransactionType.DEBIT) {
            newBalance = wallet.getBalance().subtract(request.getAmount());
        } else {
            newBalance = wallet.getBalance().add(request.getAmount());
        }
        wallet.setBalance(newBalance);

        // Mark transaction as completed and store response snapshot
        transaction.setStatus(TransactionStatus.COMPLETED);
        TransactionResponse response = new TransactionResponse(
            transaction.getTransactionId(),
            transaction.getUserId(),
            transaction.getAmount(),
            transaction.getType(),
            TransactionStatus.COMPLETED,
            newBalance
        );
        transaction.setResponseSnapshot(serializeResponse(response));

        // Save both entities
        walletRepository.save(wallet);
        transactionRepository.save(transaction);

        return response;
    }

    private String serializeResponse(TransactionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize response", e);
        }
    }

    private TransactionResponse deserializeResponse(String json) {
        try {
            return objectMapper.readValue(json, TransactionResponse.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize response", e);
        }
    }
}
