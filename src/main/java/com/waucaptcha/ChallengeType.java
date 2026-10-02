package com.waucaptcha;

/** Kinds of challenge. The plan of an API key decides which ones it may request. */
public enum ChallengeType {
    /** Distorted text in a PNG image. */
    IMAGE_DISTORT("image_distort"),
    /** Text hidden in a short animated GIF of moving dots. */
    MOTION_NOISE("motion_noise");

    private final String wireName;

    ChallengeType(String wireName) {
        this.wireName = wireName;
    }

    /** The name the API uses, e.g. {@code image_distort}. */
    public String wireName() {
        return wireName;
    }
}
