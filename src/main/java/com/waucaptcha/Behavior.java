package com.waucaptcha;

import java.util.List;

/**
 * How the person behaved while a challenge was on screen, for callers that record it themselves:
 * the fields of the Behavior that the browser SDK records and the service reads. Times are
 * milliseconds since the challenge was shown; x and y are CSS pixels from the top-left corner of
 * the widget. Key values and page content never belong in it.
 *
 * @param version                   the layout of this record, {@link #VERSION}
 * @param elapsedMs                 how long the challenge was on screen before the answer was sent
 * @param pointer                   where the pointer moved and went down: at most one move every
 *                                  16 ms, the latest 200 samples
 * @param pointerType               {@code mouse}, {@code touch}, {@code pen}, or empty when no
 *                                  pointer was used
 * @param keyIntervalsMs            milliseconds between key presses in the answer box, the latest
 *                                  64; never the keys
 * @param inputEvents               input events in the answer box, from typing, dictation or
 *                                  autocorrect
 * @param pasteEvents               pastes into the answer box
 * @param untrustedEvents           events on the widget that a script dispatched
 * @param webdriver                 the client says it is under automation
 * @param hiddenMs                  how long the window was hidden while the challenge was on screen
 * @param headlessUa                the user agent names a headless browser
 * @param noLanguages               the client names no language
 * @param chromeWithoutWindowChrome the user agent says Chrome but window.chrome is missing
 */
public record Behavior(int version, int elapsedMs, List<PointerSample> pointer, String pointerType, List<Integer> keyIntervalsMs,
        int inputEvents, int pasteEvents, int untrustedEvents, boolean webdriver, int hiddenMs,
        boolean headlessUa, boolean noLanguages, boolean chromeWithoutWindowChrome) {

    /** The layout of Behavior the service reads. */
    public static final int VERSION = 1;

    /**
     * Copies the lists; {@code null} reads as empty.
     *
     * @param version                   the layout of this record, {@link #VERSION}
     * @param elapsedMs                 how long the challenge was on screen before the answer was sent
     * @param pointer                   where the pointer moved and went down
     * @param pointerType               {@code mouse}, {@code touch}, {@code pen}, or empty
     * @param keyIntervalsMs            milliseconds between key presses in the answer box
     * @param inputEvents               input events in the answer box
     * @param pasteEvents               pastes into the answer box
     * @param untrustedEvents           events on the widget that a script dispatched
     * @param webdriver                 the client says it is under automation
     * @param hiddenMs                  how long the window was hidden
     * @param headlessUa                the user agent names a headless browser
     * @param noLanguages               the client names no language
     * @param chromeWithoutWindowChrome the user agent says Chrome but window.chrome is missing
     */
    public Behavior {
        pointer = pointer == null ? List.of() : List.copyOf(pointer);
        pointerType = pointerType == null ? "" : pointerType;
        keyIntervalsMs = keyIntervalsMs == null ? List.of() : List.copyOf(keyIntervalsMs);
    }

    /**
     * One place the pointer moved to or went down at.
     *
     * @param t milliseconds since the challenge was shown
     * @param x CSS pixels from the left edge of the widget
     * @param y CSS pixels from the top edge of the widget
     */
    public record PointerSample(int t, double x, double y) {

        /**
         * Checks that the position is a finite number.
         *
         * @param t milliseconds since the challenge was shown
         * @param x CSS pixels from the left edge of the widget
         * @param y CSS pixels from the top edge of the widget
         */
        public PointerSample {
            Json.requireFinite(x, "x");
            Json.requireFinite(y, "y");
        }
    }

    /** The JSON the service reads, with the field names of its API. */
    String toJson() {
        StringBuilder json = new StringBuilder("{\"version\":").append(version)
                .append(",\"elapsed_ms\":").append(elapsedMs)
                .append(",\"pointer\":[");
        for (int i = 0; i < pointer.size(); i++) {
            PointerSample sample = pointer.get(i);
            json.append(i == 0 ? "" : ",")
                    .append("{\"t\":").append(sample.t())
                    .append(",\"x\":").append(Json.number(sample.x()))
                    .append(",\"y\":").append(Json.number(sample.y())).append('}');
        }
        json.append("],\"pointer_type\":").append(Json.string(pointerType)).append(",\"key_intervals_ms\":[");
        for (int i = 0; i < keyIntervalsMs.size(); i++) {
            json.append(i == 0 ? "" : ",").append(keyIntervalsMs.get(i));
        }
        return json.append("],\"input_events\":").append(inputEvents)
                .append(",\"paste_events\":").append(pasteEvents)
                .append(",\"untrusted_events\":").append(untrustedEvents)
                .append(",\"webdriver\":").append(webdriver)
                .append(",\"hidden_ms\":").append(hiddenMs)
                .append(",\"headless_ua\":").append(headlessUa)
                .append(",\"no_languages\":").append(noLanguages)
                .append(",\"chrome_without_window_chrome\":").append(chromeWithoutWindowChrome)
                .append('}').toString();
    }
}
