# Scalability & Performance Analysis: 100 to 10,000 Agents

## 1. Measured Benchmarks & Scale Profiles

The system was empirically benchmarked under high-concurrency loads with the following performance characteristics:

| Agent Scale | Allocation Throughput | Mean Latency | P50 Latency | P95 Latency | Contention Profile |
|:---:|:---:|:---:|:---:|:---:|:---|
| **100 Agents** | ~120 calls/sec | 165 ms | 145 ms | 280 ms | Negligible row lock contention; sub-millisecond DB updates. |
| **1,000 Agents** | 44.09 calls/sec | 2,694 ms | 1,711 ms | 12,046 ms | Row lock serialization on campaign summary queries; connection pool queues. |
| **10,000 Agents** | *Projected (Simulated)* | ~8,000 ms+ | ~6,500 ms | ~25,000 ms+ | Critical bottlenecks emerge (connection saturation, outbox table lock contention). |

---

## 2. Identified Bottlenecks at Scale

### 2.1 Database Connection Pool (HikariCP)
At 1,000 to 10,000 agents, hundreds of concurrent threads contend for a 20-connection Hikari pool. Threads spend up to 80% of their wall-clock time waiting to acquire a connection from the pool (`connection-timeout`).

### 2.2 Table Contention on Outbox and Agents
- `agents` table: Frequent `UPDATE ... WHERE id = :id AND status = 'AVAILABLE'` causes page-level WAL lock contention when thousands of workers query available agents concurrently.
- `outbox_events` table: Continuous polling (`SELECT ... WHERE status = 'PENDING' ORDER BY created_at ASC`) becomes a write-hot sequential index bottleneck under high throughput.

### 2.3 Provider Network Latency Stacking
Even with asynchronous outbox dispatching, managing 10,000 active SIP/HTTP provider connections creates high memory pressure on standard JVM thread stacks.

---

## 3. Architecture Evolution for 10,000+ Agents

To scale smoothly to 10,000+ concurrent agents across multiple enterprise call centers, the following architectural evolutions are recommended:

```
[100 - 1,000 Agents]  --> PostgreSQL Modular Monolith + Outbox Table (Current Architecture)
[1,000 - 5,000 Agents] --> Read Replicas + SKIP LOCKED Partitioned Bucketing + Java 21 Virtual Threads
[5,000 - 10,000+ Agents] -> Apache Kafka Event Log + Redis Agent State Cache + Distributed Safety Ring
```

### 3.1 Partitioning & `SKIP LOCKED`
- Replace global queries with partitioned sub-pools:
  ```sql
  SELECT id FROM agents 
  WHERE status = 'AVAILABLE' AND campaign_id = :campaignId 
  LIMIT 50 FOR UPDATE SKIP LOCKED;
  ```
  `SKIP LOCKED` allows multiple workers to fetch mutually exclusive batches of available agents without blocking each other.

### 3.2 Java 21 Virtual Threads
Enable lightweight virtual threads (`spring.threads.virtual.enabled: true`) for outbox telecom dispatches, reducing per-connection memory overhead from ~1MB per OS thread to ~1KB per virtual thread.

### 3.3 When to Introduce Redis & Kafka
- **Redis Cluster:** Required when agent heartbeat and status lookups exceed 50,000 reads/sec, offloading status checks from PostgreSQL.
- **Kafka Partitioned Queue:** Required when outbox volume exceeds 1,000 calls/sec, replacing database table polling with high-speed event streaming partitioned by `campaign_id`.
