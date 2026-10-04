package com.airtribe.prism.service;

import com.airtribe.prism.domain.AutoRoutingDecision;
import com.airtribe.prism.util.PromptUtils;
import org.springframework.stereotype.Service;

@Service
public class AutoRouterService {

    public AutoRoutingDecision classify(String prompt) {
        String normalized = PromptUtils.normalize(prompt);
        int score = 0;
        StringBuilder reason = new StringBuilder();

        if (containsAny(normalized, "prove", "proof", "irrational", "theorem", "contradiction", "estimate", "show your reasoning")) {
            score += 3;
            reason.append("reasoning/logic; ");
        }
        if (containsAny(normalized, "design", "schema", "distributed systems", "network partition", "migration", "trade off", "trade-offs", "latency", "p99", "event sourcing", "crud", "accounting ledger", "leaderless", "quorum", "consistency", "zero downtime", "monolithic")) {
            score += 4;
            reason.append("systems/design; ");
        }
        if (containsAny(normalized, "debug", "bug", "root cause", "p95", "logging", "cache increase", "anomaly", "algorithm", "palindromic")) {
            score += 2;
            reason.append("debugging; ");
        }
        if (containsAny(normalized, "extract", "lookup", "translate", "rewrite", "what is the capital", "who is on call", "timestamp of the first error", "basic arithmetic")) {
            score -= 3;
            reason.append("lookup/extraction; ");
        }

        if (prompt != null && prompt.length() > 260) {
            score -= 1;
            reason.append("long-input penalty; ");
        }

        String tier = score >= 2 ? "smart" : "fast";
        if (reason.length() == 0) {
            reason.append("default fast; ");
        }
        return new AutoRoutingDecision(tier, (tier + ": " + reason).trim());
    }

    private boolean containsAny(String text, String... terms) {
        for (String term : terms) {
            if (text.contains(term)) {
                return true;
            }
        }
        return false;
    }
}
