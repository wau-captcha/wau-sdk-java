package com.waucaptcha;

/**
 * The codes a {@link WauCaptchaException} can carry: the reasons the service sends, then the ones
 * the SDK raises itself when no usable reply arrived. {@link WauCaptchaException#getCode()} is a
 * string, so reasons newer than this SDK still come through; compare it with these constants,
 * e.g. in a {@code switch}.
 */
public final class ErrorCode {
    /** The secret key is missing, or is not the secret key of an API key. */
    public static final String SECRET_KEY_INVALID = "SECRET_KEY_INVALID";
    /** The site key is not the site key of an API key. */
    public static final String SITE_KEY_INVALID = "SITE_KEY_INVALID";
    /** The address of the caller is not in the IP list of the key. */
    public static final String ORIGIN_NOT_ALLOWED = "ORIGIN_NOT_ALLOWED";
    /**
     * The website is not in the allowed domains of the key. A Java client sends no Origin, so a
     * key bound to domains refuses its challenge calls.
     */
    public static final String DOMAIN_NOT_ALLOWED = "DOMAIN_NOT_ALLOWED";
    /** The service does not know the challenge type asked for. */
    public static final String CHALLENGE_TYPE_UNSUPPORTED = "CHALLENGE_TYPE_UNSUPPORTED";
    /** The plan of the key does not include the challenge type asked for. */
    public static final String CHALLENGE_TYPE_NOT_ALLOWED = "CHALLENGE_TYPE_NOT_ALLOWED";
    /** The challenge expired, was answered already, or never existed: fetch a new one. */
    public static final String CHALLENGE_NOT_FOUND = "CHALLENGE_NOT_FOUND";
    /** The challenge id is not a UUID in its canonical 36-character form. */
    public static final String CHALLENGE_ID_INVALID = "CHALLENGE_ID_INVALID";
    /** The key requires behaviour data and the answer came without a {@link Behavior}. */
    public static final String BEHAVIOR_REQUIRED = "BEHAVIOR_REQUIRED";
    /** The key was suspended by an administrator, or automatically for scraping. */
    public static final String API_KEY_SUSPENDED = "API_KEY_SUSPENDED";
    /** The owner of the key was banned by an administrator. */
    public static final String ACCOUNT_BANNED = "ACCOUNT_BANNED";
    /** The owner used up the successful verifications their plan allows today. */
    public static final String DAILY_QUOTA_EXCEEDED = "DAILY_QUOTA_EXCEEDED";
    /**
     * Too many requests from this address or for this key: wait
     * {@link WauCaptchaException#getRetryAfter()} seconds.
     */
    public static final String RATE_LIMITED = "RATE_LIMITED";
    /** The request body is larger than the endpoint accepts. */
    public static final String PAYLOAD_TOO_LARGE = "PAYLOAD_TOO_LARGE";
    /** The service failed; the details stay in its log. */
    public static final String INTERNAL = "INTERNAL";
    /** No service handles the path: the base URL does not point at the WAU Captcha API. */
    public static final String ROUTE_NOT_FOUND = "ROUTE_NOT_FOUND";
    /** The service behind the path is unavailable: try again later. */
    public static final String UPSTREAM_UNAVAILABLE = "UPSTREAM_UNAVAILABLE";
    /** The API could not be reached. */
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    /** No reply came within the timeout. */
    public static final String TIMEOUT = "TIMEOUT";
    /** The thread was interrupted while it waited for a reply or solved the proof of work. */
    public static final String INTERRUPTED = "INTERRUPTED";
    /** A reply that is not the JSON the API sends. */
    public static final String INVALID_RESPONSE = "INVALID_RESPONSE";
    /** An error reply that named no reason. */
    public static final String UNKNOWN = "UNKNOWN";

    private ErrorCode() {
    }
}
