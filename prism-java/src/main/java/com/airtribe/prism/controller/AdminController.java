package com.airtribe.prism.controller;

import com.airtribe.prism.domain.CacheStatsDto;
import com.airtribe.prism.domain.RequestLogEntry;
import com.airtribe.prism.domain.RoutingEvalReport;
import com.airtribe.prism.domain.ProviderHealthReport;
import com.airtribe.prism.domain.UsageSummaryDto;
import com.airtribe.prism.domain.GatewayException;
import com.airtribe.prism.service.AdminAuthService;
import com.airtribe.prism.service.AdminService;
import com.airtribe.prism.service.RoutingEvalService;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;
    private final AdminAuthService adminAuthService;
    private final RoutingEvalService routingEvalService;

    public AdminController(AdminService adminService, AdminAuthService adminAuthService, RoutingEvalService routingEvalService) {
        this.adminService = adminService;
        this.adminAuthService = adminAuthService;
        this.routingEvalService = routingEvalService;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> adminHealth(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
        requireAdmin(token);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/cache/stats")
    public ResponseEntity<CacheStatsDto> cacheStats(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
        requireAdmin(token);
        return ResponseEntity.ok(adminService.cacheStats());
    }

    @GetMapping("/usage")
    public ResponseEntity<UsageSummaryDto> usage(@RequestHeader(name = "X-Admin-Token", required = false) String token,
                                                 @RequestParam String key,
                                                 @RequestParam(required = false) String month) {
        requireAdmin(token);
        YearMonth targetMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        return ResponseEntity.ok(adminService.usageSummary(key, targetMonth));
    }

    @GetMapping("/usage/all")
    public ResponseEntity<List<UsageSummaryDto>> allUsage(@RequestHeader(name = "X-Admin-Token", required = false) String token,
                                                           @RequestParam(required = false) String month) {
        requireAdmin(token);
        YearMonth targetMonth = month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month);
        return ResponseEntity.ok(adminService.usageSummaries(targetMonth));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<RequestLogEntry>> logs(@RequestHeader(name = "X-Admin-Token", required = false) String token,
                                                      @RequestParam(required = false) String key,
                                                      @RequestParam(defaultValue = "20") int limit) {
        requireAdmin(token);
        return ResponseEntity.ok(adminService.recentLogs(key, limit));
    }

    @GetMapping(value = "/ui", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> ui(@RequestParam(name = "token") String token) {
        requireAdmin(token);
        String html = """
                <html><head><title>Prism Admin</title>
                <style>body{font-family:system-ui;margin:2rem;max-width:1100px}table{border-collapse:collapse;width:100%;margin:1rem 0}th,td{border:1px solid #d1d5db;padding:.5rem;text-align:left}th{background:#f3f4f6}.cards{display:flex;gap:1rem}.card{background:#f8fafc;padding:1rem;border-radius:.5rem;min-width:10rem}</style>
                </head>
                <body>
                <h1>Prism operations</h1>
                <div class="cards"><div class="card">Cache hit rate<br><strong id="hit-rate">—</strong></div><div class="card">Cache hits<br><strong id="hits">—</strong></div><div class="card">Logged requests<br><strong id="requests">—</strong></div></div>
                <h2>Usage by key</h2><table><thead><tr><th>Key</th><th>Requests</th><th>Prompt tokens</th><th>Completion tokens</th><th>Cost (USD)</th><th>Cache hits</th></tr></thead><tbody id="usage"></tbody></table>
                <h2>Recent requests</h2><table><thead><tr><th>Time</th><th>Key</th><th>Model</th><th>Status</th><th>Provider</th><th>Cache</th><th>Cost</th></tr></thead><tbody id="logs"></tbody></table>
                <script>
                const token = new URLSearchParams(location.search).get('token');
                const options = {headers:{'X-Admin-Token':token}};
                const value = v => v == null ? '—' : v;
                Promise.all([fetch('/admin/cache/stats',options).then(r=>r.json()),fetch('/admin/usage/all',options).then(r=>r.json()),fetch('/admin/logs?limit=25',options).then(r=>r.json())]).then(([cache,usage,logs]) => {
                  document.querySelector('#hit-rate').textContent=(cache.hitRate*100).toFixed(1)+'%'; document.querySelector('#hits').textContent=cache.cacheHits; document.querySelector('#requests').textContent=cache.totalRequests;
                  document.querySelector('#usage').innerHTML=usage.map(x=>`<tr><td>${x.key}</td><td>${x.requests}</td><td>${x.promptTokens}</td><td>${x.completionTokens}</td><td>${x.costUsd.toFixed(6)}</td><td>${x.cacheHits}</td></tr>`).join('');
                  document.querySelector('#logs').innerHTML=logs.map(x=>`<tr><td>${new Date(x.createdAt).toLocaleString()}</td><td>${x.virtualKey}</td><td>${x.requestedModel}</td><td>${x.status}</td><td>${value(x.resolvedProvider)}</td><td>${x.cache}</td><td>${x.costUsd.toFixed(6)}</td></tr>`).join('');
                });
                </script>
                </body></html>
                """;
        return ResponseEntity.ok(html);
    }

    @GetMapping("/providers/health")
    public ResponseEntity<ProviderHealthReport> providerHealth(@RequestHeader(name = "X-Admin-Token", required = false) String token) {
        requireAdmin(token);
        return ResponseEntity.ok(adminService.providerHealth());
    }

    @GetMapping("/routing/eval")
    public ResponseEntity<RoutingEvalReport> routingEval(@RequestHeader(name = "X-Admin-Token", required = false) String token) throws Exception {
        requireAdmin(token);
        return ResponseEntity.ok(routingEvalService.evaluate());
    }

    private void requireAdmin(String token) {
        if (!adminAuthService.isAuthorized(token)) {
            throw new GatewayException(org.springframework.http.HttpStatus.UNAUTHORIZED, "authentication_error", "authentication_error", "Invalid admin token");
        }
    }
}
