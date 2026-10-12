package com.waucaptcha;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NewChallengeTest {
    private static final String SITE_KEY = "pk_test_0123456789abcdef0123456789abcdef";
    private static final String ID = "0f9c6b5e-1d2a-4c3b-8e7f-9a0b1c2d3e4f";
    private static final byte[] SALT = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);

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
        return WauCaptchaClient.builder().baseUrl(service.baseUrl()).siteKey(SITE_KEY).build();
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    /** /challenge/new as the service answers it, with the fields of one type added. */
    private void replyChallenge(String type, String mimeType, int width, int height, String extra) {
        service.reply("/challenge/new", 200, "{\"challenge_id\":\"" + ID + "\",\"challenge_type\":\"" + type + "\",\"image\":\""
                + base64(type + "-image") + "\",\"mime_type\":\"" + mimeType + "\",\"width\":" + width + ",\"height\":" + height
                + ",\"expires_in\":120" + extra + "}");
    }

    @Test
    void imageDistort() {
        replyChallenge("image_distort", "image/png", 200, 70,
                ",\"pow_salt\":\"" + Base64.getEncoder().encodeToString(SALT) + "\",\"pow_difficulty\":15");
        Instant before = Instant.now();

        Challenge challenge = client().newChallenge(ChallengeType.IMAGE_DISTORT);

        StubService.Request request = service.last();
        assertEquals("GET", request.method());
        assertEquals("/challenge/new", request.path());
        assertEquals("site_key=" + SITE_KEY + "&challenge_type=image_distort", request.query());
        assertEquals(ID, challenge.id());
        assertEquals(ChallengeType.IMAGE_DISTORT, challenge.type());
        assertArrayEquals("image_distort-image".getBytes(StandardCharsets.UTF_8), challenge.image());
        assertEquals("image/png", challenge.mimeType());
        assertEquals(200, challenge.width());
        assertEquals(70, challenge.height());
        Duration left = Duration.between(before, challenge.expiresAt());
        assertTrue(left.getSeconds() >= 119 && left.getSeconds() <= 121, left.toString());
        assertNull(challenge.piece());
        assertNull(challenge.secondImage());
        assertArrayEquals(SALT, challenge.powSalt());
        assertEquals(15, challenge.powDifficulty());
        assertEquals("data:image/png;base64," + base64("image_distort-image"), challenge.dataUrl());
    }

    @Test
    void motionNoiseWithoutAProofOfWork() {
        replyChallenge("motion_noise", "image/gif", 300, 150, ",\"pow_salt\":\"\",\"pow_difficulty\":0");

        Challenge challenge = client().newChallenge(ChallengeType.MOTION_NOISE);

        assertEquals("site_key=" + SITE_KEY + "&challenge_type=motion_noise", service.last().query());
        assertEquals(ChallengeType.MOTION_NOISE, challenge.type());
        assertEquals("image/gif", challenge.mimeType());
        assertNull(challenge.piece());
        assertNull(challenge.secondImage());
        assertNull(challenge.powSalt(), "no proof of work is asked for");
        assertEquals(0, challenge.powDifficulty());
    }

    @Test
    void slidePuzzleCarriesThePiece() {
        replyChallenge("slide_puzzle", "image/jpeg", 300, 150, ",\"piece_image\":\"" + base64("piece") + "\",\"piece_y\":52,"
                + "\"piece_width\":50,\"piece_height\":48,\"pow_salt\":\"" + Base64.getEncoder().encodeToString(SALT) + "\",\"pow_difficulty\":15");

        Challenge challenge = client().newChallenge(ChallengeType.SLIDE_PUZZLE);

        assertEquals(ChallengeType.SLIDE_PUZZLE, challenge.type());
        assertEquals("image/jpeg", challenge.mimeType());
        Challenge.Piece piece = challenge.piece();
        assertArrayEquals("piece".getBytes(StandardCharsets.UTF_8), piece.image());
        assertEquals(52, piece.y());
        assertEquals(50, piece.width());
        assertEquals(48, piece.height());
        assertNull(challenge.secondImage());
    }

    @Test
    void animalJumpCarriesBothRounds() {
        replyChallenge("animal_jump", "image/gif", 300, 150, ",\"pow_salt\":\"" + Base64.getEncoder().encodeToString(SALT)
                + "\",\"pow_difficulty\":15,\"second_image\":\"" + base64("round-2") + "\"");

        Challenge challenge = client().newChallenge(ChallengeType.ANIMAL_JUMP);

        assertEquals("site_key=" + SITE_KEY + "&challenge_type=animal_jump", service.last().query());
        assertEquals(ChallengeType.ANIMAL_JUMP, challenge.type());
        assertArrayEquals("animal_jump-image".getBytes(StandardCharsets.UTF_8), challenge.image(), "image is round 1");
        assertArrayEquals("round-2".getBytes(StandardCharsets.UTF_8), challenge.secondImage());
        assertEquals(300, challenge.width());
        assertEquals(150, challenge.height());
        assertNull(challenge.piece());
        assertEquals(15, challenge.powDifficulty());
        assertNull(challenge.frameCount(), "frame_count is object_rotate's alone");
    }

    @Test
    void objectRotateCarriesBothStripsAndTheFrameCount() {
        replyChallenge("object_rotate", "image/jpeg", 300, 150, ",\"second_image\":\"" + base64("strip-2") + "\",\"frame_count\":8");

        Challenge challenge = client().newChallenge(ChallengeType.OBJECT_ROTATE);

        assertEquals("site_key=" + SITE_KEY + "&challenge_type=object_rotate", service.last().query());
        assertEquals(ChallengeType.OBJECT_ROTATE, challenge.type());
        assertArrayEquals("object_rotate-image".getBytes(StandardCharsets.UTF_8), challenge.image(), "image is round 1");
        assertArrayEquals("strip-2".getBytes(StandardCharsets.UTF_8), challenge.secondImage());
        assertEquals(8, challenge.frameCount());
        assertEquals("image/jpeg", challenge.mimeType());
        assertEquals(300, challenge.width(), "the width of one frame");
        assertNull(challenge.piece());
    }

    @Test
    void theBuilderSetsTheDefaultType() {
        replyChallenge("animal_jump", "image/gif", 300, 150, ",\"second_image\":\"" + base64("round-2") + "\"");
        WauCaptchaClient client = WauCaptchaClient.builder().baseUrl(service.baseUrl()).siteKey(SITE_KEY)
                .challengeType(ChallengeType.ANIMAL_JUMP).build();

        client.newChallenge();

        assertEquals("site_key=" + SITE_KEY + "&challenge_type=animal_jump", service.last().query());
    }

    @Test
    void theChallengeIsACopy() {
        replyChallenge("animal_jump", "image/gif", 300, 150, ",\"second_image\":\"" + base64("round-2") + "\"");
        Challenge challenge = client().newChallenge(ChallengeType.ANIMAL_JUMP);

        challenge.image()[0] = 0;
        challenge.secondImage()[0] = 0;

        assertArrayEquals("animal_jump-image".getBytes(StandardCharsets.UTF_8), challenge.image());
        assertArrayEquals("round-2".getBytes(StandardCharsets.UTF_8), challenge.secondImage());
    }

    @Test
    void refusalsComeThroughWithTheirCodes() {
        service.reply("/challenge/new", 403, """
                {"code":403,"reason":"CHALLENGE_TYPE_NOT_ALLOWED","message":"challenge type is not allowed for this plan","metadata":{}}""");

        WauCaptchaException error = assertThrows(WauCaptchaException.class, () -> client().newChallenge(ChallengeType.ANIMAL_JUMP));

        assertEquals(ErrorCode.CHALLENGE_TYPE_NOT_ALLOWED, error.getCode());
        assertEquals(403, error.getStatus());
    }

    @Test
    void aTypeThisSdkDoesNotKnowIsInvalidResponse() {
        replyChallenge("future_type", "image/png", 200, 70, "");

        WauCaptchaException error = assertThrows(WauCaptchaException.class, () -> client().newChallenge());

        assertEquals(ErrorCode.INVALID_RESPONSE, error.getCode());
    }

    @Test
    void newChallengeNeedsTheSiteKey() {
        WauCaptchaClient client = WauCaptchaClient.builder().baseUrl(service.baseUrl()).secretKey("sk_test").build();

        assertThrows(IllegalStateException.class, client::newChallenge);
    }
}
