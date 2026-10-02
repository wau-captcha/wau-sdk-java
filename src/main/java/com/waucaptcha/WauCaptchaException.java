package com.waucaptcha;

import java.util.Map;

/**
 * A call to WAU Captcha that failed. {@link #getCode()} is the reason the service gave, such as
 * {@code SECRET_KEY_INVALID} or {@code ORIGIN_NOT_ALLOWED}, or one of {@code NETWORK_ERROR},
 * {@code TIMEOUT}, {@code INTERRUPTED} and {@code INVALID_RESPONSE} when no usable reply arrived.
 */
public class WauCaptchaException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final String code;
    private final int status;
    // Only meaningful where the call failed, so it is not carried through serialization.
    private final transient Map<String, String> metadata;

    public WauCaptchaException(String code, String message, int status, Map<String, String> metadata) {
        super(message);
        this.code = code;
        this.status = status;
        this.metadata = Map.copyOf(metadata);
    }

    public WauCaptchaException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = 0;
        this.metadata = Map.of();
    }

    /** The error reason, e.g. {@code SECRET_KEY_INVALID}. */
    public String getCode() {
        return code;
    }

    /** HTTP status of the reply; 0 when no reply arrived. */
    public int getStatus() {
        return status;
    }

    /** Details the service attached, e.g. {@code field}. */
    public Map<String, String> getMetadata() {
        return metadata == null ? Map.of() : metadata;
    }
}
