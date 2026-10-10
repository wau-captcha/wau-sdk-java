package com.waucaptcha;

import java.util.List;

/**
 * A slide_puzzle drag, in CSS pixels of the image: where the left edge of the piece ended, and the
 * points of the drag. The service reads at most 400 points and checks that they moved like a hand.
 *
 * @param x     where the left edge of the piece ended
 * @param trail the points of the drag, from the pointerdown, which is {@code (0, 0, 0)}, to the release
 */
public record SlideDrag(double x, List<Point> trail) {

    /**
     * Checks the numbers and copies the trail.
     *
     * @param x     where the left edge of the piece ended
     * @param trail the points of the drag
     */
    public SlideDrag {
        Json.requireFinite(x, "x");
        trail = List.copyOf(trail);
    }

    /**
     * One point of a drag.
     *
     * @param t milliseconds since the pointerdown
     * @param x the left edge of the piece there
     * @param y how far the pointer was, vertically, from where it went down
     */
    public record Point(double t, double x, double y) {

        /**
         * Checks that every number is finite.
         *
         * @param t milliseconds since the pointerdown
         * @param x the left edge of the piece there
         * @param y how far the pointer was, vertically, from where it went down
         */
        public Point {
            Json.requireFinite(t, "t");
            Json.requireFinite(x, "x");
            Json.requireFinite(y, "y");
        }
    }
}
