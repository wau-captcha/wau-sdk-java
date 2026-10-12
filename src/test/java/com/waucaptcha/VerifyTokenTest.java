package com.waucaptcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerifyTokenTest {
    private static final String SECRET_KEY = "sk_test_0123456789abcdef0123456789abcdef";

    private StubService service;

    @BeforeEach
    void start() throws IOException {
        service = new StubService();
    }

    @AfterEach
    void stop() {
        service.close();
    }

    private WauCaptchaClient client() {
        return WauCaptchaClient.builder().baseUrl(service.baseUrl()).secretKey(SECRET_KEY).build();
    }

    @Test
    void postsTheTokenInTheBodyWithTheBearerHeaderAndReadsEveryField() {
        service.reply("/siteverify", 200, """
                {"success": true, "challenge_type": "animal_jump", "challenge_ts": "2026-10-09T08:15:02.123456Z",
                 "behavior": {"verdict": "suspicious", "score": 0.4, "reasons": ["ip_changed", "no_pow"]},
                 "hostname": "shop.example.com"}""");

        SiteVerifyResult result = client().verifyToken(" the-token ");

        StubService.Request request = service.last();
        assertEquals("POST", request.method());
        assertEquals("/siteverify", request.path());
        assertNull(request.query(), "the token never goes in the URL");
        assertEquals("Bearer " + SECRET_KEY, request.header("Authorization"));
        assertEquals("application/json", request.header("Content-Type"));
        assertEquals("{\"token\":\"the-token\"}", request.body());

        assertTrue(result.success());
        assertEquals("animal_jump", result.challengeType());
        assertEquals(Optional.of(ChallengeType.ANIMAL_JUMP), ChallengeType.fromWireName(result.challengeType()));
        assertEquals(Instant.parse("2026-10-09T08:15:02.123456Z"), result.solvedAt());
        assertEquals(new BehaviorResult(BehaviorVerdict.SUSPICIOUS, 0.4, List.of("ip_changed", "no_pow")), result.behavior());
        assertEquals("shop.example.com", result.hostname());
    }

    @Test
    void aHumanVerdictWithAWholeScore() {
        service.reply("/siteverify", 200, """
                {"success":true,"challenge_type":"image_distort","challenge_ts":"2026-10-08T08:15:02Z",
                 "behavior":{"verdict":"human","score":1,"reasons":[]},"hostname":""}""");

        SiteVerifyResult result = client().verifyToken("token");

        assertEquals(new BehaviorResult(BehaviorVerdict.HUMAN, 1.0, List.of()), result.behavior());
        assertEquals("", result.hostname(), "empty when the challenge was asked for without an origin");
    }

    @Test
    void anUnknownExpiredOrUsedTokenIsNotASuccess() {
        service.reply("/siteverify", 200, """
                {"success":false,"challenge_type":"","challenge_ts":null,"behavior":null,"hostname":""}""");

        SiteVerifyResult result = client().verifyToken("token");

        assertFalse(result.success());
        assertNull(result.challengeType());
        assertNull(result.solvedAt());
        assertNull(result.behavior());
        assertNull(result.hostname());
    }

    @Test
    void aTokenOfAutoCheckMapsToItsConstant() {
        // A visitor auto_check let through without a puzzle; one who solved its fallback has the fallback's type.
        service.reply("/siteverify", 200, """
                {"success":true,"challenge_type":"auto_check","challenge_ts":"2026-10-12T08:15:02Z",
                 "behavior":{"verdict":"human","score":0.85,"reasons":["no_pointer"]},"hostname":"shop.example.com"}""");

        SiteVerifyResult result = client().verifyToken("token");

        assertTrue(result.success());
        assertEquals("auto_check", result.challengeType());
        assertEquals(Optional.of(ChallengeType.AUTO_CHECK), ChallengeType.fromWireName(result.challengeType()));
        assertEquals(new BehaviorResult(BehaviorVerdict.HUMAN, 0.85, List.of("no_pointer")), result.behavior());
    }

    @Test
    void aServiceWithoutTheBehaviourCheckOrAnUnknownVerdictReadsAsNotMeasured() {
        service.reply("/siteverify", 200, """
                {"success":true,"challenge_type":"motion_noise","challenge_ts":"2026-10-08T08:15:02Z"}""");
        SiteVerifyResult older = client().verifyToken("token");
        assertEquals(new BehaviorResult(BehaviorVerdict.NOT_MEASURED, null, List.of()), older.behavior());
        assertEquals("", older.hostname());

        service.reply("/siteverify", 200, """
                {"success":true,"challenge_type":"motion_noise","challenge_ts":"2026-10-08T08:15:02Z",
                 "behavior":{"verdict":"robotic","score":0.1,"reasons":["x"]}}""");
        assertEquals(BehaviorVerdict.NOT_MEASURED, client().verifyToken("token").behavior().verdict());
    }

    @Test
    void aBlankTokenFailsWithoutARequest() {
        assertFalse(client().verifyToken("  ").success());
        assertFalse(client().verifyToken(null).success());
        assertTrue(service.requests().isEmpty());
    }

    @Test
    void aBadSecretKeyIsSecretKeyInvalid() {
        service.reply("/siteverify", 401, """
                {"code":401,"reason":"SECRET_KEY_INVALID","message":"secret key is invalid","metadata":{}}""");

        WauCaptchaException error = assertThrows(WauCaptchaException.class, () -> client().verifyToken("token"));

        assertEquals(ErrorCode.SECRET_KEY_INVALID, error.getCode());
        assertEquals(401, error.getStatus());
        assertEquals("secret key is invalid", error.getMessage());
        assertEquals(OptionalInt.empty(), error.getRetryAfter());
    }

    @Test
    void aRateLimitedReplyCarriesTheSecondsToWait() {
        service.on("/siteverify", request -> new StubService.Reply(429,
                "{\"code\":429,\"reason\":\"RATE_LIMITED\",\"message\":\"too many requests\",\"metadata\":{}}",
                Map.of("Content-Type", "application/json", "Retry-After", "9")));
        WauCaptchaException fromHeader = assertThrows(WauCaptchaException.class, () -> client().verifyToken("token"));
        assertEquals(ErrorCode.RATE_LIMITED, fromHeader.getCode());
        assertEquals(OptionalInt.of(9), fromHeader.getRetryAfter());

        service.on("/siteverify", request -> new StubService.Reply(429,
                "{\"code\":429,\"reason\":\"RATE_LIMITED\",\"message\":\"too many requests\",\"metadata\":{\"retry_after\":\"17\"}}",
                Map.of("Content-Type", "application/json", "Retry-After", "9")));
        WauCaptchaException fromMetadata = assertThrows(WauCaptchaException.class, () -> client().verifyToken("token"));
        assertEquals(OptionalInt.of(17), fromMetadata.getRetryAfter());
        assertEquals("17", fromMetadata.getMetadata().get("retry_after"));
    }

    @Test
    void noReplyInTimeIsTimeout() {
        service.on("/siteverify", request -> {
            try {
                Thread.sleep(2_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return StubService.Reply.json(200, "{\"success\":false}");
        });
        WauCaptchaClient client = WauCaptchaClient.builder().baseUrl(service.baseUrl()).secretKey(SECRET_KEY)
                .timeout(Duration.ofMillis(200)).build();

        WauCaptchaException error = assertThrows(WauCaptchaException.class, () -> client.verifyToken("token"));

        assertEquals(ErrorCode.TIMEOUT, error.getCode());
        assertEquals(0, error.getStatus());
    }

    @Test
    void aReplyThatIsNotJsonIsInvalidResponse() {
        service.on("/siteverify", request -> new StubService.Reply(200, "<html>Welcome</html>", Map.of("Content-Type", "text/html")));
        WauCaptchaException ok = assertThrows(WauCaptchaException.class, () -> client().verifyToken("token"));
        assertEquals(ErrorCode.INVALID_RESPONSE, ok.getCode());
        assertEquals(200, ok.getStatus());

        service.on("/siteverify", request -> new StubService.Reply(502, "Bad Gateway", Map.of("Content-Type", "text/plain")));
        WauCaptchaException gateway = assertThrows(WauCaptchaException.class, () -> client().verifyToken("token"));
        assertEquals(ErrorCode.INVALID_RESPONSE, gateway.getCode());
        assertEquals(502, gateway.getStatus());
    }

    @Test
    void anErrorReplyWithoutAReasonIsUnknown() {
        service.reply("/siteverify", 500, "{}");

        WauCaptchaException error = assertThrows(WauCaptchaException.class, () -> client().verifyToken("token"));

        assertEquals(ErrorCode.UNKNOWN, error.getCode());
        assertEquals("HTTP 500", error.getMessage());
    }

    @Test
    void anApiThatCannotBeReachedIsNetworkError() throws IOException {
        String closed;
        try (StubService gone = new StubService()) {
            closed = gone.baseUrl();
        }
        WauCaptchaClient client = WauCaptchaClient.builder().baseUrl(closed).secretKey(SECRET_KEY).build();

        WauCaptchaException error = assertThrows(WauCaptchaException.class, () -> client.verifyToken("token"));

        assertEquals(ErrorCode.NETWORK_ERROR, error.getCode());
    }

    @Test
    void verifyTokenNeedsTheSecretKey() {
        WauCaptchaClient client = WauCaptchaClient.builder().baseUrl(service.baseUrl()).siteKey("pk_test").build();

        assertThrows(IllegalStateException.class, () -> client.verifyToken("token"));
    }
}
