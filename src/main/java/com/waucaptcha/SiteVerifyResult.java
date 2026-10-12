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
 *                      browser's requests: compare it with your own domain. A script outside a
 *                      browser can forge it, so it stops other websites from using your key, not
 *                      bots. Empty when the challenge was asked for without an origin.
 */
public record SiteVerifyResult(boolean success, String challengeType, Instant solvedAt, BehaviorResult behavior, String hostname) {

    static SiteVerifyResult failed() {
        return new SiteVerifyResult(false, null, null, null, null);
    }
}
