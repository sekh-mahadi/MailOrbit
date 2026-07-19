package com.mailorbit.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Known disposable / throwaway email domains, bundled as a classpath
 * resource (disposable-domains.txt, one domain per line).
 */
@Slf4j
@Service
public class DisposableDomains {

    private Set<String> domains = Set.of();

    @PostConstruct
    public void load() {
        Set<String> loaded = new HashSet<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("disposable-domains.txt").getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.ROOT);
                if (!line.isEmpty() && !line.startsWith("#")) loaded.add(line);
            }
        } catch (Exception e) {
            log.error("Could not load disposable-domains.txt: {}", e.toString());
        }
        domains = Set.copyOf(loaded);
        log.info("Loaded {} disposable domains", domains.size());
    }

    public boolean isDisposable(String domain) {
        if (domain == null || domain.isEmpty()) return false;
        String d = domain.toLowerCase(Locale.ROOT);
        if (domains.contains(d)) return true;
        // subdomains of a disposable provider are disposable too
        int dot = d.indexOf('.');
        while (dot > 0) {
            d = d.substring(dot + 1);
            if (domains.contains(d)) return true;
            dot = d.indexOf('.');
        }
        return false;
    }

    public int size() {
        return domains.size();
    }
}
