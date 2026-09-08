package com.example.walletprocessor.service;

import com.example.walletprocessor.entity.Wallet;
import com.example.walletprocessor.repository.WalletRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional
    public Wallet createWallet(String userId, BigDecimal initialBalance) {
        Wallet wallet = new Wallet(userId, initialBalance);
        return walletRepository.save(wallet);
    }
}
