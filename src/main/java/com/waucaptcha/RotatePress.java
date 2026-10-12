package com.waucaptcha;

/**
 * One press of an object_rotate round.
 *
 * @param ms        when, in whole milliseconds since the round was shown. The service wants the
 *                  first press at least 250 ms in and each one at least 60 ms after the one before.
 * @param direction 1 for Rotate right, the object turning a frame clockwise as seen from above; -1
 *                  for Rotate left
 */
public record RotatePress(int ms, int direction) {

    /**
     * Checks the direction.
     *
     * @param ms        when, in whole milliseconds since the round was shown
     * @param direction 1 for Rotate right, -1 for Rotate left
     * @throws IllegalArgumentException when direction is neither
     */
    public RotatePress {
        if (direction != 1 && direction != -1) {
            throw new IllegalArgumentException("direction must be 1 (right) or -1 (left), not " + direction);
        }
    }
}
