package com.example.walletprocessor;

import com.example.walletprocessor.dto.CreateTransactionRequest;
import com.example.walletprocessor.dto.CreateWalletRequest;
import com.example.walletprocessor.dto.TransactionResponse;
import com.example.walletprocessor.dto.WalletResponse;
import com.example.walletprocessor.entity.Wallet;
import com.example.walletprocessor.entity.enums.TransactionType;
import com.example.walletprocessor.repository.TransactionRepository;
import com.example.walletprocessor.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TransactionProcessingIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    private String getBaseUrl() {
        return "http://localhost:" + port + "/api/v1";
    }

    @AfterEach
    void cleanup() {
        System.out.println("\n=== Cleaning up database ===");
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
        System.out.println("All transactions and wallets deleted\n");
    }

    @Test
    @DisplayName("Processes a single valid debit transaction successfully")
    void testSingleValidDebitTransaction() {
        System.out.println("\n========================================");
        System.out.println("TEST 1: Single Valid Debit Transaction");
        System.out.println("========================================");
        System.out.println("Intent: Create wallet with ₹1000, debit ₹250, verify balance is ₹750");

        // Create wallet with initial balance 1000
        CreateWalletRequest walletRequest = new CreateWalletRequest(UUID.randomUUID().toString(), new BigDecimal("1000"));
        System.out.println("\nStep 1: Creating wallet with userId=" + walletRequest.getUserId() + ", initialBalance=1000");
        
        ResponseEntity<WalletResponse> walletResponse = restTemplate.postForEntity(
            getBaseUrl() + "/wallets",
            walletRequest,
            WalletResponse.class
        );
        
        assertEquals(HttpStatus.OK, walletResponse.getStatusCode());
        assertNotNull(walletResponse.getBody());
        System.out.println("✓ Wallet created successfully - ID: " + walletResponse.getBody().getId() + ", Balance: " + walletResponse.getBody().getBalance());

        String userId = walletResponse.getBody().getUserId();

        // Send debit transaction for 250
        CreateTransactionRequest txRequest = new CreateTransactionRequest(
            UUID.randomUUID().toString(),
            userId,
            new BigDecimal("250"),
            TransactionType.DEBIT
        );
        System.out.println("\nStep 2: Processing debit transaction - TransactionId=" + txRequest.getTransactionId() + ", Amount=250");

        ResponseEntity<TransactionResponse> txResponse = restTemplate.postForEntity(
            getBaseUrl() + "/transactions/process",
            txRequest,
            TransactionResponse.class
        );

        // Assert HTTP 200
        assertEquals(HttpStatus.OK, txResponse.getStatusCode());
        assertNotNull(txResponse.getBody());
        System.out.println("✓ Transaction processed successfully - HTTP Status: " + txResponse.getStatusCode());
        System.out.println("  Response: TransactionId=" + txResponse.getBody().getTransactionId() + 
                         ", Status=" + txResponse.getBody().getStatus() + 
                         ", NewBalance=" + txResponse.getBody().getNewBalance());

        // Assert response balance is 750
        assertEquals(new BigDecimal("750.00"), txResponse.getBody().getNewBalance());
        System.out.println("✓ Response balance assertion passed: Expected=750.00, Actual=" + txResponse.getBody().getNewBalance());

        // Fetch wallet from database and verify persisted balance
        Optional<Wallet> persistedWallet = walletRepository.findByUserId(userId);
        assertTrue(persistedWallet.isPresent());
        assertEquals(new BigDecimal("750.00"), persistedWallet.get().getBalance());
        System.out.println("✓ Persisted wallet balance verified: Expected=750.00, Actual=" + persistedWallet.get().getBalance());

        System.out.println("\n✅ TEST 1 PASSED: All assertions successful");
        System.out.println("========================================\n");
    }

    @Test
    @DisplayName("Sends 3 identical transactionIds simultaneously — balance deducted exactly once")
    void testIdempotency_ThreeIdenticalTransactions() throws InterruptedException, ExecutionException {
        System.out.println("\n========================================");
        System.out.println("TEST 2: Idempotency - 3 Identical Concurrent Requests");
        System.out.println("========================================");
        System.out.println("Intent: Create wallet with ₹1000, send 3 identical transactionIds (debit ₹100) simultaneously, verify balance deducted exactly once (final: ₹900)");

        // Create wallet with balance 1000
        CreateWalletRequest walletRequest = new CreateWalletRequest(UUID.randomUUID().toString(), new BigDecimal("1000"));
        System.out.println("\nStep 1: Creating wallet with userId=" + walletRequest.getUserId() + ", initialBalance=1000");
        
        ResponseEntity<WalletResponse> walletResponse = restTemplate.postForEntity(
            getBaseUrl() + "/wallets",
            walletRequest,
            WalletResponse.class
        );
        
        assertEquals(HttpStatus.OK, walletResponse.getStatusCode());
        String userId = walletResponse.getBody().getUserId();
        System.out.println("✓ Wallet created - ID: " + walletResponse.getBody().getId());

        // Generate ONE transactionId for all 3 requests
        String sharedTransactionId = UUID.randomUUID().toString();
        System.out.println("\nStep 2: Preparing 3 identical requests with shared transactionId=" + sharedTransactionId);

        // Create 3 identical requests
        CreateTransactionRequest txRequest = new CreateTransactionRequest(
            sharedTransactionId,
            userId,
            new BigDecimal("100"),
            TransactionType.DEBIT
        );

        // Setup concurrent execution with CyclicBarrier
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();

        System.out.println("\nStep 3: Launching 3 concurrent threads with CyclicBarrier");
        System.out.println("All threads will wait at barrier and fire simultaneously...");

        for (int i = 0; i < threadCount; i++) {
            final int threadNum = i + 1;
            Future<ResponseEntity<String>> future = executor.submit(() -> {
                try {
                    System.out.println("  Thread-" + threadNum + " waiting at barrier...");
                    barrier.await(); // Wait until all threads reach this point
                    System.out.println("  Thread-" + threadNum + " FIRING request!");
                    
                    ResponseEntity<String> response = restTemplate.postForEntity(
                        getBaseUrl() + "/transactions/process",
                        txRequest,
                        String.class
                    );
                    
                    System.out.println("  Thread-" + threadNum + " received response: HTTP " + response.getStatusCode());
                    return response;
                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    System.out.println("  Thread-" + threadNum + " received error: HTTP " + e.getStatusCode());
                    return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Collect all responses
        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (Future<ResponseEntity<String>> future : futures) {
            responses.add(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("✓ All threads completed");

        // Analyze responses
        System.out.println("\nStep 4: Analyzing responses...");
        long successCount = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long conflictCount = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.CONFLICT).count();
        
        System.out.println("  HTTP 200 (OK) responses: " + successCount);
        System.out.println("  HTTP 409 (CONFLICT) responses: " + conflictCount);

        // Assert exactly one successful response
        assertEquals(1, successCount, "Expected exactly 1 successful transaction");
        System.out.println("✓ Exactly 1 transaction succeeded");

        // Verify other responses are 409
        assertEquals(2, conflictCount, "Expected exactly 2 conflict responses");
        System.out.println("✓ Exactly 2 requests returned HTTP 409 (Conflict)");

        // Verify final wallet balance is exactly 900
        Optional<Wallet> finalWallet = walletRepository.findByUserId(userId);
        assertTrue(finalWallet.isPresent());
        assertEquals(new BigDecimal("900.00"), finalWallet.get().getBalance());
        System.out.println("\n✓ Final persisted wallet balance verified: Expected=900.00, Actual=" + finalWallet.get().getBalance());

        System.out.println("\n✅ TEST 2 PASSED: Idempotency maintained - balance deducted exactly once");
        System.out.println("========================================\n");
    }

    @Test
    @DisplayName("Sends 10 concurrent debits of ₹100 against a ₹500 wallet — final balance is exactly ₹0, 5 succeed, 5 fail with insufficient funds")
    void testConcurrentDebits_InsufficientFundsRaceCondition() throws InterruptedException, ExecutionException {
        System.out.println("\n========================================");
        System.out.println("TEST 3: Concurrent Debits with Insufficient Funds");
        System.out.println("========================================");
        System.out.println("Intent: Create wallet with ₹500, fire 10 concurrent debits of ₹100 each (distinct transactionIds), verify exactly 5 succeed and 5 fail, final balance=₹0");

        // Create wallet with balance 500
        CreateWalletRequest walletRequest = new CreateWalletRequest(UUID.randomUUID().toString(), new BigDecimal("500"));
        System.out.println("\nStep 1: Creating wallet with userId=" + walletRequest.getUserId() + ", initialBalance=500");
        
        ResponseEntity<WalletResponse> walletResponse = restTemplate.postForEntity(
            getBaseUrl() + "/wallets",
            walletRequest,
            WalletResponse.class
        );
        
        assertEquals(HttpStatus.OK, walletResponse.getStatusCode());
        String userId = walletResponse.getBody().getUserId();
        System.out.println("✓ Wallet created - ID: " + walletResponse.getBody().getId());

        // Generate 10 DISTINCT transaction requests
        int threadCount = 10;
        List<CreateTransactionRequest> requests = new ArrayList<>();
        System.out.println("\nStep 2: Preparing 10 distinct debit requests (₹100 each):");
        for (int i = 0; i < threadCount; i++) {
            CreateTransactionRequest request = new CreateTransactionRequest(
                UUID.randomUUID().toString(),
                userId,
                new BigDecimal("100"),
                TransactionType.DEBIT
            );
            requests.add(request);
            System.out.println("  Request-" + (i + 1) + ": TransactionId=" + request.getTransactionId());
        }

        // Setup concurrent execution with CyclicBarrier
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        AtomicInteger threadCounter = new AtomicInteger(0);

        System.out.println("\nStep 3: Launching 10 concurrent threads with CyclicBarrier");
        System.out.println("All threads will wait at barrier and fire simultaneously...");

        for (CreateTransactionRequest request : requests) {
            Future<ResponseEntity<String>> future = executor.submit(() -> {
                try {
                    int threadNum = threadCounter.incrementAndGet();
                    System.out.println("  Thread-" + threadNum + " waiting at barrier...");
                    barrier.await(); // Wait until all threads reach this point
                    System.out.println("  Thread-" + threadNum + " FIRING request!");
                    
                    ResponseEntity<String> response = restTemplate.postForEntity(
                        getBaseUrl() + "/transactions/process",
                        request,
                        String.class
                    );
                    
                    System.out.println("  Thread-" + threadNum + " received response: HTTP " + response.getStatusCode());
                    return response;
                } catch (org.springframework.web.client.HttpClientErrorException e) {
                    int threadNum = threadCounter.get();
                    System.out.println("  Thread-" + threadNum + " received error: HTTP " + e.getStatusCode());
                    return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            futures.add(future);
        }

        // Collect all responses
        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (Future<ResponseEntity<String>> future : futures) {
            responses.add(future.get());
        }

        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println("✓ All threads completed");

        // Analyze responses
        System.out.println("\nStep 4: Analyzing responses...");
        long successCount = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.OK).count();
        long failureCount = responses.stream().filter(r -> r.getStatusCode() == HttpStatus.BAD_REQUEST).count();
        
        System.out.println("  HTTP 200 (OK - successful debits): " + successCount);
        System.out.println("  HTTP 400 (BAD_REQUEST - insufficient funds): " + failureCount);

        // Detailed response breakdown
        int successNum = 0, failureNum = 0;
        for (ResponseEntity<String> response : responses) {
            if (response.getStatusCode() == HttpStatus.OK) {
                successNum++;
                System.out.println("  Success-" + successNum + ": HTTP 200");
            } else if (response.getStatusCode() == HttpStatus.BAD_REQUEST) {
                failureNum++;
                System.out.println("  Failure-" + failureNum + ": HTTP 400 (Insufficient funds)");
            }
        }

        // Assert exactly 5 successes and 5 failures
        assertEquals(5, successCount, "Expected exactly 5 successful debits");
        assertEquals(5, failureCount, "Expected exactly 5 failed debits due to insufficient funds");
        System.out.println("\n✓ Exactly 5 transactions succeeded");
        System.out.println("✓ Exactly 5 transactions failed with insufficient funds");

        // Verify final wallet balance is exactly 0
        Optional<Wallet> finalWallet = walletRepository.findByUserId(userId);
        assertTrue(finalWallet.isPresent());
        assertEquals(new BigDecimal("0.00"), finalWallet.get().getBalance());
        System.out.println("✓ Final persisted wallet balance verified: Expected=0.00, Actual=" + finalWallet.get().getBalance());

        System.out.println("\n✅ TEST 3 PASSED: Race condition handled correctly - exactly 5 debits succeeded, balance is ₹0");
        System.out.println("========================================\n");
    }
}
