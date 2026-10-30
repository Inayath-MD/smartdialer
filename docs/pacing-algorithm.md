# Predictive Pacing Algorithm Specification

## 1. Mathematical Formulation

The Predictive Pacing Engine calculates the optimal number of outbound calls to propose while strictly bounding call abandonment risk.

### 1.1 Core Pacing Model

$$\text{Expected Answers} = \text{Proposed Calls} \times \hat{R}_{\text{ans}}$$

Where:
- $\hat{R}_{\text{ans}}$: Exponentially Weighted Moving Average (EWMA) of the historical answer rate.
- $\bar{T}_{\text{talk}}$: Average call talk duration in seconds (EWMA).
- $T_{\text{ring}}$: Average dial and ring duration ($\approx 25$ seconds).
- $A_a$: Available (idle) agents.
- $A_c$: Connected agents currently on calls.
- $C_r$: Number of currently ringing calls in-flight.

---

### 1.2 Agent Capacity Turnover Estimation

During the $T_{\text{ring}}$ time it takes for a new call to ring and answer, a portion of currently connected agents are expected to finish their calls:

$$\text{Expected Freed Agents} = A_c \times \left( \frac{T_{\text{ring}}}{\bar{T}_{\text{talk}}} \right) \times 0.75$$

*(A 25% conservative haircut is applied to agent turnover to guard against long-tail call durations).*

$$\text{Total Expected Agent Capacity} = A_a + \text{Expected Freed Agents}$$

---

### 1.3 In-Flight Ringing Absorption

Calls that are already ringing are also expected to answer soon:

$$\text{Pending Expected Answers} = C_r \times \hat{R}_{\text{ans}}$$

$$\text{Net Available Agent Capacity} = \max(0, \; \text{Total Expected Agent Capacity} - \text{Pending Expected Answers})$$

---

### 1.4 Raw Calls to Propose

$$\text{Raw Proposed Calls} = \frac{\text{Net Available Agent Capacity}}{\hat{R}_{\text{ans}}}$$

---

### 1.5 Safety, Latency, and Confidence Factors

The final recommendation applies four multipliers:

$$\text{Recommended Calls} = \lfloor \text{Raw Proposed} \times S_{\text{margin}} \times F_{\text{latency}} \times F_{\text{abandon}} \times C_{\text{sample}} \rfloor$$

1. **Safety Margin ($S_{\text{margin}} = 0.85$):** 15% conservative buffer.
2. **Provider Latency Factor ($F_{\text{latency}}$):**
   - If provider latency $\le 500$ ms: $1.0$
   - If provider latency $500\text{ms} - 1000\text{ms}$: $0.75$
   - If provider latency $> 1000$ ms: $0.50$
3. **Abandonment Target Factor ($F_{\text{abandon}}$):**
   - If campaign abandonment target $\le 3\%$: $0.90$
   - If target $> 3\%$: $1.0$
4. **Statistical Confidence ($C_{\text{sample}}$):**
   - $< 10$ historical calls: $0.65$ (prior uncertainty)
   - $10 - 49$ calls: $0.80$
   - $50 - 199$ calls: $0.90$
   - $\ge 200$ calls: $0.98$

---

## 2. Hard Bounds and Cutoffs

1. **Provider Outage Cutoff:**
   If provider failure rate exceeds **25%**, the algorithm immediately returns **0 calls** and recommends a shutdown.
2. **Campaign Headroom Limit:**
   $$\text{Recommended Calls} \le \text{MaxConcurrentCalls} - (C_r + A_c)$$
   Calls are clamped so that total in-flight traffic never exceeds campaign concurrency ceilings.

---

## 3. Transparent Explainability

Every pacing recommendation produces a human-readable `reasoning` string, e.g.:
```
Paced 8 calls (Raw: 12.5, NetCap: 5.0, AnsRate: 40.0%, Conf: 0.90, LatencyFactor: 1.00, CampaignHeadroom: 15)
```
This guarantees operational audibility for compliance officers and call center supervisors.
