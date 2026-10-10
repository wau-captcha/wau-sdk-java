package com.waucaptcha;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Client for the WAU Captcha API. A site backend redeems tokens with {@link #verifyToken(String)}
 * and the secret key, as {@code @wau-captcha/server} does. The challenge calls use the public site
 * key and are those of {@code @wau-captcha/client} without its widget: keys that require behaviour
 * data, the default for new keys, refuse answers sent without a {@link Behavior}, so they suit
 * tests and desktop apps. On a website, show the JavaScript widget and verify its token here.
 *
 * <p>Instances are immutable and safe to share between threads.
 *
 * <pre>{@code
 * WauCaptchaClient wau = WauCaptchaClient.builder()
 *         .baseUrl("https://api.waucaptcha.com")
 *         .secretKey(System.getenv("WAU_SECRET_KEY"))
 *         .build();
 *
 * SiteVerifyResult result = wau.verifyToken(token);
 * if (result.success() && result.behavior().verdict() == BehaviorVerdict.HUMAN) {
 *     // the request comes from a person
 * }
 * }</pre>
 */
public final class WauCaptchaClient {
    /** Hosts that may be called over plain http, for local development. */
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "[::1]");

    private final String baseUrl;
    private final String secretKey;
    private final String siteKey;
    private final ChallengeType challengeType;
    private final Duration timeout;
    private final HttpClient httpClient;

    private WauCaptchaClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.secretKey = builder.secretKey;
        this.siteKey = builder.siteKey;
        this.challengeType = builder.challengeType;
        this.timeout = builder.timeout;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newBuilder().connectTimeout(builder.timeout).build();
    }

    /**
     * Starts the settings of a client.
     *
     * @return a builder; only the base URL is required
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Redeems a token once, with the secret key. A {@link WauCaptchaException} means the token could
     * not be checked, e.g. a wrong secret key ({@link ErrorCode#SECRET_KEY_INVALID}) or no reply
     * ({@link ErrorCode#NETWORK_ERROR}, {@link ErrorCode#TIMEOUT}): treat it as a failed check too.
     *
     * @param token the token the widget put in the form field {@code wau-captcha-token}
     * @return the outcome; not successful for a blank token
     * @throws IllegalStateException when the client has no secret key
     */
    public SiteVerifyResult verifyToken(String token) {
        if (secretKey == null) {
            throw new IllegalStateException("verifyToken needs the secret key: set it with Builder.secretKey");
        }
        if (token == null || token.isBlank()) {
            return SiteVerifyResult.failed();
        }
        // The token travels in the body, never in the URL, so it stays out of access logs.
        String body = "{\"token\":" + Json.string(token.strip()) + "}";
        HttpRequest request = request("/siteverify")
                .header("Authorization", "Bearer " + secretKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        Map<String, Object> reply = send(request);
        if (!Boolean.TRUE.equals(reply.get("success"))) {
            return SiteVerifyResult.failed();
        }
        return new SiteVerifyResult(true, stringField(reply, "challenge_type"), instantField(reply, "challenge_ts"),
                behaviorResult(reply.get("behavior")), reply.get("hostname") instanceof String hostname ? hostname : "");
    }

    /**
     * Fetches a new challenge of the type the builder set, {@link ChallengeType#IMAGE_DISTORT} unless
     * it set another; see {@link #newChallenge(ChallengeType)}.
     *
     * @return the challenge
     * @throws IllegalStateException when the client has no site key
     */
    public Challenge newChallenge() {
        return newChallenge(challengeType);
    }

    /**
     * Fetches a new challenge, with the site key. Each challenge can be answered once. Fetch one
     * when it is about to be shown, not ahead of time: challenges fetched and never answered count
     * against the key.
     *
     * @param type the type to ask for
     * @return the challenge
     * @throws IllegalStateException when the client has no site key
     */
    public Challenge newChallenge(ChallengeType type) {
        Objects.requireNonNull(type, "type");
        String key = requireSiteKey("newChallenge");
        HttpRequest request = request("/challenge/new?site_key=" + encode(key) + "&challenge_type=" + encode(type.wireName()))
                .GET()
                .build();
        Map<String, Object> reply = send(request);
        String replied = stringField(reply, "challenge_type");
        ChallengeType repliedType = ChallengeType.fromWireName(replied)
                .orElseThrow(() -> invalidResponse("reply names a challenge type this SDK does not know: " + replied));
        byte[] pieceImage = optionalBytes(reply, "piece_image");
        Challenge.Piece piece = pieceImage == null ? null
                : new Challenge.Piece(pieceImage, optionalInt(reply, "piece_y"), optionalInt(reply, "piece_width"), optionalInt(reply, "piece_height"));
        byte[] powSalt = optionalBytes(reply, "pow_salt");
        int powDifficulty = optionalInt(reply, "pow_difficulty");
        // Without both, no proof of work is asked for.
        if (powSalt == null || powDifficulty <= 0) {
            powSalt = null;
            powDifficulty = 0;
        }
        return new Challenge(
                stringField(reply, "challenge_id"),
                repliedType,
                bytesField(reply, "image"),
                stringField(reply, "mime_type"),
                intField(reply, "width"),
                intField(reply, "height"),
                Instant.now().plusSeconds(intField(reply, "expires_in")),
                piece,
                optionalBytes(reply, "second_image"),
                powSalt,
                powDifficulty);
    }

    /**
     * Submits the answer to an image_distort or motion_noise challenge, without behaviour data; see
     * {@link #verifyAnswer(Challenge, String, Behavior)}.
     *
     * @param challenge the challenge, as {@link #newChallenge(ChallengeType)} returned it
     * @param answer    the text read from the image
     * @return the outcome, with the token of a right answer
     */
    public AnswerResult verifyAnswer(Challenge challenge, String answer) {
        return verifyAnswer(challenge, answer, null);
    }

    /**
     * Submits the answer to an image_distort or motion_noise challenge, with the site key and the
     * proof of work the challenge asks for, which this call solves first. The challenge is used up
     * either way: after a wrong answer, fetch a new one. Answers are not case-sensitive. With
     * {@code behavior} the service also checks how the person behaved and refuses an answer a
     * script clearly gave; a key that requires behaviour data refuses an answer without it
     * ({@link ErrorCode#BEHAVIOR_REQUIRED}).
     *
     * @param challenge the challenge, as {@link #newChallenge(ChallengeType)} returned it
     * @param answer    the text read from the image
     * @param behavior  how the person behaved while the challenge was on screen; {@code null} for none
     * @return the outcome, with the token of a right answer
     * @throws IllegalStateException when the client has no site key
     */
    public AnswerResult verifyAnswer(Challenge challenge, String answer, Behavior behavior) {
        Objects.requireNonNull(challenge, "challenge");
        Objects.requireNonNull(answer, "answer");
        return submit(challenge, answer, behavior);
    }

    /**
     * Submits a slide_puzzle drag, without behaviour data; see
     * {@link #verifySlide(Challenge, SlideDrag, Behavior)}.
     *
     * @param challenge the slide_puzzle challenge
     * @param drag      the drag of the piece
     * @return the outcome, with the token of a right answer
     */
    public AnswerResult verifySlide(Challenge challenge, SlideDrag drag) {
        return verifySlide(challenge, drag, null);
    }

    /**
     * Submits a slide_puzzle drag: {@link #verifyAnswer(Challenge, String, Behavior)} with the drag
     * written as the JSON the service reads, in tenths of a pixel and millisecond like the browser
     * SDK, {@code {"x": .., "t": [[ms, x, y], ...]}}.
     *
     * @param challenge the slide_puzzle challenge
     * @param drag      the drag of the piece
     * @param behavior  how the person behaved while the challenge was on screen; {@code null} for none
     * @return the outcome, with the token of a right answer
     * @throws IllegalStateException when the client has no site key
     */
    public AnswerResult verifySlide(Challenge challenge, SlideDrag drag, Behavior behavior) {
        Objects.requireNonNull(drag, "drag");
        StringBuilder answer = new StringBuilder("{\"x\":").append(Json.number(round1(drag.x()))).append(",\"t\":[");
        List<SlideDrag.Point> trail = drag.trail();
        for (int i = 0; i < trail.size(); i++) {
            SlideDrag.Point point = trail.get(i);
            answer.append(i == 0 ? "[" : ",[")
                    .append(Json.number(round1(point.t()))).append(',')
                    .append(Json.number(round1(point.x()))).append(',')
                    .append(Json.number(round1(point.y()))).append(']');
        }
        return verifyAnswer(challenge, answer.append("]}").toString(), behavior);
    }

    /**
     * Submits the clicks of an animal_jump challenge, without behaviour data; see
     * {@link #verifyJump(Challenge, List, Behavior)}.
     *
     * @param challenge the animal_jump challenge
     * @param clicks    the click of each round, in order
     * @return the outcome, with the token of a right answer
     */
    public AnswerResult verifyJump(Challenge challenge, List<JumpClick> clicks) {
        return verifyJump(challenge, clicks, null);
    }

    /**
     * Submits the clicks of an animal_jump challenge, one per round in order:
     * {@link #verifyAnswer(Challenge, String, Behavior)} with the clicks written as the JSON the
     * service reads, in tenths of a pixel and millisecond like the browser SDK,
     * {@code {"c": [{"x": .., "y": .., "ms": .., "p": ..}, ...]}}. Both clicks must be right.
     *
     * @param challenge the animal_jump challenge
     * @param clicks    the click of each round, in order
     * @param behavior  how the person behaved while the challenge was on screen; {@code null} for none
     * @return the outcome, with the token of a right answer
     * @throws IllegalStateException when the client has no site key
     */
    public AnswerResult verifyJump(Challenge challenge, List<JumpClick> clicks, Behavior behavior) {
        Objects.requireNonNull(clicks, "clicks");
        StringBuilder answer = new StringBuilder("{\"c\":[");
        for (int i = 0; i < clicks.size(); i++) {
            JumpClick click = clicks.get(i);
            answer.append(i == 0 ? "" : ",")
                    .append("{\"x\":").append(Json.number(round1(click.x())))
                    .append(",\"y\":").append(Json.number(round1(click.y())))
                    .append(",\"ms\":").append(Json.number(round1(click.ms())))
                    .append(",\"p\":").append(click.replays()).append('}');
        }
        return verifyAnswer(challenge, answer.append("]}").toString(), behavior);
    }

    /** Sends an answer as the browser SDK does: with the site key, the behaviour if any and the proof of work. */
    private AnswerResult submit(Challenge challenge, String answer, Behavior behavior) {
        String key = requireSiteKey("the verify calls");
        StringBuilder body = new StringBuilder("{\"challenge_id\":").append(Json.string(challenge.id()))
                .append(",\"answer\":").append(Json.string(answer))
                .append(",\"site_key\":").append(Json.string(key));
        if (behavior != null) {
            body.append(",\"behavior\":").append(behavior.toJson());
        }
        byte[] salt = challenge.powSalt();
        if (salt != null) {
            ProofOfWork.solve(salt, challenge.id(), challenge.powDifficulty())
                    .ifPresent(nonce -> body.append(",\"pow_nonce\":").append(Json.string(nonce)));
        }
        HttpRequest request = request("/challenge/verify")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.append('}').toString(), StandardCharsets.UTF_8))
                .build();
        Map<String, Object> reply = send(request);
        String token = reply.get("token") instanceof String value ? value : "";
        if (!Boolean.TRUE.equals(reply.get("success")) || token.isEmpty()) {
            return AnswerResult.wrong();
        }
        long expiresIn = reply.get("expires_in") instanceof Number value ? value.longValue() : 0;
        return new AnswerResult(true, token, Instant.now().plusSeconds(expiresIn));
    }

    private String requireSiteKey(String call) {
        if (siteKey == null) {
            throw new IllegalStateException(call + " needs the site key: set it with Builder.siteKey");
        }
        return siteKey;
    }

    private HttpRequest.Builder request(String pathAndQuery) {
        return HttpRequest.newBuilder(URI.create(baseUrl + pathAndQuery))
                .timeout(timeout)
                .header("Accept", "application/json");
    }

    /** Sends a request and returns its JSON object; replies that are not 2xx throw. */
    private Map<String, Object> send(HttpRequest request) {
        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            throw new WauCaptchaException(ErrorCode.TIMEOUT, "no reply within " + timeout.toMillis() + " ms", e);
        } catch (IOException e) {
            throw new WauCaptchaException(ErrorCode.NETWORK_ERROR, "could not reach WAU Captcha at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WauCaptchaException(ErrorCode.INTERRUPTED, "interrupted while waiting for WAU Captcha", e);
        }

        Map<String, Object> reply = parseObject(response);
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            String reason = reply.get("reason") instanceof String value && !value.isEmpty() ? value : ErrorCode.UNKNOWN;
            String message = reply.get("message") instanceof String value && !value.isEmpty() ? value : "HTTP " + status;
            Map<String, String> metadata = metadata(reply);
            // The seconds a RATE_LIMITED reply asks to wait come in metadata.retry_after, or else in Retry-After.
            if (metadata.getOrDefault("retry_after", "").isEmpty()) {
                response.headers().firstValue("Retry-After").ifPresent(seconds -> metadata.put("retry_after", seconds));
            }
            throw new WauCaptchaException(reason, message, status, metadata);
        }
        return reply;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parseObject(HttpResponse<String> response) {
        try {
            if (Json.parse(response.body()) instanceof Map<?, ?> object) {
                return (Map<String, Object>) object;
            }
        } catch (IllegalArgumentException e) {
            // Falls through to the error below.
        }
        throw new WauCaptchaException(ErrorCode.INVALID_RESPONSE, "WAU Captcha answered " + response.statusCode() + " without a JSON object",
                response.statusCode(), Map.of());
    }

    private static Map<String, String> metadata(Map<String, Object> reply) {
        Map<String, String> metadata = new LinkedHashMap<>();
        if (reply.get("metadata") instanceof Map<?, ?> values) {
            values.forEach((key, value) -> {
                if (key instanceof String name && value instanceof String text) {
                    metadata.put(name, text);
                }
            });
        }
        return metadata;
    }

    /** A service that does not check behaviour yet, or a verdict this SDK does not know, reads as not measured. */
    private static BehaviorResult behaviorResult(Object raw) {
        if (!(raw instanceof Map<?, ?> behavior)) {
            return BehaviorResult.notMeasured();
        }
        BehaviorVerdict verdict = BehaviorVerdict.fromWireName(behavior.get("verdict"));
        if (verdict == BehaviorVerdict.NOT_MEASURED) {
            return BehaviorResult.notMeasured();
        }
        Double score = behavior.get("score") instanceof Number value ? value.doubleValue() : null;
        List<String> reasons = new ArrayList<>();
        if (behavior.get("reasons") instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof String reason) {
                    reasons.add(reason);
                }
            }
        }
        return new BehaviorResult(verdict, score, reasons);
    }

    private static WauCaptchaException invalidResponse(String message) {
        return new WauCaptchaException(ErrorCode.INVALID_RESPONSE, message, 200, Map.of());
    }

    private static String stringField(Map<String, Object> reply, String name) {
        if (reply.get(name) instanceof String value) {
            return value;
        }
        throw invalidResponse("reply has no text field '" + name + "'");
    }

    private static int intField(Map<String, Object> reply, String name) {
        if (reply.get(name) instanceof Number value) {
            return value.intValue();
        }
        throw invalidResponse("reply has no number field '" + name + "'");
    }

    /** A number the reply may leave out; 0 then. */
    private static int optionalInt(Map<String, Object> reply, String name) {
        return reply.get(name) instanceof Number value ? value.intValue() : 0;
    }

    private static byte[] bytesField(Map<String, Object> reply, String name) {
        try {
            return Base64.getDecoder().decode(stringField(reply, name));
        } catch (IllegalArgumentException e) {
            throw new WauCaptchaException(ErrorCode.INVALID_RESPONSE, "reply field '" + name + "' is not base64", e);
        }
    }

    /** Bytes the reply may leave out or leave empty; {@code null} then. */
    private static byte[] optionalBytes(Map<String, Object> reply, String name) {
        if (!(reply.get(name) instanceof String value) || value.isEmpty()) {
            return null;
        }
        return bytesField(reply, name);
    }

    private static Instant instantField(Map<String, Object> reply, String name) {
        try {
            return Instant.parse(stringField(reply, name));
        } catch (DateTimeParseException e) {
            throw new WauCaptchaException(ErrorCode.INVALID_RESPONSE, "reply field '" + name + "' is not a timestamp", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Tenths of a pixel or millisecond are as precise as the check needs, and keep the answer short. */
    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }

    /** Settings of a {@link WauCaptchaClient}. The base URL is required. */
    public static final class Builder {
        private String baseUrl;
        private String secretKey;
        private String siteKey;
        private ChallengeType challengeType = ChallengeType.IMAGE_DISTORT;
        private Duration timeout = Duration.ofSeconds(10);
        private HttpClient httpClient;

        private Builder() {
        }

        /**
         * Address of the WAU Captcha API. It must be https, so tokens and secret keys are never sent
         * in clear text; http is only allowed for localhost, 127.0.0.1 and [::1].
         *
         * @param baseUrl e.g. {@code https://api.waucaptcha.com}
         * @return this builder
         */
        public Builder baseUrl(String baseUrl) {
            Objects.requireNonNull(baseUrl, "baseUrl");
            this.baseUrl = baseUrl.strip().replaceAll("/+$", "");
            return this;
        }

        /**
         * The secret key, needed by {@link WauCaptchaClient#verifyToken(String)}. Keep it on the server.
         *
         * @param secretKey {@code sk_…}
         * @return this builder
         */
        public Builder secretKey(String secretKey) {
            this.secretKey = blankToNull(secretKey);
            return this;
        }

        /**
         * The public site key, needed by the challenge calls.
         *
         * @param siteKey {@code pk_…}
         * @return this builder
         */
        public Builder siteKey(String siteKey) {
            this.siteKey = blankToNull(siteKey);
            return this;
        }

        /**
         * The type {@link WauCaptchaClient#newChallenge()} asks for.
         *
         * @param challengeType {@link ChallengeType#IMAGE_DISTORT} by default
         * @return this builder
         */
        public Builder challengeType(ChallengeType challengeType) {
            this.challengeType = Objects.requireNonNull(challengeType, "challengeType");
            return this;
        }

        /**
         * How long to wait for each reply.
         *
         * @param timeout 10 seconds by default
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        /**
         * An HTTP client to use instead of a new one, e.g. to share a connection pool or set a proxy.
         *
         * @param httpClient the client
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            return this;
        }

        /**
         * Builds the client.
         *
         * @return the client
         * @throws IllegalStateException when the base URL is missing, not a URL, or not https (http
         *                               only for localhost, 127.0.0.1 and [::1])
         */
        public WauCaptchaClient build() {
            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new IllegalStateException("baseUrl is required, e.g. https://api.waucaptcha.com");
            }
            URI parsed;
            try {
                parsed = new URI(baseUrl);
            } catch (URISyntaxException e) {
                throw new IllegalStateException("baseUrl is not a URL: " + baseUrl, e);
            }
            String scheme = parsed.getScheme() == null ? "" : parsed.getScheme().toLowerCase(Locale.ROOT);
            String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.ROOT);
            boolean local = scheme.equals("http") && LOCAL_HOSTS.contains(host);
            if (host.isEmpty() || (!scheme.equals("https") && !local)) {
                throw new IllegalStateException("baseUrl must start with https:// so the token and secret key are never sent in clear text "
                        + "(http:// only for localhost, 127.0.0.1 or [::1]): " + baseUrl);
            }
            return new WauCaptchaClient(this);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }
}
