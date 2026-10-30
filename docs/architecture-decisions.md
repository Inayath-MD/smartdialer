# Architecture Decision Records (ADR)

## ADR 1: Technology Stack Selection (Java 21 & Spring Boot 3.x)
- **Status:** Accepted
- **Context:** Collections outbound calling requires high concurrency, strict type safety, rock-solid threading models, and mature enterprise tooling.
- **Decision:** Use Java 21 LTS with Spring Boot 3.3.x.
- **Consequences:** Provides modern language features (Virtual Threads, Records, Pattern Matching), robust Spring Data JPA transactions, native Actuator observability, and standard OpenAPI documentation.

---

## ADR 2: Architecture Paradigm (Modular Monolith vs Microservices)
- **Status:** Accepted
- **Context:** An outbound dialer must pair agents with borrowers within single-digit milliseconds under high concurrent pressure. Microservices with HTTP/gRPC boundaries and distributed transactions (Sagas) would introduce race conditions, network hops, and partial failure modes during pairing.
- **Decision:** Adopt a Modular Monolithic architecture with strict domain boundaries (`service.agent`, `service.borrower`, `service.dialer`, `service.safety`, `service.allocation`).
- **Consequences:** Retains in-memory function calls, sub-millisecond execution, simple deployment, and local transactional boundaries without introducing Kafka/Redis operational overhead prematurely.

---

## ADR 3: Concurrency Control Strategy (PostgreSQL Conditional Updates vs In-Memory Locks)
- **Status:** Accepted
- **Context:** Multiple dialing worker nodes or concurrent threads may attempt to reserve the same available agent or borrower simultaneously. In-memory locks (such as Java `synchronized` or `ReentrantLock`) fail across multi-node or multi-instance deployments.
- **Decision:** Enforce concurrency control directly in PostgreSQL using atomic conditional updates (`UPDATE agents SET status = 'RESERVED' WHERE id = :id AND status = 'AVAILABLE'`) and pessimistic row locking (`SELECT FOR UPDATE`).
- **Consequences:** Exactly one transaction succeeds in acquiring the reservation. All other concurrent transactions fail fast without deadlock or duplicate assignment.

---

## ADR 4: Transactional Outbox Pattern for Telecom Dispatch
- **Status:** Accepted
- **Context:** Telecom providers frequently suffer from variable network latency (50ms - 2,000ms) and occasional network timeouts. Holding open a PostgreSQL connection and transaction across a network call exhausts database connection pools (HikariCP) and causes thread starvation.
- **Decision:** Implement the Transactional Outbox pattern. The pairing transaction commits immediately upon writing the call reservation and outbox event. Dispatch happens asynchronously.
- **Consequences:** Database transactions complete in <5ms. Telecom network delays or provider timeouts cannot degrade database throughput.

---

## ADR 5: Mandatory Independent Safety Controller
- **Status:** Accepted
- **Context:** Predictive pacing relies on statistical approximations of answer rates. If a sudden surge occurs or answer rates diverge, predictive models risk causing call abandonment (borrower answers with no agent available), leading to severe regulatory penalties.
- **Decision:** Mandate an independent `SafetyControllerService` that must evaluate and authorize every dialing batch against live authoritative database state.
- **Consequences:** Predictive pacing is strictly decoupled and advisory. The Safety Controller has sole authority to approve, reduce, reject, or fallback to 1:1 progressive dialing.
