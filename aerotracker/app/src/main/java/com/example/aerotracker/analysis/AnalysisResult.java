package com.example.aerotracker.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured result returned by {@link LaunchAnalyzer}: an overall verdict
 * plus the list of specific findings that produced it.
 */
public class AnalysisResult {

    public static final String VERDICT_NOMINAL = "Nominal";
    public static final String VERDICT_MINOR_ISSUES = "Minor issues";
    public static final String VERDICT_NEEDS_REVIEW = "Needs review";

    private final String verdict;
    private final List<Finding> findings;

    public AnalysisResult(String verdict, List<Finding> findings) {
        this.verdict = verdict;
        this.findings = findings;
    }

    public String getVerdict() { return verdict; }
    public List<Finding> getFindings() { return findings; }

    public boolean isNominal() { return VERDICT_NOMINAL.equals(verdict); }

    /** Serializes the result for storage alongside the launch document. */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("verdict", verdict);
        List<Map<String, Object>> findingMaps = new ArrayList<>();
        for (Finding finding : findings) {
            findingMaps.add(finding.toMap());
        }
        map.put("findings", findingMaps);
        return map;
    }

    /** Restores a result previously written with {@link #toMap()}, or null if the data is unusable. */
    @SuppressWarnings("unchecked")
    public static AnalysisResult fromMap(Map<String, Object> map) {
        if (map == null || !(map.get("verdict") instanceof String)) return null;
        String verdict = (String) map.get("verdict");
        List<Finding> findings = new ArrayList<>();
        Object rawFindings = map.get("findings");
        if (rawFindings instanceof List) {
            for (Object item : (List<Object>) rawFindings) {
                if (item instanceof Map) {
                    findings.add(Finding.fromMap((Map<String, Object>) item));
                }
            }
        }
        return new AnalysisResult(verdict, findings);
    }
}
