package com.mailorbit.service;

import groovy.lang.Binding;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RuleEngine - Groovy verification-rule engine (DataOrbit OrbitScriptService style).
 *
 * Each .groovy file in the rules/ directory is evaluated with GroovyShell and
 * must return a Map:
 *
 *   [
 *     name       : "typo-domains",
 *     description: "Catches misspelled freemail domains",
 *     apply      : { contact -> [scoreDelta: -100, fatal: true, reason: "..."] }
 *   ]
 *
 * The apply closure receives the EmailContact entity (contact.email,
 * contact.localPart, contact.domain, ...) and returns null (no opinion) or a
 * Map with any of: scoreDelta (int), reason (String), fatal (boolean).
 */
@Slf4j
@Service
public class RuleEngine {

    public record Rule(String name, String description, String file, Closure<?> apply) {
    }

    @Value("${mailorbit.rules.dir:rules}")
    private String rulesDir;

    private volatile List<Rule> rules = List.of();

    @PostConstruct
    public void init() {
        reload();
    }

    public synchronized int reload() {
        List<Rule> loaded = new ArrayList<>();
        Path dir = Path.of(rulesDir);
        if (Files.isDirectory(dir)) {
            GroovyShell shell = new GroovyShell(new Binding());
            try (Stream<Path> files = Files.list(dir)) {
                for (Path p : files.filter(f -> f.getFileName().toString().endsWith(".groovy")).sorted().toList()) {
                    try {
                        Object result = shell.evaluate(p.toFile());
                        if (result instanceof Map<?, ?> m && m.get("apply") instanceof Closure<?> apply) {
                            Object name = m.get("name");
                            Object description = m.get("description");
                            loaded.add(new Rule(
                                    name != null ? String.valueOf(name) : p.getFileName().toString(),
                                    description != null ? String.valueOf(description) : "",
                                    p.getFileName().toString(),
                                    apply));
                        } else {
                            log.warn("Rule {} did not return a [name:, apply: {{...}}] map - skipped", p.getFileName());
                        }
                    } catch (Exception e) {
                        log.error("Failed to load rule {}: {}", p.getFileName(), e.toString());
                    }
                }
            } catch (Exception e) {
                log.error("Could not scan rules dir {}: {}", rulesDir, e.toString());
            }
        } else {
            log.warn("Rules directory '{}' not found - no Groovy rules loaded", rulesDir);
        }
        rules = List.copyOf(loaded);
        log.info("Loaded {} Groovy rules from {}", rules.size(), rulesDir);
        return rules.size();
    }

    public List<Rule> getRules() {
        return rules;
    }
}
