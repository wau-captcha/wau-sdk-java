package com.waucaptcha;

/**
 * What the behaviour check concluded about the answer behind a token. Answers judged automated
 * never get a token, so they have no verdict here.
 */
public enum BehaviorVerdict {
    /** It behaved like a person. */
    HUMAN("human"),
    /**
     * The answer was right but the session looked scripted, or came without the proof of work:
     * decide per form, and refuse it for sign-up and login.
     */
    SUSPICIOUS("suspicious"),
    /**
     * The client sent no behaviour data (a browser SDK older than 1.2.0, or a hand-written
     * integration), so only the answer was checked: treat it as untrusted and refuse it.
     */
    NOT_MEASURED("not_measured");

    private final String wireName;

    BehaviorVerdict(String wireName) {
        this.wireName = wireName;
    }

    /**
     * The name the API uses.
     *
     * @return e.g. {@code human}
     */
    public String wireName() {
        return wireName;
    }

    /**
     * The verdict an API name stands for. A service without the behaviour check, or a verdict
     * newer than this SDK, reads as not measured.
     */
    static BehaviorVerdict fromWireName(Object wireName) {
        for (BehaviorVerdict verdict : values()) {
            if (verdict.wireName.equals(wireName)) {
                return verdict;
            }
        }
        return NOT_MEASURED;
    }
}
