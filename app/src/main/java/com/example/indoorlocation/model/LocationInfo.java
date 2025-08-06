package com.example.indoorlocation.model;

import lombok.Data;

/**
 * 定位信息模型类，用于封装经纬度结果
 */
@Data
public class LocationInfo {
    private double latitude;   // 纬度
    private double longitude;  // 经度
    private boolean success;   // 是否获取成功
    private String errorMsg;   // 错误信息（失败时不为空）

    // 成功时的构造方法
    public LocationInfo(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.success = true;
        this.errorMsg = "";
    }

    // 失败时的构造方法
    public LocationInfo(String errorMsg) {
        this.latitude = 0.0;
        this.longitude = 0.0;
        this.success = false;
        this.errorMsg = errorMsg;
    }
}
