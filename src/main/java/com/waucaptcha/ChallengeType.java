package com.waucaptcha;

import java.util.Optional;

/**
 * Kinds of challenge, by their API names. The plan of an API key decides which ones it may
 * request. {@link #SLIDE_PUZZLE} and {@link #ANIMAL_JUMP} need a pointer, so keyboard-only users
 * cannot solve them; {@link #OBJECT_ROTATE} works with a pointer or the keyboard, but needs sight.
 * Where accessibility matters, keep {@link #IMAGE_DISTORT} available as well.
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
     * Two rounds in which an object stands beside an arrow on the ground; in each, the person turns
     * it a frame at a time until it faces the arrow, then presses Done. It works with a pointer or
     * the keyboard, but needs sight.
     */
    OBJECT_ROTATE("object_rotate");

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
