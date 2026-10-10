package com.waucaptcha;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

/**
 * The proof of work of a challenge: the decimal nonce for which SHA-256(salt ‖ challenge id ‖
 * nonce) starts with {@code difficulty} zero bits. Found the way the browser SDK finds it, counting
 * up from 0, so the service checks both the same way. An answer without it is judged at most
 * suspicious.
 */
final class ProofOfWork {
    /** The most the service asks for; past it the work would keep a phone busy for minutes. */
    static final int MAX_DIFFICULTY = 32;
    // How many hashes go by between two looks at the interrupt flag.
    private static final int INTERRUPT_CHECK_MASK = 0xfff;

    private ProofOfWork() {
    }

    /**
     * Finds the nonce. Empty for a difficulty out of range, or past 256 times the expected work:
     * the answer then goes without it, as from an older SDK.
     *
     * @throws WauCaptchaException with {@link ErrorCode#INTERRUPTED} when the thread is interrupted
     */
    static Optional<String> solve(byte[] salt, String challengeId, int difficulty) {
        if (difficulty < 0 || difficulty > MAX_DIFFICULTY) {
            return Optional.empty();
        }
        MessageDigest sha256 = sha256();
        byte[] id = challengeId.getBytes(StandardCharsets.UTF_8);
        long limit = 1L << (difficulty + 8);
        for (long nonce = 0; nonce < limit; nonce++) {
            if ((nonce & INTERRUPT_CHECK_MASK) == 0 && Thread.currentThread().isInterrupted()) {
                throw new WauCaptchaException(ErrorCode.INTERRUPTED, "interrupted while solving the proof of work",
                        new InterruptedException());
            }
            String digits = Long.toString(nonce);
            sha256.update(salt);
            sha256.update(id);
            sha256.update(digits.getBytes(StandardCharsets.US_ASCII));
            if (leadingZeroBits(sha256.digest()) >= difficulty) {
                return Optional.of(digits);
            }
        }
        return Optional.empty();
    }

    /** How many zero bits {@code digest} starts with. */
    static int leadingZeroBits(byte[] digest) {
        int zeros = 0;
        for (byte value : digest) {
            if (value != 0) {
                return zeros + Integer.numberOfLeadingZeros(value & 0xff) - 24;
            }
            zeros += 8;
        }
        return zeros;
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform has SHA-256.
            throw new IllegalStateException(e);
        }
    }
}
