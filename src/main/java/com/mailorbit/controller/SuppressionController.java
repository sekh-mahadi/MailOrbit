package com.mailorbit.controller;

import com.mailorbit.entity.SuppressionEntry;
import com.mailorbit.repository.SuppressionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/suppressions")
@RequiredArgsConstructor
public class SuppressionController {

    private final SuppressionRepository suppressionRepository;

    @GetMapping
    public List<SuppressionEntry> list() {
        return suppressionRepository.findAllByOrderByIdDesc();
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, String> body) {
        String pattern = body.get("pattern");
        if (pattern == null || pattern.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "pattern is required (email or domain)"));
        }
        pattern = pattern.trim().toLowerCase(Locale.ROOT);
        if (pattern.startsWith("@")) pattern = pattern.substring(1);
        if (suppressionRepository.findByPattern(pattern).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Already on the suppression list"));
        }
        SuppressionEntry entry = new SuppressionEntry();
        entry.setPattern(pattern);
        entry.setReason(body.getOrDefault("reason", null));
        return ResponseEntity.ok(suppressionRepository.save(entry));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable long id) {
        if (!suppressionRepository.existsById(id)) return ResponseEntity.notFound().build();
        suppressionRepository.deleteById(id);
        return ResponseEntity.ok(Map.of("deleted", id));
    }
}
