package com.aiplatform.agent.controller;

import com.aiplatform.agent.runbook.Runbook;
import com.aiplatform.agent.store.PastIncidentStore;
import com.aiplatform.agent.store.RunbookStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/runbooks")
@RequiredArgsConstructor
public class RunbookController {

    private final RunbookStore runbookStore;
    private final PastIncidentStore pastIncidentStore;

    @PostMapping
    public ResponseEntity<Runbook> createRunbook(@RequestBody Runbook runbook) {
        Runbook saved = runbookStore.save(runbook);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<Runbook>> getRunbooks(
            @RequestParam(required = false) String serviceId) {
        if (serviceId != null) return ResponseEntity.ok(runbookStore.findByService(serviceId));
        return ResponseEntity.ok(runbookStore.findByService("*"));
    }

    @GetMapping("/{runbookId}")
    public ResponseEntity<Runbook> getRunbook(@PathVariable String runbookId) {
        return runbookStore.findById(runbookId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{runbookId}")
    public ResponseEntity<Map<String, String>> deleteRunbook(@PathVariable String runbookId) {
        runbookStore.delete(runbookId);
        return ResponseEntity.ok(Map.of("status", "deleted", "runbookId", runbookId));
    }

    @GetMapping("/history/{serviceId}")
    public ResponseEntity<List<String>> getPastIncidents(
            @PathVariable String serviceId,
            @RequestParam(defaultValue = "5") int limit) {
        return ResponseEntity.ok(pastIncidentStore.findRecent(serviceId, limit));
    }

    /**
     * Seeds realistic runbooks for demo.
     * POST /api/v1/runbooks/seed
     * Call this once after starting the platform.
     */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedDefaultRunbooks() {
        List<Runbook> runbooks = List.of(
                Runbook.builder()
                        .serviceId("payment-service")
                        .title("Payment service CPU spike response")
                        .description("CPU spikes are usually caused by batch settlement jobs or DB connection pool issues")
                        .steps(List.of(
                                "Check if nightly batch settlement job is running (2am-4am IST) — if yes, wait 10 mins",
                                "Check DB connection pool in Grafana — if > 80%, that is the root cause",
                                "If no batch job, scale up to 4 replicas immediately",
                                "Monitor for 5 mins — if CPU does not drop, restart the pod with highest CPU"
                        ))
                        .notes("Month-end reconciliation jobs run on the 1st — CPU spikes are expected and self-resolving")
                        .createdBy("platform-team")
                        .build(),

                Runbook.builder()
                        .serviceId("payment-service")
                        .title("Payment service high error rate response")
                        .description("Error rates above 10% usually indicate downstream payment gateway failures")
                        .steps(List.of(
                                "Check if Razorpay/Stripe is having an outage at their status page first",
                                "Check DB connectivity via health endpoint",
                                "If gateway is down, enable circuit breaker fallback mode",
                                "Alert payments team lead immediately for error rate > 15%"
                        ))
                        .notes("Never auto-restart during high error rates without checking the gateway first")
                        .createdBy("payments-team")
                        .build(),

                Runbook.builder()
                        .serviceId("order-service")
                        .title("Order service memory leak response")
                        .description("Known memory leak in PDF invoice generation library")
                        .steps(List.of(
                                "Check if memory grew steadily for 30+ mins (leak) vs sudden spike (load)",
                                "If steady growth: restart the pod — known leak in invoice PDF generation",
                                "If sudden spike: check order volume — scale up if traffic related",
                                "After restart, monitor GC pause times — should drop below 200ms"
                        ))
                        .notes("Fix expected in library v3.1. Until then daily restarts at 3am are scheduled as workaround")
                        .createdBy("orders-team")
                        .build(),

                Runbook.builder()
                        .serviceId("*")
                        .title("General high latency response — applies to all services")
                        .description("Generic steps for P99 latency above 1 second on any service")
                        .steps(List.of(
                                "Check downstream dependencies (DB, cache, other services) for slowness first",
                                "Check thread pool utilization — if > 80%, scale up",
                                "Check for deployments in the last 2 hours — if yes, consider rollback",
                                "Enable request sampling to identify the slowest endpoints"
                        ))
                        .notes("Latency issues are almost always downstream — check dependencies before restarting")
                        .createdBy("platform-team")
                        .build()
        );

        runbooks.forEach(runbookStore::save);

        return ResponseEntity.ok(Map.of(
                "status", "seeded",
                "count", runbooks.size(),
                "message", "Runbooks loaded. Mistral will now use your team's knowledge for diagnosis."
        ));
    }
}