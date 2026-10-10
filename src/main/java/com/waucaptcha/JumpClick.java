package com.waucaptcha;

/**
 * The click of one animal_jump round, in CSS pixels of the 300 × 150 image.
 *
 * @param x       where it landed, from the left edge of the image
 * @param y       where it landed, from the top edge of the image
 * @param ms      milliseconds since the playback it was made in started; a replay starts the count
 *                again. The service takes a click before the high jump has been seen for a guess.
 * @param replays the replays used in the round, 0 to 3
 */
public record JumpClick(double x, double y, double ms, int replays) {

    /**
     * Checks that every number is finite.
     *
     * @param x       where it landed, from the left edge of the image
     * @param y       where it landed, from the top edge of the image
     * @param ms      milliseconds since the playback it was made in started
     * @param replays the replays used in the round
     */
    public JumpClick {
        Json.requireFinite(x, "x");
        Json.requireFinite(y, "y");
        Json.requireFinite(ms, "ms");
    }
}
