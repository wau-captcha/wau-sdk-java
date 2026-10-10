package com.waucaptcha;

import java.util.Map;
import java.util.OptionalInt;

/**
 * A call to WAU Captcha that failed. {@link #getCode()} is the reason the service gave, such as
 * {@link ErrorCode#SECRET_KEY_INVALID} or {@link ErrorCode#BEHAVIOR_REQUIRED}, or one the SDK
 * raised when no usable reply arrived: {@link ErrorCode#NETWORK_ERROR}, {@link ErrorCode#TIMEOUT},
 * {@link ErrorCode#INTERRUPTED} or {@link ErrorCode#INVALID_RESPONSE}. Compare it with the
 * {@link ErrorCode} constants.
 */
public class WauCaptchaException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** The error reason, an {@link ErrorCode} value. */
    private final String code;
    /** The HTTP status of the reply; 0 when no reply arrived. */
    private final int status;
    // Only meaningful where the call failed, so it is not carried through serialization.
    private final transient Map<String, String> metadata;
    /** The seconds of metadata {@code retry_after}; -1 when the reply named none. */
    private final int retryAfter;

    /**
     * An error the service answered with.
     *
     * @param code     the reason, e.g. {@link ErrorCode#RATE_LIMITED}
     * @param message  what went wrong
     * @param status   the HTTP status of the reply
     * @param metadata details the service attached, e.g. {@code retry_after}
     */
    public WauCaptchaException(String code, String message, int status, Map<String, String> metadata) {
        super(message);
        this.code = code;
        this.status = status;
        this.metadata = Map.copyOf(metadata);
        this.retryAfter = retryAfterOf(this.metadata);
    }

    /**
     * An error without a usable reply.
     *
     * @param code    the reason, e.g. {@link ErrorCode#NETWORK_ERROR}
     * @param message what went wrong
     * @param cause   the exception behind it
     */
    public WauCaptchaException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = 0;
        this.metadata = Map.of();
        this.retryAfter = -1;
    }

    /**
     * The error reason; a string, so reasons newer than this SDK still come through.
     *
     * @return an {@link ErrorCode} value, e.g. {@code SECRET_KEY_INVALID}
     */
    public String getCode() {
        return code;
    }

    /**
     * The HTTP status of the reply.
     *
     * @return the status; 0 when no reply arrived
     */
    public int getStatus() {
        return status;
    }

    /**
     * Details the service attached.
     *
     * @return e.g. {@code field} or {@code retry_after}
     */
    public Map<String, String> getMetadata() {
        return metadata == null ? Map.of() : metadata;
    }

    /**
     * With {@link ErrorCode#RATE_LIMITED}: the seconds to wait before trying again, from the reply's
     * metadata or its Retry-After header.
     *
     * @return the seconds; empty when the reply named none
     */
    public OptionalInt getRetryAfter() {
        return retryAfter < 0 ? OptionalInt.empty() : OptionalInt.of(retryAfter);
    }

    private static int retryAfterOf(Map<String, String> metadata) {
        try {
            int seconds = Integer.parseInt(metadata.getOrDefault("retry_after", "").strip());
            return seconds >= 0 ? seconds : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
