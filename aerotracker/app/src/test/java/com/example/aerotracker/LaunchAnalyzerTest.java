package com.example.aerotracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.example.aerotracker.analysis.AnalysisResult;
import com.example.aerotracker.analysis.Finding;
import com.example.aerotracker.analysis.LaunchAnalyzer;
import com.example.aerotracker.model.Launch;

import org.junit.Test;

public class LaunchAnalyzerTest {

    private Launch goodLaunch() {
        Launch launch = new Launch();
        launch.setRocketName("Alpha III");
        launch.setMotorType("C6-5");
        launch.setLaunchAngle(4.0);
        launch.setMaxAltitude(1100);
        launch.setFlightDuration("45s");
        launch.setRecoveryCondition("Perfect, no damage");
        launch.setOutcome("Success");
        return launch;
    }

    @Test
    public void nominalLaunchHasNoFindings() {
        AnalysisResult result = LaunchAnalyzer.analyze(goodLaunch());
        assertEquals(AnalysisResult.VERDICT_NOMINAL, result.getVerdict());
        assertTrue(result.isNominal());
        assertTrue(result.getFindings().isEmpty());
    }

    @Test
    public void steepAngleFlagsMinorIssue() {
        Launch launch = goodLaunch();
        launch.setLaunchAngle(20.0);
        AnalysisResult result = LaunchAnalyzer.analyze(launch);
        assertEquals(AnalysisResult.VERDICT_MINOR_ISSUES, result.getVerdict());
        assertEquals(1, result.getFindings().size());
        assertFalse(result.getFindings().get(0).isMajor());
    }

    @Test
    public void verySteepAngleNeedsReview() {
        Launch launch = goodLaunch();
        launch.setLaunchAngle(40.0);
        AnalysisResult result = LaunchAnalyzer.analyze(launch);
        assertEquals(AnalysisResult.VERDICT_NEEDS_REVIEW, result.getVerdict());
    }

    @Test
    public void lowAltitudeForMotorClassIsFlagged() {
        Launch launch = goodLaunch();
        launch.setMotorType("C6-5"); // typical ~1200 ft
        launch.setMaxAltitude(400);  // under 40% of typical
        launch.setFlightDuration("20s");
        AnalysisResult result = LaunchAnalyzer.analyze(launch);
        assertEquals(AnalysisResult.VERDICT_MINOR_ISSUES, result.getVerdict());
    }

    @Test
    public void veryLowAltitudeNeedsReview() {
        Launch launch = goodLaunch();
        launch.setMaxAltitude(150); // under 20% of typical for a C motor
        launch.setFlightDuration("");
        AnalysisResult result = LaunchAnalyzer.analyze(launch);
        assertEquals(AnalysisResult.VERDICT_NEEDS_REVIEW, result.getVerdict());
    }

    @Test
    public void unknownMotorClassSkipsAltitudeRule() {
        Launch launch = goodLaunch();
        launch.setMotorType("");
        launch.setMaxAltitude(50);
        launch.setFlightDuration("");
        AnalysisResult result = LaunchAnalyzer.analyze(launch);
        assertTrue(result.isNominal());
    }

    @Test
    public void shortFlightForAltitudeSuggestsEarlyDeployment() {
        Launch launch = goodLaunch();
        launch.setMaxAltitude(1100);
        launch.setFlightDuration("5s");
        AnalysisResult result = LaunchAnalyzer.analyze(launch);
        assertEquals(AnalysisResult.VERDICT_MINOR_ISSUES, result.getVerdict());
        assertTrue(result.getFindings().get(0).getTitle().contains("short"));
    }

    @Test
    public void damagedRecoveryIsMinorLostIsMajor() {
        Launch damaged = goodLaunch();
        damaged.setRecoveryCondition("Fin damaged on landing");
        assertEquals(AnalysisResult.VERDICT_MINOR_ISSUES, LaunchAnalyzer.analyze(damaged).getVerdict());

        Launch lost = goodLaunch();
        lost.setRecoveryCondition("Rocket lost in trees");
        assertEquals(AnalysisResult.VERDICT_NEEDS_REVIEW, LaunchAnalyzer.analyze(lost).getVerdict());
    }

    @Test
    public void failedOutcomeAlwaysNeedsReview() {
        Launch launch = goodLaunch();
        launch.setOutcome("Failed");
        AnalysisResult result = LaunchAnalyzer.analyze(launch);
        assertEquals(AnalysisResult.VERDICT_NEEDS_REVIEW, result.getVerdict());
        assertFalse(result.getFindings().isEmpty());
    }

    @Test
    public void partialOutcomeIsAtLeastMinorIssues() {
        Launch launch = goodLaunch();
        launch.setOutcome("Partial");
        assertEquals(AnalysisResult.VERDICT_MINOR_ISSUES, LaunchAnalyzer.analyze(launch).getVerdict());
    }

    @Test
    public void durationParsingHandlesCommonFormats() {
        assertEquals(45.0, LaunchAnalyzer.parseDurationSeconds("45s"), 0.01);
        assertEquals(45.0, LaunchAnalyzer.parseDurationSeconds("45"), 0.01);
        assertEquals(80.0, LaunchAnalyzer.parseDurationSeconds("1:20"), 0.01);
        assertEquals(80.0, LaunchAnalyzer.parseDurationSeconds("1 min 20 s"), 0.01);
        assertEquals(60.0, LaunchAnalyzer.parseDurationSeconds("1 min"), 0.01);
        assertEquals(0.0, LaunchAnalyzer.parseDurationSeconds("unknown"), 0.01);
        assertEquals(0.0, LaunchAnalyzer.parseDurationSeconds(null), 0.01);
    }

    @Test
    public void resultRoundTripsThroughMapSerialization() {
        Launch launch = goodLaunch();
        launch.setOutcome("Failed");
        launch.setRecoveryCondition("Parachute torn");
        AnalysisResult original = LaunchAnalyzer.analyze(launch);

        AnalysisResult restored = AnalysisResult.fromMap(original.toMap());
        assertEquals(original.getVerdict(), restored.getVerdict());
        assertEquals(original.getFindings().size(), restored.getFindings().size());
        for (int i = 0; i < original.getFindings().size(); i++) {
            Finding a = original.getFindings().get(i);
            Finding b = restored.getFindings().get(i);
            assertEquals(a.getTitle(), b.getTitle());
            assertEquals(a.getExplanation(), b.getExplanation());
            assertEquals(a.getSeverity(), b.getSeverity());
        }
    }
}
