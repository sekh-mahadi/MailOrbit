package com.mailorbit.util;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Email normalization + syntax checks shared by import and verification.
 */
public final class EmailNormalizer {

    private static final Pattern SYNTAX = Pattern.compile(
            "^[A-Za-z0-9._%+\\-']+@[A-Za-z0-9](?:[A-Za-z0-9.\\-]*[A-Za-z0-9])?\\.[A-Za-z]{2,}$");

    /** Providers where dots in the local part are ignored for delivery. */
    private static final Set<String> DOT_INSENSITIVE = Set.of("gmail.com", "googlemail.com");

    private EmailNormalizer() {
    }

    /** Lowercase + trim; returns null if there is no usable address. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String email = raw.trim();
        // tolerate "Name <mail@host>" and mailto: forms often found in exports
        int lt = email.indexOf('<');
        int gt = email.indexOf('>');
        if (lt >= 0 && gt > lt) email = email.substring(lt + 1, gt).trim();
        if (email.toLowerCase(Locale.ROOT).startsWith("mailto:")) email = email.substring(7);
        email = email.toLowerCase(Locale.ROOT);
        return email.isEmpty() || email.indexOf('@') <= 0 ? null : email;
    }

    public static boolean isValidSyntax(String email) {
        if (email == null || email.length() > 254 || !SYNTAX.matcher(email).matches()) return false;
        String local = email.substring(0, email.indexOf('@'));
        String domain = email.substring(email.indexOf('@') + 1);
        return local.length() <= 64
                && !local.startsWith(".") && !local.endsWith(".")
                && !email.contains("..")
                && domain.length() <= 253;
    }

    public static String domainOf(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1);
    }

    /**
     * Key for duplicate detection: lowercased, "+tag" stripped from the local
     * part, and dots removed from the local part for gmail-style providers
     * (jane.doe+news@gmail.com == janedoe@gmail.com).
     */
    public static String dedupeKey(String normalizedEmail) {
        int at = normalizedEmail.lastIndexOf('@');
        String local = normalizedEmail.substring(0, at);
        String domain = normalizedEmail.substring(at + 1);
        int plus = local.indexOf('+');
        if (plus > 0) local = local.substring(0, plus);
        if (DOT_INSENSITIVE.contains(domain)) local = local.replace(".", "");
        return local + "@" + domain;
    }
}
