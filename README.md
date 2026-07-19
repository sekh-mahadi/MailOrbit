# MailOrbit 📬

Email list **verification & hygiene** platform — the DataOrbit sibling for lead-list cleaning.
Java 25 + Spring Boot 4.1 engine, Groovy 5 verification rules loaded at runtime, H2 zero-setup
local profile, and a single-page dashboard.

> **Scope / compliance:** MailOrbit verifies and cleans lists you already have a lawful basis to
> contact (opt-ins, licensed B2B data, existing customers). It deliberately contains **no
> harvesting/crawling** and **no SMTP RCPT probing** — mailbox-level checks belong to a proper
> verification API; MailOrbit stops at the DNS level.

## What it does

CSV in → verified, deduplicated, suppression-filtered CSV out.

Verification cascade (cheapest first), producing a 0–100 score and a status
(`VALID` ≥ 80, `RISKY` ≥ 50, else `INVALID`, or `SUPPRESSED`):

1. **Suppression list** — do-not-contact emails/domains (unsubscribes, complaints)
2. **Syntax** — RFC-lite validation, length limits, `..` etc.
3. **Disposable domains** — bundled `disposable-domains.txt` (subdomain-aware)
4. **Role accounts** — `info@`, `sales@`, … (score penalty) and free-provider flag
5. **DNS MX** — JNDI MX lookup with A-record fallback (implicit MX), cached per domain
6. **Groovy rules** — every `rules/*.groovy` file, hot-reloadable

Import already normalizes and dedupes: lowercasing, `Name <mail@host>` unwrapping,
`+tag` stripping and gmail dot-insensitivity via a `dedupe_key`.

## Build & run

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home
~/apache-maven-3.9.11/bin/mvn -DskipTests package

# zero-setup demo (in-memory H2); default profile expects PostgreSQL (see application.yml)
java -jar target/mailorbit-1.0.0.jar --spring.profiles.active=local
```

Dashboard: <http://localhost:8090> — *Load sample list* → *Run verification* → export.
Run from the project root so the `rules/` directory is found (or set `RULES_DIR`).

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/imports` | multipart CSV upload (flexible headers: `email`, `first name`, …) |
| POST | `/api/imports/sample` | load the bundled demo list |
| GET | `/api/imports` | import batches with dedupe counts |
| POST | `/api/verify?scope=new\|all&batchId=` | start async verification job |
| GET | `/api/verify/{id}` | job progress (`processed`/`total`) |
| GET | `/api/contacts?status=&q=&page=&size=` | paged contact list |
| GET | `/api/contacts/export?status=VALID\|RISKY\|ALL` | CSV download with scores & reasons |
| GET/POST/DELETE | `/api/suppressions` | do-not-contact list (email or domain) |
| GET | `/api/rules` · POST `/api/rules/reload` | Groovy rule management |
| GET | `/api/stats` · `/api/health` | dashboard stats / liveness |

## Groovy rules

`rules/*.groovy` (DataOrbit script-engine style) — each file returns:

```groovy
[
    name       : "typo-domains",
    description: "Flags misspelled freemail domains",
    apply      : { contact ->               // EmailContact: email, localPart, domain, ...
        contact.domain == "gmial.com" ? [fatal: true, reason: "Did you mean gmail.com?"]
                                      : null   // or [scoreDelta: -20, reason: "..."]
    }
]
```

Bundled: `typo_domains`, `no_reply`, `institutional_domains`. Extension ideas: a client for a
commercial verification API (ZeroBounce/NeverBounce) as a rule, industry allow/deny lists.

## Layout

```
src/main/java/com/mailorbit/
  entity/      EmailContact, ImportBatch, SuppressionEntry, ContactStatus
  repository/  Spring Data JPA repositories
  service/     CsvImportService, VerificationService (job registry),
               MxResolver, DisposableDomains, RuleEngine, ExportService
  controller/  imports, verify, contacts(+export), suppressions, rules, stats
  util/        EmailNormalizer (syntax, normalize, dedupe key)
rules/         runtime Groovy rules
src/main/resources/
  static/index.html          dashboard SPA
  samples/sample-contacts.csv demo list (typos, disposables, dupes, role accounts…)
  disposable-domains.txt
```

## License

Source-available for **portfolio review only** — all rights reserved. See [LICENSE](LICENSE) for details.
