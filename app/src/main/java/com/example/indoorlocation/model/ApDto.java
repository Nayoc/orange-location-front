package com.example.indoorlocation.model;

import lombok.Data;

@Data
public class ApDto {
    private String apId;
    private String apName;
    private Integer rssi;
    private Integer rsrp;
    private String source;
}
