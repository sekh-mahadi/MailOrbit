package com.mailorbit.controller;

import com.mailorbit.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/verify")
@RequiredArgsConstructor
public class VerifyController {

    private final VerificationService verificationService;

    @PostMapping
    public VerificationService.VerifyJob start(
            @RequestParam(required = false) Long batchId,
            @RequestParam(defaultValue = "new") String scope) {
        return verificationService.start(batchId, scope);
    }

    @GetMapping("/{id}")
    public ResponseEntity<VerificationService.VerifyJob> get(@PathVariable long id) {
        VerificationService.VerifyJob job = verificationService.getJob(id);
        return job == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(job);
    }

    @GetMapping
    public List<VerificationService.VerifyJob> list() {
        return verificationService.listJobs();
    }
}
