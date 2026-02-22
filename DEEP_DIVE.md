# AI-Powered Incident Response Platform — Deep Dive

> I wrote this to explain what I built, why I built it this way, and what I would do differently in a real production system. Useful for code reviews, onboarding someone new to this codebase, or anyone who wants to understand the thinking behind the design.

---

## 1. The Problem

Every team running microservices deals with this at some point:

```
Alert fires at 3am
    → Engineer wakes up
    → Opens Grafana, checks dashboards
    → Digs through logs
    → Reads the runbook or pings a colleague
    → Figures out root cause (10–30 minutes)
    → Restarts something, scales something, rolls back
    → Goes back to sleep
    → Same thing happens next week
```

The same failures repeat. CPU spike on payment-service every Friday night. GC pressure on notification-service after 72 hours uptime. Every single time, a human has to diagnose it from scratch — even when the team already knows the pattern and knows the fix.

Grafana, PagerDuty, Datadog — they are all good at telling you something is wrong. None of them tell you why, and none of them fix it. The gap between alert firing and remediation action is where engineer time gets burned at 3am.

That is the gap this platform closes.

---

## 2. What It Does

Detects anomalies in real-time, diagnoses root cause using a local LLM loaded with your team's runbooks and incident history, and takes a remediation action — automatically, without waking anyone up for failure patterns the system already knows.

```
Services emit metrics
        |
Anomaly detected (threshold breach + severity rules)
        |
Incident created (deduplicated via Redis)
        |
Mistral LLM diagnoses root cause
  context: metric data + team runbook + past incident history
        |
Action: SCALE_UP / RESTART_POD / ROLLBACK / ALERT_TEAM / INVESTIGATE
        |
Incident resolved, stored, learned from
```

For repeating failure patterns, no human needs to be involved.

---

## 3. Why Not Just Use Datadog AI?

Fair question. Datadog and PagerDuty are excellent tools and this platform is not trying to replace them — it is designed to sit alongside them.

The problem with off-the-shelf AI monitoring is that it has never read your runbooks. It does not know your batch settlement job runs at 2am and always spikes payment-service CPU. It does not know you should never rollback payment-service because the DB migration is irreversible. It does not learn from your past incidents — every alert is evaluated in isolation with no memory of what worked before.

This platform is built around one idea: **the AI should reason with your team's knowledge, not generic SRE patterns.**

You teach it once through runbooks. It uses that knowledge on every incident, forever. And as incidents get resolved and recorded, the context it has for your services gets richer over time.

In a real company this connects to wherever your runbooks already live — Confluence, Notion, a shared drive. The LLM context improves automatically as your documentation improves.

---

## 4. Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                          PIPELINE                            │
│                                                              │
│  MetricSimulator          incident-consumer                  │
│  (or Prometheus)  ──────▶ Anomaly detection   ──▶ incidents │
│                           Redis deduplication     (Kafka)   │
│                                                      |       │
│                                            ai-agent  |       │
│                                            Mistral LLM       │
│                                            Runbook context   │
│                                            Past incidents    │
│                                                      |       │
│                                       action-executor|       │
│                                            SCALE_UP          │
│                                            RESTART_POD       │
│                                            ROLLBACK          │
│                                            ALERT_TEAM        │
│                                            INVESTIGATE       │
└──────────────────────────────────────────────────────────────┘

Infrastructure: Kafka (3 topics), Redis, Ollama/Mistral
```

### Kafka Topics

| Topic | Producer | Consumer | What flows through |
|-------|----------|----------|--------------------|
| `metric-events` | incident-producer | incident-consumer | Raw metrics from services |
| `incidents` | incident-consumer | ai-agent | Detected anomalies |
| `actions` | ai-agent | action-executor | AI-enriched incidents ready for action |

### Why Kafka and not direct REST calls between services?

A few reasons I care about here:

- **Decoupling** — LLM calls take 30–60 seconds. If the AI agent is slow, it should not block metric ingestion. Kafka handles the backpressure naturally.
- **Durability** — if action-executor is down, incidents queue up and get processed when it recovers. No incidents are lost.
- **Scalability** — you can run multiple AI agent instances consuming from the same topic. Each picks up a different incident partition.
- **Replay** — if you deploy an improved LLM or better prompts, you can reprocess historical incidents against the new model.

---

## 5. Key Engineering Decisions

### 5.1 Kafka Partitioning by serviceId

All events from the same service go to the same Kafka partition. This means events from payment-service are always processed in order, while events from different services are processed in parallel across partitions.

If we partitioned randomly, events from the same service could arrive out of order. Anomaly detection that looks at trends over time would break.

### 5.2 Redis Deduplication with a 2-Minute Window

Without deduplication, a single CPU spike generates 50+ metric events in 2 minutes — 50 incidents, 50 LLM calls, 50 Slack notifications. That is an alert storm and it is worse than the original problem.

The dedup key is `serviceId + metricType`. One incident per service per metric type per 2 minutes. The window is configurable.

The trade-off: if the same service has two genuinely separate incidents for the same metric within 2 minutes, the second gets dropped. That is an acceptable trade-off for most cases. In production you would tune the window per service criticality — shorter for payment-service, longer for a background job.

### 5.3 Manual Offset Acknowledgment in Kafka

Kafka offsets are only committed after the event is fully processed. If the AI agent crashes mid-diagnosis, the incident gets reprocessed when it restarts. Nothing is silently lost.

The trade-off is at-least-once delivery — the same incident could be processed twice if a failure happens between processing and committing. The incident ID acts as an idempotency key for storage. But the action executor could theoretically restart a pod twice. In production I would add an `actioned:{incidentId}` key in Redis before executing any action — check it first, set it after.

### 5.4 LangChain4j as the LLM Abstraction Layer

The AI agent calls `ChatLanguageModel.generate()` — an interface. Right now the implementation is `OllamaChatModel` pointing at Mistral running locally. Switching to OpenAI GPT-4 is one config change in `LlmConfig.java`:

```java
// swap this:
return OllamaChatModel.builder().baseUrl(...).modelName("mistral").build();
// for this:
return OpenAiChatModel.builder().apiKey(key).modelName("gpt-4").build();
```

The prompt, the parsing, the runbook injection — none of it changes. This was a deliberate decision. I did not want the rest of the platform coupled to any specific LLM provider.

### 5.5 Runbook and Past Incident Injection into the Prompt

This is the most important architectural decision in the whole platform.

Without context, Mistral gives generic SRE advice that any engineer already knows. With the team's runbooks and past incidents injected into the prompt, Mistral knows your service's specific failure patterns, what actions are safe versus dangerous, and what worked last time.

This is essentially a simple form of RAG — Retrieval-Augmented Generation. Before the LLM call, I retrieve relevant runbooks and recent incident history from Redis and inject them into the prompt. The LLM reasons over your data, not just its training data.

### 5.6 Dual Deployment Mode

Same codebase, two ways to run it:

- **Monolith** (`platform-monolith`) — one JAR, one port (8080). `java -jar platform-monolith.jar` and everything is running. Good for teams getting started or for demos.
- **Microservices** — four separate JARs on four ports. Run them independently, scale them independently.

No code changes to switch between modes. The `@Primary` beans in `MonolithConfig` resolve any conflicts when all modules load in the same Spring context.

---

## 6. What the Simulator Is (and Is Not)

The `incident-producer` module simulates 5 microservices emitting metrics. In a real deployment, you replace it by pointing Alertmanager at the webhook:

```yaml
# alertmanager.yml
receivers:
  - name: 'ai-platform'
    webhook_configs:
      - url: 'http://incident-consumer:8082/api/v1/webhook/alertmanager'
```

The `AlertmanagerWebhookController` already accepts real Prometheus Alertmanager payloads and converts them into the platform's internal `MetricEvent` format. The simulator exists only for local development and demos.

To connect to a real monitoring stack, point Alertmanager at the webhook endpoint. No code changes needed — just an Alertmanager config change.

---

## 7. Honest Limitations

### The AI Can Be Wrong

Mistral is a general-purpose LLM, not a trained SRE model. It can misdiagnose. I mitigate this with low temperature (0.3) for deterministic outputs, a structured prompt format that constrains the response, fallback to `INVESTIGATE` when the response does not match the expected format, and full logging of every AI response so humans can review.

In production I would add confidence scoring — only auto-remediate above a threshold, escalate to human below it.

### No Action Idempotency Yet

If the same incident is processed twice due to at-least-once delivery, the action executor will execute the action twice. A pod could get restarted twice. This needs a Redis-based idempotency check before any action is taken. It is straightforward to add, I just have not done it yet.

### Runbooks Are Manual

Runbooks are seeded via a REST API call. In production you would build a sync job that pulls from Confluence or Notion on a schedule. The platform's `RunbookStore` interface is designed to make this a drop-in addition.

### Kubernetes Actions Are Simulated

The `RESTART_POD`, `SCALE_UP`, and `ROLLBACK` handlers log what they would do and sleep to simulate latency. Replacing with real calls means adding the fabric8 Kubernetes Java client and configuring RBAC. The handler structure is deliberately a drop-in replacement — the rest of the platform does not change.

### Single-Node Kafka

`docker-compose` uses one Kafka broker, replication factor 1. Fine for development. Production minimum is 3 brokers, replication factor 3, rack-aware partitioning.

### LLM Latency

Mistral on a laptop takes 30–90 seconds per diagnosis. Detection to action can be 1–2 minutes end-to-end. For most incidents that is acceptable. For production at scale you would use GPU-accelerated inference or a faster hosted model.

---

## 8. What I Would Do Differently in Production

1. **Real Kubernetes integration** using fabric8 client — actual pod restarts and deployment scaling
2. **Confidence scoring on AI output** — auto-remediate above threshold, escalate below
3. **Action idempotency** — `actioned:{incidentId}` Redis key before every action execution
4. **Multi-broker Kafka** — 3 brokers, replication factor 3, rack-aware partitioning
5. **Runbook auto-sync** — scheduled job pulling from Confluence or Notion
6. **Feedback loop** — after action is taken, check if metrics recovered within 5 minutes. If yes, record as a successful pattern. If no, escalate.
7. **Rate limiting on actions** — do not restart the same pod more than once per 10 minutes regardless of what the AI says
8. **Dead letter topic** — failed events go to a DLT for manual review instead of being acknowledged and dropped
9. **OpenTelemetry instrumentation** — distributed tracing across all four services
10. **Authentication on APIs** — runbook endpoints and webhook endpoint need at minimum API key auth

---

## 9. Tests Written

Unit tests cover the core business logic across all modules:

| Test | What it verifies |
|------|-----------------|
| `AnomalyDetectorTest` | Detection thresholds, severity rules, metric-specific WARNING logic |
| `IncidentServiceTest` | Redis deduplication — same service+metric within 2 mins creates only one incident |
| `IncidentDiagnosisServiceTest` | LLM response parsing — handles preamble, bold markdown, case variations, unknown actions |
| `IncidentPromptBuilderTest` | Runbook injection — verifies team context actually reaches the LLM prompt |
| `ActionDispatcherServiceTest` | Action routing — correct handler per action, CRITICAL always notifies Slack |

```bash
mvn test                        # all tests
mvn test -pl incident-consumer  # single module
mvn verify                      # unit + integration
```

---

## 10. Pre-Commit Checklist

Before pushing any changes:

- [ ] `docker-compose up -d` — all containers start clean
- [ ] `mvn clean install -DskipTests` — builds without errors
- [ ] All 4 services start without errors in logs
- [ ] `POST /api/v1/runbooks/seed` returns 200
- [ ] Trigger CPU_SPIKE on payment-service — incident appears in Kafka UI
- [ ] AI agent logs show non-empty ROOT_CAUSE
- [ ] Action executor logs show action taken and incident status RESOLVED
- [ ] Slack notification received in #incidents channel
- [ ] `GET /api/v1/runbooks?serviceId=payment-service` returns seeded runbooks
- [ ] `GET /api/v1/runbooks/history/payment-service` returns past incidents after a few cycles

---

## 11. Module Summary

| Module | Port | Responsibility |
|--------|------|----------------|
| `common` | — | Shared models and enums — MetricEvent, Incident, Severity, IncidentStatus |
| `incident-producer` | 8081 | Metric simulation for local dev, REST API for manual scenario triggers |
| `incident-consumer` | 8082 | Anomaly detection, Kafka consumer, Redis dedup, Alertmanager webhook |
| `ai-agent` | 8083 | Mistral LLM integration, runbook store, past incident store, prompt builder |
| `action-executor` | 8084 | Action handlers, Slack integration, incident query API |
| `platform-monolith` | 8080 | Single-JAR mode — runs all modules in one Spring context |

---

*Mallikarjuna Mayakara — Technical Architect, 11+ years in distributed systems.*
