package com.example.indoorlocation.model;

import com.google.gson.annotations.SerializedName;

import java.io.File;

import lombok.Data;

@Data
public class Space {
    @SerializedName("id")
    private String id;

    @SerializedName("spaceName")
    private String spaceName;

    @SerializedName("isAccess")
    private Boolean isAccess;

    @SerializedName("spacePlan")
    private File spacePlan;

    @SerializedName("spacePlanUrl")
    private String spacePlanUrl;

    @SerializedName("longitude")
    private String longitude;

    @SerializedName("latitude")
    private String latitude;

    @SerializedName("scaleX")
    private double scaleX;

    @SerializedName("scaleY")
    private double scaleY;

    @SerializedName("scaleRate")
    private double scaleRate;

    public Space(String spaceName) {
        this.spaceName = spaceName;
    }

    public Space() {
    }
}
