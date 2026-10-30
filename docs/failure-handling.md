# Failure Handling & Fault Recovery

## 1. Scenario A: Worker Crash Recovery

### The Failure Case
A worker thread reserves an Agent and Borrower, creates a `CallRecord` in `RESERVED` status, and crashes before dispatching to the telecom provider.

### The Recovery Solution
`RecoveryService.recoverStaleReservations(Duration timeout)` executes on a background schedule:
1. Queries all `CallRecord` rows where `status = 'RESERVED'` and `initiatedAt IS NULL` older than 30 seconds.
2. Marks the dead call as `CANCELLED` with `failureReason = 'RECOVERY_WORKER_CRASH_UNINITIATED'`.
3. Atomically releases the reserved agent back to `AVAILABLE`.
4. Releases the borrower lead back to `PENDING` so it can be retried cleanly.

For in-flight calls (`INITIATED` or `RINGING`) older than 60 seconds without a callback:
1. The recovery sweep queries the active provider's status API (`queryCallStatus(providerCallId)`).
2. If the provider confirms the call is ended or failed, the DB is reconciled immediately.
3. If the outcome is unknown, the call is marked `FAILED` with `failureReason = 'RECOVERY_NO_PROVIDER_CONFIRMATION'`.

---

## 2. Scenario B: Telecom Provider Outage

### The Failure Case
Telecom network latency spikes above 2,000ms or carrier error rate exceeds 25%.

### The Recovery Solution
1. **Circuit Breaker (`ProviderCircuitBreaker`):**
   - Monitors rolling window of consecutive telecom failures.
   - 5 consecutive failures trips the breaker to `OPEN`.
2. **Safety Controller Reaction:**
   - Detects `circuitBreaker.isOpen()` or `providerStatus.failureRate() > 0.25`.
   - In Predictive mode: Triggers an immediate `PROGRESSIVE_FALLBACK` or `REJECT`.
   - In Progressive mode: Caps allocations strictly to existing idle agents, avoiding aggressive queueing.
3. **Half-Open Probing:**
   - After a 15-second cool-off window, the circuit breaker enters `HALF_OPEN` and permits a single canary call.
   - If successful, the breaker resets to `CLOSED`. If failed, it reopens for another 15 seconds.

---

## 3. Scenario C: Sudden Drop in Agent Availability

### The Failure Case
40 out of 100 available agents suddenly drop off (e.g. shift change, browser disconnect, network blip).

### The Recovery Solution
1. Predictive dialer pacing recalculates safe capacity based on actual real-time agent counts.
2. The **Safety Controller** authoritatively queries PostgreSQL at decision time and rejects excess calls.
3. At the final transactional boundary, `CallAllocatorService` runs an atomic check:
   If `availableAgents < countToAllocate`, a `SafetyViolationException` aborts the transaction immediately before any outbox event or call record is created.

---

## 4. Scenario D: Duplicate Callbacks

### The Failure Case
A telecom provider sends the `ANSWERED` or `COMPLETED` webhook twice due to network retries.

### The Recovery Solution
- Unique constraint on `provider_events(provider_event_id)`.
- If an event ID is already persisted, the event processor logs a duplicate hit and returns HTTP 200 `DUPLICATE` without mutating any call or agent state.

---

## 5. Scenario E: Out-of-Order Callbacks

### The Failure Case
Due to packet reordering, `RINGING` arrives after `ANSWERED`, or `ANSWERED` arrives after `COMPLETED`.

### The Recovery Solution
`CallStateMachine.isStaleOrDuplicate(currentStatus, targetStatus)` identifies that the call has already progressed past the incoming status:
- A late `RINGING` after `ANSWERED` is ignored.
- A late `ANSWERED` after `COMPLETED` is discarded.
- Terminal call states (`COMPLETED`, `FAILED`, `CANCELLED`) can never be regressed.
