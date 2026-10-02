package com.waucaptcha;

import java.time.Instant;
import java.util.Base64;

/**
 * A challenge to show to a person. It can be answered once, before {@code expiresAt}.
 *
 * @param id        pass it to {@link WauCaptchaClient#verifyAnswer(String, String)}
 * @param type      the challenge type, e.g. {@code image_distort}
 * @param image     the media: a PNG for image_distort, an animated GIF for motion_noise
 * @param mimeType  {@code image/png} or {@code image/gif}
 * @param width     width to display the image at, in CSS pixels
 * @param height    height to display the image at, in CSS pixels
 * @param expiresAt after this the challenge can no longer be answered
 */
public record Challenge(String id, String type, byte[] image, String mimeType, int width, int height, Instant expiresAt) {

    public Challenge {
        image = image.clone();
    }

    /** A copy of the media bytes. */
    @Override
    public byte[] image() {
        return image.clone();
    }

    /** The media as a {@code data:} URL, ready for an HTML {@code <img src>}. */
    public String dataUrl() {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);
    }
}
