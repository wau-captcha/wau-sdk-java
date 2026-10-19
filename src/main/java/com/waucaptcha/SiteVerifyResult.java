package com.waucaptcha;

import java.time.Instant;

/**
 * The outcome of redeeming a token. {@code success} is false when the token is unknown, expired,
 * already used, or was issued for another key; the other components are then {@code null}.
 *
 * @param success       whether the token proves a solved challenge for this key
 * @param challengeType the API name of the type of the solved challenge, e.g. {@code animal_jump};
 *                      {@link ChallengeType#fromWireName(String)} turns it into the enum.
 *                      {@code auto_check} means the visitor passed without a puzzle; for
 *                      sign-ups, logins and payments, require a {@link BehaviorResult#score()} at
 *                      or above a threshold of your own
 * @param solvedAt      when the challenge was solved
 * @param behavior      how the person behaved, as the service judged it: refuse
 *                      {@link BehaviorVerdict#NOT_MEASURED}, decide about
 *                      {@link BehaviorVerdict#SUSPICIOUS} per form
 * @param hostname      host name of the website the challenge was solved on, from the Origin of the
 *                      browser's requests: compare it with your own domains, with
 *                      {@link #hostnameMatches(String...)}. A script outside a browser can forge
 *                      it, so it stops other websites from using your key, not bots. Empty when
 *                      the challenge was asked for without an origin.
 */
public record SiteVerifyResult(boolean success, String challengeType, Instant solvedAt, BehaviorResult behavior, String hostname) {

    /**
     * Whether the token succeeded and {@link #hostname()} is one of {@code allowed}. Each argument
     * holds one entry or several, separated by commas or white space as an environment variable
     * holds them, and null arguments are skipped. Each entry is reduced to its host name, so
     * {@code https://Shop.example.com/}, {@code shop.example.com:443} and {@code shop.example.com}
     * are the same, and {@code *.example.com} stands for every subdomain of example.com but not
     * example.com itself. An empty hostname, or no entries, match nothing.
     *
     * <pre>{@code
     * if (!result.hostnameMatches(System.getenv("WAU_ALLOWED_HOSTNAMES"))) { ... }
     * }</pre>
     *
     * @param allowed the host names, origins or URLs of the websites that may use your key
     * @return true when the token succeeded on one of them
     */
    public boolean hostnameMatches(String... allowed) {
        return success && Hostnames.matches(hostname, allowed);
    }

    static SiteVerifyResult failed() {
        return new SiteVerifyResult(false, null, null, null, null);
    }
}
