package com.waucaptcha;

import java.time.Instant;
import java.util.Base64;
import java.util.Objects;

/**
 * A challenge to show to a person. It can be answered once, before {@code expiresAt}.
 *
 * @param id            the challenge's id
 * @param type          the challenge type
 * @param image         the media: a PNG for image_distort, an animated GIF for motion_noise and for
 *                      round 1 of animal_jump, the background JPEG for slide_puzzle, and for
 *                      object_rotate the JPEG strip of round 1
 * @param mimeType      {@code image/png}, {@code image/gif} or {@code image/jpeg}
 * @param width         width to display the image at, in CSS pixels; for object_rotate, of one frame
 * @param height        height to display the image at, in CSS pixels
 * @param expiresAt     after this the challenge can no longer be answered
 * @param piece         slide_puzzle only: the piece to drag over the image; {@code null} for the
 *                      other types
 * @param secondImage   round 2, {@code image} being round 1: for animal_jump an animated GIF (each
 *                      plays once and lasts 2 s), for object_rotate a strip like {@code image}.
 *                      {@code null} for the other types
 * @param powSalt       the proof of work the service asks for, 16 bytes; {@code null} when it asks
 *                      for none. {@link WauCaptchaClient} solves it and sends it with the answer.
 * @param powDifficulty the zero bits the proof of work needs; 0 when none is asked for
 * @param frameCount    object_rotate only: {@code image} and {@code secondImage} are each this many
 *                      frames of width × height side by side, frame k the object turned k steps
 *                      clockwise as seen from above; {@code null} for the other types
 */
public record Challenge(String id, ChallengeType type, byte[] image, String mimeType, int width, int height, Instant expiresAt,
        Piece piece, byte[] secondImage, byte[] powSalt, int powDifficulty, Integer frameCount) {

    /**
     * Copies the byte arrays, so the challenge cannot change under its holder.
     *
     * @param id            the challenge's id
     * @param type          the challenge type
     * @param image         the media
     * @param mimeType      the media type of {@code image}
     * @param width         width to display the image at, in CSS pixels
     * @param height        height to display the image at, in CSS pixels
     * @param expiresAt     after this the challenge can no longer be answered
     * @param piece         slide_puzzle only: the piece; {@code null} for the other types
     * @param secondImage   animal_jump and object_rotate: round 2; {@code null} for the other types
     * @param powSalt       the proof of work's salt; {@code null} when none is asked for
     * @param powDifficulty the zero bits the proof of work needs; 0 when none is asked for
     * @param frameCount    object_rotate only: the frames in each strip; {@code null} for the other types
     */
    public Challenge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        image = image.clone();
        secondImage = secondImage == null ? null : secondImage.clone();
        powSalt = powSalt == null ? null : powSalt.clone();
    }

    /**
     * A challenge without strips of frames, as 1.0.0 made them.
     *
     * @param id            the challenge's id
     * @param type          the challenge type
     * @param image         the media
     * @param mimeType      the media type of {@code image}
     * @param width         width to display the image at, in CSS pixels
     * @param height        height to display the image at, in CSS pixels
     * @param expiresAt     after this the challenge can no longer be answered
     * @param piece         slide_puzzle only: the piece; {@code null} for the other types
     * @param secondImage   animal_jump only: round 2; {@code null} for the other types
     * @param powSalt       the proof of work's salt; {@code null} when none is asked for
     * @param powDifficulty the zero bits the proof of work needs; 0 when none is asked for
     */
    public Challenge(String id, ChallengeType type, byte[] image, String mimeType, int width, int height, Instant expiresAt,
            Piece piece, byte[] secondImage, byte[] powSalt, int powDifficulty) {
        this(id, type, image, mimeType, width, height, expiresAt, piece, secondImage, powSalt, powDifficulty, null);
    }

    /**
     * A copy of the media bytes.
     *
     * @return the image, or round 1 of animal_jump and object_rotate
     */
    @Override
    public byte[] image() {
        return image.clone();
    }

    /**
     * A copy of round 2 of animal_jump or object_rotate.
     *
     * @return the GIF or the strip of round 2; {@code null} for the other types
     */
    @Override
    public byte[] secondImage() {
        return secondImage == null ? null : secondImage.clone();
    }

    /**
     * A copy of the salt of the proof of work.
     *
     * @return 16 bytes; {@code null} when no proof of work is asked for
     */
    @Override
    public byte[] powSalt() {
        return powSalt == null ? null : powSalt.clone();
    }

    /**
     * The media as a {@code data:} URL, ready for an HTML {@code <img src>}.
     *
     * @return the data URL of {@code image}
     */
    public String dataUrl() {
        return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(image);
    }

    /**
     * The slide_puzzle piece. It starts with its left edge at x = 0 and moves horizontally; its
     * position and size are in CSS pixels of the image.
     *
     * @param image  the piece, a PNG
     * @param y      where its top edge sits on the image
     * @param width  its width
     * @param height its height
     */
    public record Piece(byte[] image, int y, int width, int height) {

        /**
         * Copies the image.
         *
         * @param image  the piece, a PNG
         * @param y      where its top edge sits on the image
         * @param width  its width
         * @param height its height
         */
        public Piece {
            image = image.clone();
        }

        /**
         * A copy of the PNG of the piece.
         *
         * @return the piece
         */
        @Override
        public byte[] image() {
            return image.clone();
        }
    }
}
