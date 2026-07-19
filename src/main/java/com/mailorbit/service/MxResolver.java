package com.mailorbit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DNS-level deliverability check: does the domain publish MX records
 * (or at least an A record, the RFC 5321 implicit MX)? Results are
 * cached per domain for the lifetime of the JVM.
 *
 * Deliberately NOT an SMTP RCPT probe - mailbox-level verification
 * should go through a proper verification API, not direct SMTP from
 * a residential IP.
 */
@Slf4j
@Service
public class MxResolver {

    /** ok = true (deliverable domain), false (no mail host), null (DNS lookup failed / unknown). */
    public record MxResult(Boolean ok, String detail) {
    }

    private final Map<String, MxResult> cache = new ConcurrentHashMap<>();

    @Value("${mailorbit.verify.mx-timeout-ms:3000}")
    private int timeoutMs;

    public MxResult lookup(String domain) {
        if (domain == null || domain.isEmpty()) return new MxResult(false, "No domain");
        return cache.computeIfAbsent(domain.toLowerCase(Locale.ROOT), this::query);
    }

    private MxResult query(String domain) {
        DirContext ctx = null;
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(timeoutMs));
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            ctx = new InitialDirContext(env);

            Attributes mxAttrs = ctx.getAttributes(domain, new String[]{"MX"});
            Attribute mx = mxAttrs.get("MX");
            if (mx != null && mx.size() > 0) {
                return new MxResult(true, "MX " + String.valueOf(mx.get(0)).trim());
            }
            Attributes aAttrs = ctx.getAttributes(domain, new String[]{"A"});
            Attribute a = aAttrs.get("A");
            if (a != null && a.size() > 0) {
                return new MxResult(true, "No MX, A record fallback (implicit MX)");
            }
            return new MxResult(false, "No MX or A records");
        } catch (NameNotFoundException e) {
            return new MxResult(false, "Domain does not resolve");
        } catch (NamingException e) {
            log.debug("DNS lookup failed for {}: {}", domain, e.toString());
            return new MxResult(null, "DNS lookup failed (" + e.getClass().getSimpleName() + ")");
        } finally {
            if (ctx != null) {
                try {
                    ctx.close();
                } catch (NamingException ignored) {
                }
            }
        }
    }

    public int cacheSize() {
        return cache.size();
    }
}
