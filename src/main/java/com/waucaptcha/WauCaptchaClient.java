package com.waucaptcha;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Client for the WAU Captcha API. A site backend redeems tokens with
 * {@link #verifyToken(String)}, using the secret key. The challenge calls, which use the public
 * site key, serve Java frontends such as desktop or Android apps.
 *
 * <p>Instances are immutable and safe to share between threads.
 *
 * <pre>{@code
 * WauCaptchaClient wau = WauCaptchaClient.builder()
 *         .baseUrl("https://captcha.example.com")
 *         .secretKey(System.getenv("WAU_SECRET_KEY"))
 *         .build();
 *
 * if (wau.verifyToken(token).success()) {
 *     // the request comes from a person
 * }
 * }</pre>
 */
public final class WauCaptchaClient {
    private final String baseUrl;
    private final String secretKey;
    private final String siteKey;
    private final Duration timeout;
    private final HttpClient httpClient;

    private WauCaptchaClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.secretKey = builder.secretKey;
        this.siteKey = builder.siteKey;
        this.timeout = builder.timeout;
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newBuilder().connectTimeout(builder.timeout).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Redeems a token once. Needs the secret key. A {@link WauCaptchaException} means the token
     * could not be checked (e.g. {@code SECRET_KEY_INVALID}, {@code NETWORK_ERROR}); treat it as a
     * failed check.
     *
     * @param token the token the frontend obtained, e.g. the form field {@code wau-captcha-token}
     */
    public SiteVerifyResult verifyToken(String token) {
        if (secretKey == null) {
            throw new IllegalStateException("verifyToken needs the secret key: set it with Builder.secretKey");
        }
        if (token == null || token.isBlank()) {
            return SiteVerifyResult.failed();
        }
        HttpRequest request = request("/siteverify?token=" + encode(token.strip()))
                .header("Authorization", "Bearer " + secretKey)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        Map<String, Object> reply = send(request);
        if (!Boolean.TRUE.equals(reply.get("success"))) {
            return SiteVerifyResult.failed();
        }
        return new SiteVerifyResult(true, stringField(reply, "challenge_type"), instantField(reply, "challenge_ts"));
    }

    /** Fetches a new image_distort challenge. Needs the site key. */
    public Challenge newChallenge() {
        return newChallenge(ChallengeType.IMAGE_DISTORT);
    }

    /** Fetches a new challenge of the given type. Needs the site key. */
    public Challenge newChallenge(ChallengeType type) {
        Objects.requireNonNull(type, "type");
        if (siteKey == null) {
            throw new IllegalStateException("newChallenge needs the site key: set it with Builder.siteKey");
        }
        HttpRequest request = request("/challenge/new?site_key=" + encode(siteKey) + "&challenge_type=" + encode(type.wireName()))
                .GET()
                .build();
        Map<String, Object> reply = send(request);
        return new Challenge(
                stringField(reply, "challenge_id"),
                stringField(reply, "challenge_type"),
                Base64.getDecoder().decode(stringField(reply, "image")),
                stringField(reply, "mime_type"),
                intField(reply, "width"),
                intField(reply, "height"),
                Instant.now().plusSeconds(intField(reply, "expires_in")));
    }

    /**
     * Submits the answer to a challenge. The challenge is used up either way: after a wrong
     * answer, fetch a new one. Answers are not case-sensitive.
     */
    public AnswerResult verifyAnswer(String challengeId, String answer) {
        Objects.requireNonNull(challengeId, "challengeId");
        Objects.requireNonNull(answer, "answer");
        String body = "{\"challenge_id\":" + quote(challengeId) + ",\"answer\":" + quote(answer) + "}";
        HttpRequest request = request("/challenge/verify")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        Map<String, Object> reply = send(request);
        String token = reply.get("token") instanceof String value ? value : "";
        if (!Boolean.TRUE.equals(reply.get("success")) || token.isEmpty()) {
            return AnswerResult.wrong();
        }
        return new AnswerResult(true, token, Instant.now().plusSeconds(intField(reply, "expires_in")));
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
            throw new WauCaptchaException("TIMEOUT", "no reply within " + timeout.toMillis() + " ms", e);
        } catch (IOException e) {
            throw new WauCaptchaException("NETWORK_ERROR", "could not reach WAU Captcha at " + baseUrl + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WauCaptchaException("INTERRUPTED", "interrupted while waiting for WAU Captcha", e);
        }

        Map<String, Object> reply = parseObject(response);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String reason = reply.get("reason") instanceof String value && !value.isEmpty() ? value : "UNKNOWN";
            String message = reply.get("message") instanceof String value && !value.isEmpty() ? value : "HTTP " + response.statusCode();
            throw new WauCaptchaException(reason, message, response.statusCode(), metadata(reply));
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
        throw new WauCaptchaException("INVALID_RESPONSE", "WAU Captcha answered " + response.statusCode() + " without a JSON object",
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

    private static String stringField(Map<String, Object> reply, String name) {
        if (reply.get(name) instanceof String value) {
            return value;
        }
        throw new WauCaptchaException("INVALID_RESPONSE", "reply has no text field '" + name + "'", 200, Map.of());
    }

    private static int intField(Map<String, Object> reply, String name) {
        if (reply.get(name) instanceof Number value) {
            return value.intValue();
        }
        throw new WauCaptchaException("INVALID_RESPONSE", "reply has no number field '" + name + "'", 200, Map.of());
    }

    private static Instant instantField(Map<String, Object> reply, String name) {
        try {
            return Instant.parse(stringField(reply, name));
        } catch (DateTimeParseException e) {
            throw new WauCaptchaException("INVALID_RESPONSE", "reply field '" + name + "' is not a timestamp", e);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** A JSON string literal. */
    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> quoted.append("\\\"");
                case '\\' -> quoted.append("\\\\");
                case '\n' -> quoted.append("\\n");
                case '\r' -> quoted.append("\\r");
                case '\t' -> quoted.append("\\t");
                default -> {
                    if (character < 0x20) {
                        quoted.append(String.format("\\u%04x", (int) character));
                    } else {
                        quoted.append(character);
                    }
                }
            }
        }
        return quoted.append('"').toString();
    }

    /** Settings of a {@link WauCaptchaClient}. The base URL is required. */
    public static final class Builder {
        private String baseUrl;
        private String secretKey;
        private String siteKey;
        private Duration timeout = Duration.ofSeconds(10);
        private HttpClient httpClient;

        private Builder() {
        }

        /** Address of the WAU Captcha API, e.g. {@code https://captcha.example.com}. */
        public Builder baseUrl(String baseUrl) {
            Objects.requireNonNull(baseUrl, "baseUrl");
            this.baseUrl = baseUrl.strip().replaceAll("/+$", "");
            return this;
        }

        /** Secret key {@code sk_…}, needed by {@link WauCaptchaClient#verifyToken(String)}. Keep it on the server. */
        public Builder secretKey(String secretKey) {
            this.secretKey = blankToNull(secretKey);
            return this;
        }

        /** Public site key {@code pk_…}, needed by the challenge calls. */
        public Builder siteKey(String siteKey) {
            this.siteKey = blankToNull(siteKey);
            return this;
        }

        /** How long to wait for each reply; 10 seconds by default. */
        public Builder timeout(Duration timeout) {
            Objects.requireNonNull(timeout, "timeout");
            if (timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            this.timeout = timeout;
            return this;
        }

        /** An HTTP client to use instead of a new one, e.g. to share a connection pool or set a proxy. */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
            return this;
        }

        public WauCaptchaClient build() {
            if (baseUrl == null || baseUrl.isEmpty()) {
                throw new IllegalStateException("baseUrl is required, e.g. https://captcha.example.com");
            }
            URI parsed = URI.create(baseUrl);
            if (parsed.getScheme() == null || parsed.getHost() == null) {
                throw new IllegalStateException("baseUrl must be an absolute http(s) URL: " + baseUrl);
            }
            return new WauCaptchaClient(this);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.strip();
        }
    }
}
