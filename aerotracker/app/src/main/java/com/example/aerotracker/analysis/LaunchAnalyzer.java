package com.example.aerotracker.analysis;

import com.example.aerotracker.model.Launch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * On-device, rule-based post-flight analysis. Takes a launch's recorded data
 * and produces a plain-language verdict on whether the launch went well.
 *
 * Pure Java with no Android or Firebase dependencies so the rules are unit
 * testable in isolation from the UI.
 */
public final class LaunchAnalyzer {

    // Launch rods are normally within a few degrees of vertical; anything past
    // these deviations causes noticeable horizontal drift.
    static final double ANGLE_MINOR_DEG = 15.0;
    static final double ANGLE_MAJOR_DEG = 30.0;

    // Fraction of the motor's typical apogee below which altitude is flagged.
    static final double ALTITUDE_MINOR_FRACTION = 0.4;
    static final double ALTITUDE_MAJOR_FRACTION = 0.2;

    private static final String[] RECOVERY_MINOR_KEYWORDS =
            {"damaged", "broken", "cracked", "torn", "tangled", "ripped", "bent", "dented"};
    private static final String[] RECOVERY_MAJOR_KEYWORDS =
            {"lost", "missing", "destroyed", "shredded"};

    private LaunchAnalyzer() {}

    /** Evaluates the recorded launch data against the rule set. */
    public static AnalysisResult analyze(Launch launch) {
        List<Finding> findings = new ArrayList<>();

        checkOutcome(launch.getOutcome(), findings);
        checkLaunchAngle(launch.getLaunchAngle(), findings);
        checkAltitude(launch.getMaxAltitude(), launch.getMotorType(), findings);
        checkFlightDuration(launch.getFlightDuration(), launch.getMaxAltitude(), findings);
        checkRecoveryCondition(launch.getRecoveryCondition(), findings);

        return new AnalysisResult(verdictFor(findings), findings);
    }

    private static String verdictFor(List<Finding> findings) {
        if (findings.isEmpty()) return AnalysisResult.VERDICT_NOMINAL;
        for (Finding finding : findings) {
            if (finding.isMajor()) return AnalysisResult.VERDICT_NEEDS_REVIEW;
        }
        return AnalysisResult.VERDICT_MINOR_ISSUES;
    }

    private static void checkOutcome(String outcome, List<Finding> findings) {
        if ("Failed".equalsIgnoreCase(outcome)) {
            findings.add(new Finding(
                    "Launch marked as failed",
                    "The flight was logged as a failure, so the data below should be reviewed for the root cause.",
                    Finding.SEVERITY_MAJOR));
        } else if ("Partial".equalsIgnoreCase(outcome)) {
            findings.add(new Finding(
                    "Partial success reported",
                    "Something did not go fully to plan during this flight — check the notes and recovery condition.",
                    Finding.SEVERITY_MINOR));
        }
    }

    private static void checkLaunchAngle(double angleDeg, List<Finding> findings) {
        if (angleDeg > ANGLE_MAJOR_DEG) {
            findings.add(new Finding(
                    "Very steep launch angle (" + formatDeg(angleDeg) + " from vertical)",
                    "An angle this far from vertical sends much of the motor's thrust sideways, causing large horizontal drift and a lower apogee.",
                    Finding.SEVERITY_MAJOR));
        } else if (angleDeg > ANGLE_MINOR_DEG) {
            findings.add(new Finding(
                    "Steep launch angle (" + formatDeg(angleDeg) + " from vertical)",
                    "Launching more than 15° off vertical may cause horizontal drift and reduce peak altitude.",
                    Finding.SEVERITY_MINOR));
        }
    }

    private static void checkAltitude(double maxAltitudeFt, String motorType, List<Finding> findings) {
        double typical = typicalAltitudeForMotor(motorType);
        if (typical <= 0 || maxAltitudeFt <= 0) return; // unknown motor class or altitude not recorded

        if (maxAltitudeFt < typical * ALTITUDE_MAJOR_FRACTION) {
            findings.add(new Finding(
                    "Altitude far below expected for a " + motorClassOf(motorType) + " motor",
                    "Reaching well under the typical range suggests an underpowered motor, excess weight, high drag, or an unstable flight path.",
                    Finding.SEVERITY_MAJOR));
        } else if (maxAltitudeFt < typical * ALTITUDE_MINOR_FRACTION) {
            findings.add(new Finding(
                    "Lower than expected altitude for a " + motorClassOf(motorType) + " motor",
                    "The rocket flew noticeably lower than typical for this motor class — check motor selection or rocket weight.",
                    Finding.SEVERITY_MINOR));
        }
    }

    private static void checkFlightDuration(String flightDuration, double maxAltitudeFt, List<Finding> findings) {
        double seconds = parseDurationSeconds(flightDuration);
        if (seconds <= 0 || maxAltitudeFt < 300) return; // no usable duration or too low to judge

        // A flight to several hundred feet should stay airborne well past a few
        // seconds once the recovery system deploys; scale the floor with altitude.
        double expectedMinimum = Math.max(8.0, maxAltitudeFt / 100.0);
        if (seconds < expectedMinimum) {
            findings.add(new Finding(
                    "Flight unusually short for the altitude reached",
                    "A flight to " + Math.round(maxAltitudeFt) + " ft lasting only " + Math.round(seconds)
                            + " s points to a possible early recovery deployment or motor failure.",
                    Finding.SEVERITY_MINOR));
        }
    }

    private static void checkRecoveryCondition(String recoveryCondition, List<Finding> findings) {
        if (recoveryCondition == null) return;
        String lower = recoveryCondition.toLowerCase(Locale.ROOT);
        for (String keyword : RECOVERY_MAJOR_KEYWORDS) {
            if (lower.contains(keyword)) {
                findings.add(new Finding(
                        "Recovery issue detected: \"" + keyword + "\"",
                        "The rocket or parts of it were not recovered intact — inspect the recovery system before the next flight.",
                        Finding.SEVERITY_MAJOR));
                return;
            }
        }
        for (String keyword : RECOVERY_MINOR_KEYWORDS) {
            if (lower.contains(keyword)) {
                findings.add(new Finding(
                        "Recovery issue detected: \"" + keyword + "\"",
                        "The recovery notes mention damage — repairs may be needed before this rocket flies again.",
                        Finding.SEVERITY_MINOR));
                return;
            }
        }
    }

    /**
     * Typical apogee in feet for a small/mid-power motor class, keyed by the
     * first impulse letter of the motor designation (e.g. "C6-5" → C).
     * Returns 0 when the class cannot be determined.
     */
    static double typicalAltitudeForMotor(String motorType) {
        char letter = motorClassOf(motorType);
        switch (letter) {
            case 'A': return 300;
            case 'B': return 650;
            case 'C': return 1200;
            case 'D': return 1800;
            case 'E': return 2500;
            case 'F': return 3200;
            case 'G': return 4000;
            case 'H': return 5500;
            default: return 0;
        }
    }

    /** First letter of the motor designation, uppercased, or '?' if absent. */
    static char motorClassOf(String motorType) {
        if (motorType == null) return '?';
        String trimmed = motorType.trim();
        if (trimmed.isEmpty()) return '?';
        char first = Character.toUpperCase(trimmed.charAt(0));
        return (first >= 'A' && first <= 'Z') ? first : '?';
    }

    /**
     * Lenient parse of a free-text flight duration ("45s", "45 sec", "1:20",
     * "1 min 20 s", "45"). Returns total seconds, or 0 if unparseable.
     */
    public static double parseDurationSeconds(String duration) {
        if (duration == null) return 0;
        String text = duration.trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) return 0;

        if (text.contains(":")) {
            String[] parts = text.split(":");
            try {
                double minutes = Double.parseDouble(parts[0].replaceAll("[^0-9.]", ""));
                double seconds = parts.length > 1
                        ? Double.parseDouble(parts[1].replaceAll("[^0-9.]", "")) : 0;
                return minutes * 60 + seconds;
            } catch (NumberFormatException e) {
                return 0;
            }
        }

        // "1 min 20 s" style: minutes value followed by a seconds value
        java.util.regex.Matcher minuteMatcher =
                java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*m").matcher(text);
        if (minuteMatcher.find()) {
            double total = Double.parseDouble(minuteMatcher.group(1)) * 60;
            java.util.regex.Matcher secondMatcher = java.util.regex.Pattern
                    .compile("([0-9]+(?:\\.[0-9]+)?)\\s*s").matcher(text.substring(minuteMatcher.end()));
            if (secondMatcher.find()) total += Double.parseDouble(secondMatcher.group(1));
            return total;
        }

        java.util.regex.Matcher numberMatcher =
                java.util.regex.Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        if (numberMatcher.find()) return Double.parseDouble(numberMatcher.group(1));
        return 0;
    }

    private static String formatDeg(double angle) {
        return String.format(Locale.US, "%.0f°", angle);
    }
}
