package com.example.walletprocessor.service;

import com.example.walletprocessor.entity.Transaction;
import com.example.walletprocessor.repository.TransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * TransactionClaimService isolates the idempotency claim in its own transaction.
 * 
 * Using REQUIRES_NEW ensures the unique constraint violation on transactionId
 * surfaces immediately via saveAndFlush(), and if it fails, only this inner
 * transaction rolls back, leaving the outer transaction's Hibernate session
 * clean for subsequent queries.
 */
@Service
public class TransactionClaimService {

    private final TransactionRepository transactionRepository;

    public TransactionClaimService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Transaction claimTransaction(Transaction transaction) {
        return transactionRepository.saveAndFlush(transaction);
    }
}
