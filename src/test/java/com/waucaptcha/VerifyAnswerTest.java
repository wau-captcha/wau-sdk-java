package com.waucaptcha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VerifyAnswerTest {
    private static final String SITE_KEY = "pk_test_0123456789abcdef0123456789abcdef";
    // The vector of services/challenge/internal/biz/pow_test.go and of the JavaScript SDK's tests.
    private static final String ID = "0f9c6b5e-1d2a-4c3b-8e7f-9a0b1c2d3e4f";
    private static final byte[] SALT = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

    private StubService service;

    @BeforeEach
    void start() throws IOException {
        service = new StubService();
        service.reply("/challenge/verify", 200, "{\"success\":true,\"token\":\"the-token\",\"expires_in\":120}");
    }

    @AfterEach
    void stop() {
        service.close();
    }

    private WauCaptchaClient client() {
        return WauCaptchaClient.builder().baseUrl(service.baseUrl()).siteKey(" " + SITE_KEY + " ").build();
    }

    private static Challenge challenge(ChallengeType type, byte[] salt, int difficulty) {
        return new Challenge(ID, type, new byte[] {1}, "image/gif", 300, 150, Instant.now().plusSeconds(120), null,
                type == ChallengeType.ANIMAL_JUMP ? new byte[] {2} : null, salt, difficulty);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sent() {
        StubService.Request request = service.last();
        assertEquals("POST", request.method());
        assertEquals("/challenge/verify", request.path());
        assertEquals("application/json", request.header("Content-Type"));
        return (Map<String, Object>) Json.parse(request.body());
    }

    @Test
    void sendsTheSiteKeyAndTheProofOfWorkThatPowGoAccepts() {
        Instant before = Instant.now();

        AnswerResult result = client().verifyAnswer(challenge(ChallengeType.IMAGE_DISTORT, SALT, 12), "a8Kq2");

        Map<String, Object> body = sent();
        assertEquals(ID, body.get("challenge_id"));
        assertEquals("a8Kq2", body.get("answer"));
        assertEquals(SITE_KEY, body.get("site_key"));
        assertEquals("1233", body.get("pow_nonce"), "the first nonce counting up from 0, as the browser SDK finds it");
        assertTrue(ProofOfWorkTest.powSolved(SALT, ID, (String) body.get("pow_nonce"), 12));
        assertFalse(body.containsKey("behavior"));
        assertTrue(result.success());
        assertEquals("the-token", result.token());
        assertTrue(!result.expiresAt().isBefore(before.plusSeconds(119)));
    }

    @Test
    void sendsNoProofOfWorkWhenNoneIsAskedFor() {
        client().verifyAnswer(challenge(ChallengeType.MOTION_NOISE, null, 0), "x7pQ");

        Map<String, Object> body = sent();
        assertEquals(SITE_KEY, body.get("site_key"));
        assertFalse(body.containsKey("pow_nonce"));
    }

    @Test
    void aWrongAnswerHasNoToken() {
        service.reply("/challenge/verify", 200, "{\"success\":false,\"token\":\"\",\"expires_in\":0}");

        AnswerResult result = client().verifyAnswer(challenge(ChallengeType.IMAGE_DISTORT, null, 0), "wrong");

        assertFalse(result.success());
        assertNull(result.token());
        assertNull(result.expiresAt());
    }

    @Test
    void aKeyThatRequiresBehaviourRefusesAnAnswerWithout() {
        service.reply("/challenge/verify", 400, """
                {"code":400,"reason":"BEHAVIOR_REQUIRED","message":"this key requires behaviour data","metadata":{}}""");

        WauCaptchaException error = assertThrows(WauCaptchaException.class,
                () -> client().verifyAnswer(challenge(ChallengeType.IMAGE_DISTORT, null, 0), "a8Kq2"));

        assertEquals(ErrorCode.BEHAVIOR_REQUIRED, error.getCode());
        assertEquals(400, error.getStatus());
    }

    @Test
    void theBehaviourGoesWithTheFieldNamesOfTheApi() {
        Behavior behavior = new Behavior(Behavior.VERSION, 4210,
                List.of(new Behavior.PointerSample(0, 12.5, 30), new Behavior.PointerSample(16, 13.25, 31)), "mouse",
                List.of(180, 240), 5, 0, 0, false, 0, false, false, false);

        client().verifyAnswer(challenge(ChallengeType.IMAGE_DISTORT, null, 0), "a8Kq2", behavior);

        String body = service.last().body();
        assertTrue(body.contains(",\"behavior\":{\"version\":1,\"elapsed_ms\":4210,\"pointer\":[{\"t\":0,\"x\":12.5,\"y\":30},"
                + "{\"t\":16,\"x\":13.25,\"y\":31}],\"pointer_type\":\"mouse\",\"key_intervals_ms\":[180,240],\"input_events\":5,"
                + "\"paste_events\":0,\"untrusted_events\":0,\"webdriver\":false,\"hidden_ms\":0,\"headless_ua\":false,"
                + "\"no_languages\":false,\"chrome_without_window_chrome\":false}"), body);
    }

    @Test
    void verifySlideWritesTheDragAsTheJavaScriptSdkDoes() {
        SlideDrag drag = new SlideDrag(120.26, List.of(new SlideDrag.Point(0, 0, 0), new SlideDrag.Point(16.66, 10.54, -0.26),
                new SlideDrag.Point(400.04, 120.26, 1.25)));

        client().verifySlide(challenge(ChallengeType.SLIDE_PUZZLE, null, 0), drag);

        assertEquals("{\"x\":120.3,\"t\":[[0,0,0],[16.7,10.5,-0.3],[400,120.3,1.3]]}", sent().get("answer"));
    }

    @Test
    void verifyJumpWritesTheClicksAsTheJavaScriptSdkDoes() {
        // The same clicks as the test of verifyJump in @wau-captcha/client, and the same JSON.
        List<JumpClick> clicks = List.of(new JumpClick(31.2345, 100.06, 1712.449, 0), new JumpClick(280, 99.94, 2200.05, 2));
        Behavior behavior = new Behavior(Behavior.VERSION, 5000, List.of(), "mouse", List.of(), 0, 0, 0, false, 0, false, false, false);

        AnswerResult result = client().verifyJump(challenge(ChallengeType.ANIMAL_JUMP, SALT, 12), clicks, behavior);

        Map<String, Object> body = sent();
        assertEquals("{\"c\":[{\"x\":31.2,\"y\":100.1,\"ms\":1712.4,\"p\":0},{\"x\":280,\"y\":99.9,\"ms\":2200.1,\"p\":2}]}", body.get("answer"));
        assertEquals("1233", body.get("pow_nonce"));
        assertTrue(body.get("behavior") instanceof Map<?, ?>);
        assertTrue(result.success());
    }

    @Test
    void verifyRotateWritesThePressesAsTheJavaScriptSdkDoes() {
        // The same rounds as the test of verifyRotate in @wau-captcha/client, and the same JSON.
        List<RotateRound> rounds = List.of(
                new RotateRound(3, List.of(new RotatePress(812, 1), new RotatePress(1240, 1), new RotatePress(1531, 1)), 2100),
                new RotateRound(6, List.of(new RotatePress(600, -1), new RotatePress(900, -1)), 1300));
        Challenge challenge = new Challenge(ID, ChallengeType.OBJECT_ROTATE, new byte[] {1}, "image/jpeg", 300, 150,
                Instant.now().plusSeconds(120), null, new byte[] {2}, SALT, 12, 8, null);

        AnswerResult result = client().verifyRotate(challenge, rounds);

        Map<String, Object> body = sent();
        assertEquals("{\"r\":[{\"f\":3,\"s\":[[812,1],[1240,1],[1531,1]],\"done\":2100},{\"f\":6,\"s\":[[600,-1],[900,-1]],\"done\":1300}]}",
                body.get("answer"));
        assertEquals("1233", body.get("pow_nonce"));
        assertFalse(body.containsKey("behavior"));
        assertTrue(result.success());

        Behavior behavior = new Behavior(Behavior.VERSION, 5200, List.of(), "", List.of(410, 380, 520), 0, 0, 0, false, 0, false, false, false);
        client().verifyRotate(challenge, rounds, behavior);
        assertTrue(sent().get("behavior") instanceof Map<?, ?>);
    }

    @Test
    void aPressTurnsOneWayOrTheOther() {
        assertThrows(IllegalArgumentException.class, () -> new RotatePress(600, 0));
        assertThrows(IllegalArgumentException.class, () -> new RotatePress(600, 2));
        assertThrows(NullPointerException.class, () -> new RotateRound(1, null, 900));
        List<RotatePress> presses = new ArrayList<>(List.of(new RotatePress(600, 1)));
        RotateRound round = new RotateRound(1, presses, 900);
        presses.add(new RotatePress(700, 1));
        assertEquals(1, round.presses().size(), "the round keeps its own copy");
    }

    @Test
    void numbersThatJsonCannotHoldAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> new JumpClick(Double.NaN, 10, 1000, 0));
        assertThrows(IllegalArgumentException.class, () -> new JumpClick(10, 10, Double.POSITIVE_INFINITY, 0));
        assertThrows(IllegalArgumentException.class, () -> new SlideDrag.Point(0, Double.NaN, 0));
    }

    @Test
    void theVerifyCallsNeedTheSiteKey() {
        WauCaptchaClient client = WauCaptchaClient.builder().baseUrl(service.baseUrl()).secretKey("sk_test").build();

        assertThrows(IllegalStateException.class, () -> client.verifyAnswer(challenge(ChallengeType.IMAGE_DISTORT, null, 0), "a8Kq2"));
    }
}
