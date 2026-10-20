package com.waucaptcha;

import java.util.Optional;

/**
 * Kinds of challenge, by their API names. The plan of an API key decides which ones it may
 * request. {@link #SLIDE_PUZZLE} and {@link #ANIMAL_JUMP} need a pointer, so keyboard-only users
 * cannot solve them; {@link #OBJECT_ROTATE} works with a pointer or the keyboard, but needs sight;
 * {@link #AUTO_CHECK} asks most visitors for nothing and shows the rest a challenge of its fallback
 * type, so it is as accessible as the fallback. Where accessibility matters, keep
 * {@link #IMAGE_DISTORT} available as well.
 */
public enum ChallengeType {
    /** Distorted text in a PNG image. */
    IMAGE_DISTORT("image_distort"),
    /** Text hidden in a short animated GIF of moving dots. */
    MOTION_NOISE("motion_noise"),
    /** A puzzle piece to drag into its hole; the drag is checked for the movement of a hand. */
    SLIDE_PUZZLE("slide_puzzle"),
    /**
     * Two rounds of 2 s in which five animal heads jump; in each, the person clicks the one that
     * jumps highest.
     */
    ANIMAL_JUMP("animal_jump"),
    /**
     * Two rounds in which an arrow on the ground and an object stand in two panels; in each, the person turns
     * the object a frame at a time until it faces the way the arrow points, then presses Done. It works with a pointer or
     * the keyboard, but needs sight.
     */
    OBJECT_ROTATE("object_rotate"),
    /**
     * No puzzle for most visitors: the browser SDK checks the browser and the behaviour in it, and
     * shows a challenge of its fallback type only to those the service is unsure of. The check
     * needs a browser, so a Java backend meets this type only as the challenge type of
     * {@link WauCaptchaClient#verifyToken(String)}: a visitor who passed without a puzzle. One who
     * solved the fallback has a token of the fallback's type.
     */
    AUTO_CHECK("auto_check");

    private final String wireName;

    ChallengeType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The name the API uses.
     *
     * @return e.g. {@code image_distort}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * The type an API name stands for, e.g. the {@link SiteVerifyResult#challengeType()} of a token.
     *
     * @param wireName an API name, e.g. {@code animal_jump}
     * @return the type; empty for a name this SDK does not know, such as a type newer than it
     */
    public static Optional<ChallengeType> fromWireName(String wireName) {
        for (ChallengeType type : values()) {
            if (type.wireName.equals(wireName)) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
