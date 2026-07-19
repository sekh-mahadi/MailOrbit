package com.mailorbit.controller;

import com.mailorbit.service.RuleEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rules")
@RequiredArgsConstructor
public class RuleController {

    private final RuleEngine ruleEngine;

    @GetMapping
    public List<Map<String, String>> list() {
        return ruleEngine.getRules().stream()
                .map(r -> Map.of("name", r.name(), "description", r.description(), "file", r.file()))
                .toList();
    }

    @PostMapping("/reload")
    public Map<String, Object> reload() {
        return Map.of("loaded", ruleEngine.reload());
    }
}
