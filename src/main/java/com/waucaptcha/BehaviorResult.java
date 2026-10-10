package com.waucaptcha;

import java.util.List;
import java.util.Objects;

/**
 * How the person who solved the challenge behaved, as the service judged it.
 *
 * @param verdict what the check concluded
 * @param score   from 0 (scripted) to 1 (human-like); {@code null} when not measured
 * @param reasons short codes of what lowered the score, e.g. {@code webdriver}
 */
public record BehaviorResult(BehaviorVerdict verdict, Double score, List<String> reasons) {

    /**
     * Copies the reasons; {@code null} reads as none.
     *
     * @param verdict what the check concluded
     * @param score   from 0 (scripted) to 1 (human-like); {@code null} when not measured
     * @param reasons short codes of what lowered the score, e.g. {@code webdriver}
     */
    public BehaviorResult {
        Objects.requireNonNull(verdict, "verdict");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    static BehaviorResult notMeasured() {
        return new BehaviorResult(BehaviorVerdict.NOT_MEASURED, null, List.of());
    }
}
