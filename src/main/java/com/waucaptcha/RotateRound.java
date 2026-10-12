package com.waucaptcha;

import java.util.List;
import java.util.Objects;

/**
 * One object_rotate round, as the browser SDK records it: Rotate left and Rotate right turn the
 * object a frame of the strip at a time, starting from frame 0, and Done ends the round.
 *
 * @param frame   the frame on screen when Done was pressed, from 0; the presses must lead to it
 * @param presses every press of the round, in order; the service takes 1 to 24
 * @param doneMs  when Done was pressed, in whole milliseconds since the round was shown, after the
 *                last press
 */
public record RotateRound(int frame, List<RotatePress> presses, int doneMs) {

    /**
     * Copies the presses, so the round cannot change under its holder.
     *
     * @param frame   the frame on screen when Done was pressed
     * @param presses every press of the round, in order
     * @param doneMs  when Done was pressed, in whole milliseconds since the round was shown
     */
    public RotateRound {
        presses = List.copyOf(Objects.requireNonNull(presses, "presses"));
    }
}
