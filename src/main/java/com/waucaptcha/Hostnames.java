package com.waucaptcha;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Compares the host name a token was solved on with the websites a site allows, for
 * {@link SiteVerifyResult#hostnameMatches(String...)}; the same rules as hostnameMatches of
 * {@code @waucaptcha-sdk/server}.
 */
final class Hostnames {
    private static final Pattern SEPARATORS = Pattern.compile("[\\s,]+");
    private static final Pattern SCHEME = Pattern.compile("^[a-z][a-z\\d+.-]*://");

    private Hostnames() {
    }

    /**
     * Whether {@code hostname} is one of the entries in {@code allowed}, each argument holding one
     * or more separated by commas or white space; null arguments are skipped. An entry
     * {@code *.example.com} stands for every subdomain of example.com, not example.com itself.
     */
    static boolean matches(String hostname, String... allowed) {
        String host = hostname == null ? "" : toHost(hostname);
        if (host.isEmpty() || allowed == null) {
            return false;
        }
        for (String entries : allowed) {
            if (entries == null) {
                continue;
            }
            for (String entry : SEPARATORS.split(entries)) {
                String pattern = toHost(entry);
                boolean match = pattern.startsWith("*.") ? host.endsWith(pattern.substring(1)) : !pattern.isEmpty() && host.equals(pattern);
                if (match) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The host name in a host name, origin or URL: lowercase, without scheme, user, port, path or final dot. */
    static String toHost(String raw) {
        String value = SCHEME.matcher(raw.strip().toLowerCase(Locale.ROOT)).replaceFirst("");
        if (value.startsWith("//")) {
            value = value.substring(2);
        }
        for (char end : new char[] {'/', '?', '#'}) {
            int index = value.indexOf(end);
            if (index >= 0) {
                value = value.substring(0, index);
            }
        }
        value = value.substring(value.lastIndexOf('@') + 1);
        if (value.startsWith("[")) {
            // An IPv6 address, which the service reports without its brackets.
            int end = value.indexOf(']');
            if (end > 0) {
                value = value.substring(1, end);
            }
        } else if (value.indexOf(':') == value.lastIndexOf(':') && value.indexOf(':') >= 0) {
            // One colon: a port. More are an IPv6 address written without brackets.
            value = value.substring(0, value.indexOf(':'));
        }
        return value.endsWith(".") ? value.substring(0, value.length() - 1) : value;
    }
}
