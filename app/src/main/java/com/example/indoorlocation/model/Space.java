package com.example.indoorlocation.model;

import com.google.gson.annotations.SerializedName;

import java.io.File;

public class Space {
    @SerializedName("id")
    private String id;

    @SerializedName("spaceName")
    private String spaceName;

    @SerializedName("isAccess")
    private Boolean isAccess;

    @SerializedName("spacePlan")
    private File spacePlan;

    @SerializedName("longitude")
    private String longitude;

    @SerializedName("latitude")
    private String latitude;

    public Space(String spaceName) {
        this.spaceName = spaceName;
    }

    public File getSpacePlan() {
        return spacePlan;
    }

    public void setSpacePlan(File spacePlan) {
        this.spacePlan = spacePlan;
    }

    public Space() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSpaceName() {
        return spaceName;
    }

    public void setSpaceName(String spaceName) {
        this.spaceName = spaceName;
    }

    public Boolean getAccess() {
        return isAccess;
    }

    public void setAccess(Boolean access) {
        isAccess = access;
    }

    public String getLongitude() {
        return longitude;
    }

    public void setLongitude(String longitude) {
        this.longitude = longitude;
    }

    public String getLatitude() {
        return latitude;
    }

    public void setLatitude(String latitude) {
        this.latitude = latitude;
    }
}
