package com.example.indoorlocation.model;

import lombok.Data;

@Data
public class WifiDto {
    private String bssid;
    private String ssid;
    private Integer rssi;
}
