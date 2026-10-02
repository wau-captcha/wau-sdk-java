package com.waucaptcha;

import java.time.Instant;

/**
 * The outcome of an answer. A correct one carries the one-time token a site backend redeems
 * with {@link WauCaptchaClient#verifyToken(String)}.
 *
 * @param success   whether the answer was correct
 * @param token     the one-time token; {@code null} when the answer was wrong
 * @param expiresAt when the token stops working; {@code null} when the answer was wrong
 */
public record AnswerResult(boolean success, String token, Instant expiresAt) {

    static AnswerResult wrong() {
        return new AnswerResult(false, null, null);
    }
}
