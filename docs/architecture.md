# SmartDialer Architecture Specification

## 1. Executive Summary

SmartDialer is a concurrency-safe, high-throughput outbound calling system designed for debt collection platforms. It strikes an optimal balance between agent utilization and strict compliance safety.

The fundamental design principle is:
> **Zero Unassigned Connected Calls:** No connected borrower call may ever exist without an eligible assigned agent.

All dialing recommendations—whether progressive or predictive—are strictly gated by an **Independent Safety Controller** that authoritatively inspects the database before any call allocation or telecom dispatch occurs.

---

## 2. System Architecture Diagram

```mermaid
graph TD
    subgraph Campaign Management
        CM[Campaign Manager]
        CS[Campaign Service]
    end

    subgraph Dialing Strategy Layer
        PDS[Progressive Dialer Service<br/>1:1 Agent Safe Bound]
        PPE[Predictive Pacing Engine<br/>Statistical EWMA Recommendation]
    end

    subgraph Critical Safety Gate
        ISC[INDEPENDENT SAFETY CONTROLLER<br/>Authoritative DB Validation<br/>Approve | Reduce | Reject | Fallback]
    end

    subgraph Transactional Allocation Layer
        TCA[Transactional Call Allocator<br/>Atomic Agent & Borrower Reservation]
        OB[Transactional Outbox Pattern<br/>Atomic DB Event Log]
    end

    subgraph Telecom Provider Interface
        ODS[Outbox Dispatcher<br/>Asynchronous Non-Blocking HTTP]
        TPR[Telecom Provider Registry / Circuit Breaker]
        MPA[Mock Provider A<br/>Fast & Reliable]
        MPB[Mock Provider B<br/>Jitter, Timeouts & Failures]
    end

    subgraph Asynchronous Event Processing
        EVP[Idempotent Provider Event Processor<br/>Monotonic State Machine Protection]
    end

    subgraph Storage & Audit
        PG[(PostgreSQL Source of Truth<br/>Atomic Row Updates & Versioning)]
    end

    CM --> CS
    CS --> PDS
    CS --> PPE
    PDS --> ISC
    PPE -.->|Advisory Proposal Only| ISC
    ISC -->|Authorized Quota Only| TCA
    TCA -->|Atomic Commit| PG
    TCA -->|Store Event| OB
    OB --> ODS
    ODS --> TPR
    TPR --> MPA
    TPR --> MPB
    MPA -.->|Webhook Events| EVP
    MPB -.->|Webhook Events| EVP
    EVP -->|Idempotent State Update| PG
```

---

## 3. Core Architectural Principles

### 3.1 Modular Monolith
SmartDialer is built as a modular monolith in Java 21 and Spring Boot 3.3.4. 
- Eliminates distributed transaction complexity (2PC / Sagas) across microservice boundaries.
- Keeps latency low (in-memory invocation between dialer, safety controller, and allocator).
- Ensures atomic ACID guarantees in PostgreSQL for agent and borrower reservations.

### 3.2 Decoupled Predictive Pacing
`PredictivePacingService` does not have access to `TelecomProvider` or `CallAllocatorService`. It acts purely as a statistical consultant that analyzes historical answer rates, agent talk times, and ringing durations to compute an advisory recommendation.

### 3.3 Independent Safety Gatekeeper
`SafetyControllerService` is mandatory and cannot be bypassed. Even if the predictive engine requests 50 calls, the safety controller queries the PostgreSQL database in real time and clamps the allocation down to authoritative safe capacity.

### 3.4 Transactional Outbox Pattern
A database transaction must **never** be held open while making an external network HTTP request to a telecom provider. 
1. The database transaction reserves the agent and borrower, writes the `CallRecord` (status `RESERVED`), writes an `OutboxEvent`, and **commits immediately**.
2. An asynchronous dispatcher picks up the outbox event and executes the telecom HTTP call outside of any database transaction.
3. Upon telecom response, a new short-lived transaction updates the call state to `INITIATED` or `FAILED`.

### 3.5 Monotonic State Progression & Idempotent Webhooks
Every provider callback is validated against the `CallStateMachine`. Terminal states (`COMPLETED`, `FAILED`, `CANCELLED`) can never be regressed by delayed, duplicate, or out-of-order telecom webhooks.
