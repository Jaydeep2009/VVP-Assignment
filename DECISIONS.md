# DECISIONS.md

## 1. How did you handle the concurrency race condition?

This service faces two distinct concurrency problems, and each is handled with a different database-level mechanism — no in-JVM locking (`synchronized`, `ReentrantLock`, etc.) is used anywhere, since that only protects a single JVM instance and doesn't hold up if the service is ever scaled to multiple instances or connections.

**Idempotency (duplicate transactionId requests):**

The naive approach — check `existsByTransactionId()`, then insert if not found — has a race window between the check and the insert. Two threads can both see "not found" and both proceed to insert, defeating the whole purpose.

Instead, the insert attempt itself *is* the concurrency control:

1. `transactionId` is the primary key on the `Transaction` table (not a separate unique-checked field), so the database's own constraint is the single source of truth for "has this transactionId been claimed."

2. When a request arrives, the service immediately attempts to insert a new `Transaction` row with status `PENDING`. This claim step runs in a separate `TransactionClaimService` bean, annotated `@Transactional(propagation = Propagation.REQUIRES_NEW)`, using `saveAndFlush()` instead of `save()` so the constraint violation is forced to surface immediately rather than being deferred to a later flush.

3. If the insert succeeds, this thread has "won" the right to process that transactionId — it proceeds to update the wallet balance and mark the transaction `COMPLETED`.

4. If the insert fails with a `DataIntegrityViolationException` (unique/primary key violation), some other thread already claimed this transactionId first. The service fetches the existing row: if it's `COMPLETED`, the original cached response (stored in a `responseSnapshot` column) is replayed back to the caller with HTTP 200; if it's still `PENDING` or `FAILED`, the caller gets HTTP 409.

5. The claim step runs in `REQUIRES_NEW` specifically because catching a flush exception inside the *same* transaction can leave the Hibernate session unusable for the subsequent lookup query. Isolating the claim in its own transaction means a failed claim rolls back cleanly without poisoning the outer transaction that does the lookup-and-respond logic.

6. Because `transactionId` is a manually-assigned (not auto-generated) ID that's already non-null before save, `Transaction` implements `Persistable<String>` with a transient `isNew` flag — otherwise Spring Data JPA would treat every save as an "update" (via `merge()`, which does a SELECT-then-decide) rather than a true INSERT attempt, undermining the whole design.

**Balance-safety (concurrent debits against the same wallet):**

`WalletRepository.findByUserIdForUpdate()` uses `@Lock(LockModeType.PESSIMISTIC_WRITE)` on an explicit `@Query`, which translates to a `SELECT ... FOR UPDATE` at the database level. Once a thread acquires this lock on a wallet row, any other thread trying to read-for-update the same row blocks until the first transaction commits or rolls back. This serializes all balance-affecting operations on a given wallet, so a stale balance can never be read and acted on by two threads simultaneously.

Combined with `@Transactional(noRollbackFor = InsufficientFundsException.class)` on the main processing method, a `FAILED` transaction (insufficient funds) is committed permanently rather than rolled back — this matters for idempotency, because a rolled-back FAILED attempt would leave no trace, and a retry of the same transactionId could then process fresh and succeed, silently violating the "one resolution per transactionId, forever" guarantee.

This design was verified by running the idempotency test (3 identical concurrent transactionIds) and the race-condition test (10 concurrent debits against a ₹500 wallet) 10 times consecutively. All 10 runs produced identical, correct results: exactly 1 success + 2 conflicts in the idempotency test, and exactly 5 successes + 5 insufficient-funds failures with a final balance of exactly ₹0 in the race-condition test.

---

## 2. Where did your AI assistant give an incorrect or sub-optimal suggestion?

Several issues came up during development where the AI's first suggestion needed correction:

**1. Scope creep on the initial scaffold.** When asked to set up the base Spring Boot project, the assistant generated a full CRUD example (`User` entity, controller, service, repository, exceptions) that had nothing to do with the assignment — it defaulted to a generic tutorial pattern instead of an empty scaffold. This was caught before it got committed and the project was restarted with an explicit instruction not to add unrelated sample code.

**2. Using `save()` instead of `saveAndFlush()` for the idempotency claim.** The first version of `TransactionService` called `transactionRepository.save(transaction)` inside the try block that was supposed to catch a constraint violation. With Hibernate, `save()` doesn't guarantee an immediate flush to the database — the actual `INSERT` can be deferred until later in the transaction, meaning the constraint violation might not surface inside the catch block at all, silently breaking the idempotency guarantee under concurrent load. This was fixed by switching to `saveAndFlush()` inside an isolated `REQUIRES_NEW` transaction.

**3. Not accounting for `merge()` vs `persist()` on a manually-assigned ID.** Because `transactionId` is set by the client (not auto-generated) and is non-null before the first save, Spring Data JPA's default logic treats the entity as "not new" and routes the save through `merge()` (a SELECT-then-insert-or-update) rather than a direct `persist()`. This is a subtle JPA behavior the assistant didn't flag on its own — it required explicitly asking whether the entity would reliably attempt a true INSERT. The fix was implementing `Persistable<String>` with a transient `isNew` flag so the framework always attempts `persist()` for a genuinely new transaction.

**4. Rollback of FAILED transactions was an unintended side effect, not a deliberate choice.** The original insufficient-funds branch set `status = FAILED` and saved the transaction, then threw `InsufficientFundsException` — but since `@Transactional`'s default rollback rules apply to unchecked exceptions, this would have silently rolled back the FAILED status write too, meaning the "failed" attempt would leave no trace and the transactionId would remain retryable indefinitely. This wasn't caught by initial testing (the required tests only assert on HTTP status codes, not on DB-level persistence of FAILED rows), and was only surfaced by explicitly reasoning through what "idempotent" should mean for a failed attempt. Fixed with `@Transactional(noRollbackFor = InsufficientFundsException.class)`.

**5. Test client assumed a single response shape for both success and error paths.** The first version of the concurrent integration tests deserialized every HTTP response — success or error — into `TransactionResponse.class`. This broke because the error response body (from `GlobalExceptionHandler`) also has a field named `status`, but it holds a numeric HTTP status code, while `TransactionResponse.status` is a `TransactionStatus` enum (PENDING/COMPLETED/FAILED). Jackson threw a deserialization error trying to map `409` onto a 3-value enum. This wasn't a concurrency bug at all — it was a naive assumption in the test harness that success and error responses share a structure. Fixed by using `exchange()` and inspecting the HTTP status code directly rather than assuming a uniform response DTO.
