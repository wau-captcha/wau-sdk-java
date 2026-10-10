package com.waucaptcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProofOfWorkTest {
    private static final byte[] SALT = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
    private static final String ID = "0f9c6b5e-1d2a-4c3b-8e7f-9a0b1c2d3e4f";

    /**
     * powSolved of services/challenge/internal/biz/pow.go, line for line: a decimal nonce of at most
     * 20 digits, and SHA-256(salt ‖ challenge id ‖ nonce) starting with {@code difficulty} zero bits.
     */
    static boolean powSolved(byte[] salt, String challengeId, String nonce, int difficulty) {
        if (nonce == null || nonce.isEmpty() || nonce.length() > 20) {
            return false;
        }
        for (char digit : nonce.toCharArray()) {
            if (digit < '0' || digit > '9') {
                return false;
            }
        }
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(salt);
            hash.update(challengeId.getBytes(StandardCharsets.UTF_8));
            hash.update(nonce.getBytes(StandardCharsets.UTF_8));
            return ProofOfWork.leadingZeroBits(hash.digest()) >= difficulty;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void findsTheNonceOfTheServiceVector() {
        // SHA-256 = 00092f9d…: exactly 12 zero bits (pow_test.go).
        Optional<String> nonce = ProofOfWork.solve(SALT, ID, 12);

        assertEquals(Optional.of("1233"), nonce);
        assertTrue(powSolved(SALT, ID, nonce.get(), 12));
        assertFalse(powSolved(SALT, ID, nonce.get(), 24), "too few zero bits for more");
        assertFalse(powSolved(SALT, ID, "1232", 12));
    }

    @Test
    void theNonceAtTheDefaultDifficultyPassesTheServiceCheck() {
        String nonce = ProofOfWork.solve(SALT, ID, 15).orElseThrow();

        assertTrue(powSolved(SALT, ID, nonce, 15));
        assertTrue(nonce.matches("0|[1-9][0-9]*"), "decimal, without leading zeros: " + nonce);
    }

    @Test
    void noWorkAskedTakesTheFirstNonce() {
        assertEquals(Optional.of("0"), ProofOfWork.solve(SALT, ID, 0));
    }

    @Test
    void aDifficultyOutOfRangeIsNotSolved() {
        assertEquals(Optional.empty(), ProofOfWork.solve(SALT, ID, -1));
        assertEquals(Optional.empty(), ProofOfWork.solve(SALT, ID, ProofOfWork.MAX_DIFFICULTY + 1));
    }

    @Test
    void leadingZeroBitsAsPowGoCountsThem() {
        assertEquals(0, ProofOfWork.leadingZeroBits(new byte[] {(byte) 0x80, 0x00}));
        assertEquals(7, ProofOfWork.leadingZeroBits(new byte[] {0x01}));
        assertEquals(12, ProofOfWork.leadingZeroBits(new byte[] {0x00, 0x0f}));
        assertEquals(17, ProofOfWork.leadingZeroBits(new byte[] {0x00, 0x00, 0x7f}));
        assertEquals(16, ProofOfWork.leadingZeroBits(new byte[] {0x00, 0x00}));
        assertEquals(0, ProofOfWork.leadingZeroBits(new byte[0]));
    }
}
