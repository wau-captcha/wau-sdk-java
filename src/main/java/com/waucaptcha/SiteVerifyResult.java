package com.waucaptcha;

import java.time.Instant;

/**
 * The outcome of redeeming a token. {@code success} is false when the token is unknown,
 * expired, already used, or was issued for another key.
 *
 * @param success       whether the token proves a solved challenge for this key
 * @param challengeType the type of the solved challenge; {@code null} when not successful
 * @param solvedAt      when the challenge was solved; {@code null} when not successful
 */
public record SiteVerifyResult(boolean success, String challengeType, Instant solvedAt) {

    static SiteVerifyResult failed() {
        return new SiteVerifyResult(false, null, null);
    }
}
