package com.example.aerotracker.model;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentId;

public class Launch {
    @DocumentId
    private String id;
    private String rocketName;
    private String motorType;
    private Timestamp timestamp;
    private double latitude;
    private double longitude;
    private double elevation;
    private String locationString;
    private double launchAngle;
    private String photoUrl;
    private String weatherNotes;
    private double maxAltitude;
    private String flightDuration;
    private String recoveryCondition;
    private String notes;
    private String outcome;
    // Stored result of LaunchAnalyzer so the verdict doesn't need recomputing
    // on every read; the full finding list lives in the "analysis" map field.
    private String analysisVerdict;

    public Launch() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRocketName() { return rocketName; }
    public void setRocketName(String rocketName) { this.rocketName = rocketName; }

    public String getMotorType() { return motorType; }
    public void setMotorType(String motorType) { this.motorType = motorType; }

    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    public double getElevation() { return elevation; }
    public void setElevation(double elevation) { this.elevation = elevation; }

    public String getLocationString() { return locationString; }
    public void setLocationString(String locationString) { this.locationString = locationString; }

    public double getLaunchAngle() { return launchAngle; }
    public void setLaunchAngle(double launchAngle) { this.launchAngle = launchAngle; }

    public String getPhotoUrl() { return photoUrl; }
    public void setPhotoUrl(String photoUrl) { this.photoUrl = photoUrl; }

    public String getWeatherNotes() { return weatherNotes; }
    public void setWeatherNotes(String weatherNotes) { this.weatherNotes = weatherNotes; }

    public double getMaxAltitude() { return maxAltitude; }
    public void setMaxAltitude(double maxAltitude) { this.maxAltitude = maxAltitude; }

    public String getFlightDuration() { return flightDuration; }
    public void setFlightDuration(String flightDuration) { this.flightDuration = flightDuration; }

    public String getRecoveryCondition() { return recoveryCondition; }
    public void setRecoveryCondition(String recoveryCondition) { this.recoveryCondition = recoveryCondition; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }

    public String getAnalysisVerdict() { return analysisVerdict; }
    public void setAnalysisVerdict(String analysisVerdict) { this.analysisVerdict = analysisVerdict; }
}
