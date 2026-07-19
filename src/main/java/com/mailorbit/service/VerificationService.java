package com.mailorbit.service;

import com.mailorbit.entity.ContactStatus;
import com.mailorbit.entity.EmailContact;
import com.mailorbit.entity.SuppressionEntry;
import com.mailorbit.repository.EmailContactRepository;
import com.mailorbit.repository.SuppressionRepository;
import com.mailorbit.util.EmailNormalizer;
import groovy.lang.Closure;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Verification cascade, cheapest checks first:
 *   suppression list -> syntax -> disposable domain -> role account /
 *   free provider flags -> DNS MX -> Groovy rules -> score + status.
 *
 * Runs as an async job on a worker pool; progress is tracked in an
 * in-memory registry (DataOrbit ScriptRun style, lost on restart).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final Set<String> ROLE_ACCOUNTS = Set.of(
            "abuse", "accounts", "admin", "administrator", "billing", "careers", "contact",
            "customercare", "customerservice", "enquiries", "finance", "help", "hello", "hr",
            "info", "infos", "jobs", "legal", "mail", "marketing", "media", "news",
            "newsletter", "office", "postmaster", "press", "privacy", "sales", "security",
            "service", "support", "team", "webmaster");

    private static final Set<String> FREE_PROVIDERS = Set.of(
            "gmail.com", "googlemail.com", "yahoo.com", "yahoo.co.uk", "yahoo.co.in", "ymail.com",
            "hotmail.com", "hotmail.co.uk", "outlook.com", "live.com", "msn.com", "aol.com",
            "icloud.com", "me.com", "mail.com", "gmx.com", "gmx.de", "proton.me", "protonmail.com",
            "zoho.com", "yandex.com", "yandex.ru", "mail.ru", "rediffmail.com", "qq.com",
            "163.com", "126.com");

    public static class VerifyJob {
        private final long id;
        private final Long batchId;
        private final String scope;
        private volatile String status = "PENDING";   // PENDING / RUNNING / COMPLETED / FAILED
        private volatile int total;
        private final AtomicInteger processed = new AtomicInteger();
        private volatile String error;
        private volatile LocalDateTime startedAt;
        private volatile LocalDateTime finishedAt;

        VerifyJob(long id, Long batchId, String scope) {
            this.id = id;
            this.batchId = batchId;
            this.scope = scope;
        }

        public long getId() { return id; }
        public Long getBatchId() { return batchId; }
        public String getScope() { return scope; }
        public String getStatus() { return status; }
        public int getTotal() { return total; }
        public int getProcessed() { return processed.get(); }
        public String getError() { return error; }
        public LocalDateTime getStartedAt() { return startedAt; }
        public LocalDateTime getFinishedAt() { return finishedAt; }
    }

    private final EmailContactRepository contactRepository;
    private final SuppressionRepository suppressionRepository;
    private final DisposableDomains disposableDomains;
    private final MxResolver mxResolver;
    private final RuleEngine ruleEngine;

    @Value("${mailorbit.verify.threads:16}")
    private int workerThreads;

    private final Map<Long, VerifyJob> jobs = new ConcurrentHashMap<>();
    private final AtomicLong jobIdSequence = new AtomicLong();
    private volatile ExecutorService workers;
    private final ExecutorService master = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "verify-master");
        t.setDaemon(true);
        return t;
    });

    /**
     * @param batchId limit verification to one import batch (null = everything)
     * @param scope   "new" = only unverified contacts, "all" = re-verify everything
     */
    public VerifyJob start(Long batchId, String scope) {
        String effectiveScope = "all".equalsIgnoreCase(scope) ? "all" : "new";
        VerifyJob job = new VerifyJob(jobIdSequence.incrementAndGet(), batchId, effectiveScope);
        jobs.put(job.getId(), job);
        master.submit(() -> run(job));
        return job;
    }

    public VerifyJob getJob(long id) {
        return jobs.get(id);
    }

    public List<VerifyJob> listJobs() {
        return jobs.values().stream()
                .sorted((a, b) -> Long.compare(b.getId(), a.getId()))
                .toList();
    }

    private void run(VerifyJob job) {
        job.status = "RUNNING";
        job.startedAt = LocalDateTime.now();
        try {
            List<EmailContact> targets = selectTargets(job);
            job.total = targets.size();

            Set<String> suppressions = new HashSet<>();
            for (SuppressionEntry entry : suppressionRepository.findAll()) {
                suppressions.add(entry.getPattern().toLowerCase(Locale.ROOT));
            }

            ExecutorService pool = workerPool();
            List<Callable<Void>> tasks = new ArrayList<>(targets.size());
            for (EmailContact contact : targets) {
                tasks.add(() -> {
                    try {
                        verifyOne(contact, suppressions);
                    } catch (Exception e) {
                        log.error("Verification failed for {}: {}", contact.getEmail(), e.toString());
                    } finally {
                        job.processed.incrementAndGet();
                    }
                    return null;
                });
            }
            pool.invokeAll(tasks);
            job.status = "COMPLETED";
            log.info("Verify job {} completed: {} contacts", job.getId(), job.getTotal());
        } catch (Exception e) {
            job.status = "FAILED";
            job.error = e.toString();
            log.error("Verify job {} failed", job.getId(), e);
        } finally {
            job.finishedAt = LocalDateTime.now();
        }
    }

    private List<EmailContact> selectTargets(VerifyJob job) {
        if ("all".equals(job.getScope())) {
            return job.getBatchId() == null
                    ? contactRepository.findAll()
                    : contactRepository.findByBatchId(job.getBatchId());
        }
        return job.getBatchId() == null
                ? contactRepository.findByStatus(ContactStatus.NEW)
                : contactRepository.findByStatusAndBatchId(ContactStatus.NEW, job.getBatchId());
    }

    private void verifyOne(EmailContact contact, Set<String> suppressions) {
        String email = contact.getEmail();
        String domain = contact.getDomain() == null ? "" : contact.getDomain().toLowerCase(Locale.ROOT);
        List<String> reasons = new ArrayList<>();
        int score = 100;
        ContactStatus status;

        if (suppressions.contains(email) || suppressions.contains(domain)) {
            score = 0;
            status = ContactStatus.SUPPRESSED;
            reasons.add("On suppression list");
        } else {
            boolean syntaxOk = EmailNormalizer.isValidSyntax(email);
            contact.setSyntaxOk(syntaxOk);
            if (!syntaxOk) {
                score = 0;
                reasons.add("Invalid address syntax");
            } else {
                boolean disposable = disposableDomains.isDisposable(domain);
                contact.setDisposable(disposable);
                if (disposable) {
                    score = 0;
                    reasons.add("Disposable/throwaway domain");
                } else {
                    String localPart = contact.getLocalPart().toLowerCase(Locale.ROOT);
                    int plus = localPart.indexOf('+');
                    String bareLocal = plus > 0 ? localPart.substring(0, plus) : localPart;

                    boolean role = ROLE_ACCOUNTS.contains(bareLocal);
                    contact.setRoleAccount(role);
                    if (role) {
                        score -= 25;
                        reasons.add("Role account (" + bareLocal + "@)");
                    }

                    boolean free = FREE_PROVIDERS.contains(domain);
                    contact.setFreeProvider(free);
                    if (free) {
                        score -= 5;
                        reasons.add("Free mail provider");
                    }

                    MxResolver.MxResult mx = mxResolver.lookup(domain);
                    contact.setMxOk(mx.ok());
                    if (Boolean.FALSE.equals(mx.ok())) {
                        score = 0;
                        reasons.add(mx.detail());
                    } else if (mx.ok() == null) {
                        score -= 15;
                        reasons.add("MX unverified: " + mx.detail());
                    }

                    if (score > 0) {
                        score = applyGroovyRules(contact, score, reasons);
                    }
                }
            }
            score = Math.max(0, Math.min(100, score));
            status = score >= 80 ? ContactStatus.VALID
                    : score >= 50 ? ContactStatus.RISKY
                    : ContactStatus.INVALID;
        }

        contact.setScore(score);
        contact.setStatus(status);
        contact.setReasons(reasons.isEmpty() ? null : String.join("; ", reasons));
        contact.setVerifiedAt(LocalDateTime.now());
        contactRepository.save(contact);
    }

    private int applyGroovyRules(EmailContact contact, int score, List<String> reasons) {
        for (RuleEngine.Rule rule : ruleEngine.getRules()) {
            try {
                Object result = ((Closure<?>) rule.apply().clone()).call(contact);
                if (result instanceof Map<?, ?> m) {
                    Object delta = m.get("scoreDelta");
                    if (delta instanceof Number n) score += n.intValue();
                    Object reason = m.get("reason");
                    if (reason != null) reasons.add("[" + rule.name() + "] " + reason);
                    if (Boolean.TRUE.equals(m.get("fatal"))) score = 0;
                }
            } catch (Exception e) {
                log.warn("Rule {} failed on {}: {}", rule.name(), contact.getEmail(), e.toString());
            }
        }
        return score;
    }

    private synchronized ExecutorService workerPool() {
        if (workers == null || workers.isShutdown()) {
            workers = Executors.newFixedThreadPool(Math.max(1, workerThreads), r -> {
                Thread t = new Thread(r, "verify-worker");
                t.setDaemon(true);
                return t;
            });
        }
        return workers;
    }

    @PreDestroy
    public void shutdown() {
        master.shutdownNow();
        if (workers != null) workers.shutdownNow();
    }
}
