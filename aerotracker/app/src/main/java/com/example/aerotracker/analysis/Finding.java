package com.example.aerotracker.analysis;

import java.util.HashMap;
import java.util.Map;

/**
 * A single issue detected by {@link LaunchAnalyzer}, with a short title and a
 * plain-language explanation of the likely cause.
 */
public class Finding {

    public static final String SEVERITY_MINOR = "minor";
    public static final String SEVERITY_MAJOR = "major";

    private final String title;
    private final String explanation;
    private final String severity;

    public Finding(String title, String explanation, String severity) {
        this.title = title;
        this.explanation = explanation;
        this.severity = severity;
    }

    public String getTitle() { return title; }
    public String getExplanation() { return explanation; }
    public String getSeverity() { return severity; }

    public boolean isMajor() { return SEVERITY_MAJOR.equals(severity); }

    /** Serializes this finding for storage inside a Firestore document. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("title", title);
        map.put("explanation", explanation);
        map.put("severity", severity);
        return map;
    }

    /** Restores a finding previously written with {@link #toMap()}. */
    public static Finding fromMap(Map<String, Object> map) {
        String title = map.get("title") instanceof String ? (String) map.get("title") : "";
        String explanation = map.get("explanation") instanceof String ? (String) map.get("explanation") : "";
        String severity = map.get("severity") instanceof String ? (String) map.get("severity") : SEVERITY_MINOR;
        return new Finding(title, explanation, severity);
    }
}
