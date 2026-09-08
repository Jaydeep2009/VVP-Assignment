package com.example.walletprocessor.controller;

import com.example.walletprocessor.dto.CreateWalletRequest;
import com.example.walletprocessor.dto.WalletResponse;
import com.example.walletprocessor.entity.Wallet;
import com.example.walletprocessor.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    public ResponseEntity<WalletResponse> createWallet(@Valid @RequestBody CreateWalletRequest request) {
        Wallet wallet = walletService.createWallet(request.getUserId(), request.getInitialBalance());
        WalletResponse response = new WalletResponse(
            wallet.getId(),
            wallet.getUserId(),
            wallet.getBalance(),
            wallet.getCreatedAt()
        );
        return ResponseEntity.ok(response);
    }
}
