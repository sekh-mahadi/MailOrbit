package com.mailorbit.controller;

import com.mailorbit.entity.ContactStatus;
import com.mailorbit.repository.EmailContactRepository;
import com.mailorbit.repository.ImportBatchRepository;
import com.mailorbit.repository.SuppressionRepository;
import com.mailorbit.service.RuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StatsController {

    private final EmailContactRepository contactRepository;
    private final ImportBatchRepository batchRepository;
    private final SuppressionRepository suppressionRepository;
    private final RuleEngine ruleEngine;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "app", "MailOrbit", "version", "1.0.0");
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ContactStatus status : ContactStatus.values()) {
            byStatus.put(status.name(), contactRepository.countByStatus(status));
        }

        List<Map<String, Object>> topDomains = new ArrayList<>();
        for (Object[] row : contactRepository.topDomains(PageRequest.of(0, 5))) {
            topDomains.add(Map.of("domain", row[0], "count", row[1]));
        }

        Double avgScore = contactRepository.averageScore();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", contactRepository.count());
        stats.put("byStatus", byStatus);
        stats.put("batches", batchRepository.count());
        stats.put("suppressions", suppressionRepository.count());
        stats.put("rules", ruleEngine.getRules().size());
        stats.put("avgScore", avgScore == null ? null : Math.round(avgScore));
        stats.put("topDomains", topDomains);
        return stats;
    }
}
