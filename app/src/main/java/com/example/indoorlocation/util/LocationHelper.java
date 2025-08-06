package com.example.indoorlocation.util;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.indoorlocation.model.LocationInfo;

public class LocationHelper {
    private static final String TAG = "LocationHelper";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final long MIN_TIME_BETWEEN_UPDATES = 1000 * 10; // 10秒
    private static final float MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10米

    private final Context context;
    private final LocationManager locationManager;
    private LocationListener locationListener;

    public LocationHelper(Context context) {
        this.context = context;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    public boolean checkLocationPermission(Activity activity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, LOCATION_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    public void getCurrentLocation(LocationCallback callback) {
        if (!checkLocationPermission((Activity) context)) {
            callback.onLocationReceived(new LocationInfo("需要定位权限"));
            return;
        }

        try {
            // 先尝试获取最后已知位置
            Location lastKnownLocation = getLastKnownLocation();
            if (lastKnownLocation != null) {
                callback.onLocationReceived(new LocationInfo(lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude()));
                return;
            }

            // 如果没有最后已知位置，请求新位置
            requestNewLocation(callback);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception: " + e.getMessage());
            callback.onLocationReceived(new LocationInfo("定位权限被拒绝"));
        }
    }

    private Location getLastKnownLocation() {
        try {
            Location gpsLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location networkLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            if (gpsLocation != null && networkLocation != null) {
                return gpsLocation.getTime() > networkLocation.getTime() ? gpsLocation : networkLocation;
            } else if (gpsLocation != null) {
                return gpsLocation;
            } else {
                return networkLocation;
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Error getting last known location: " + e.getMessage());
            return null;
        }
    }

    private void requestNewLocation(final LocationCallback callback) {
        try {
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (location != null) {
                        callback.onLocationReceived(new LocationInfo(location.getLatitude(), location.getLongitude()));
                        stopLocationUpdates();
                    }
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {}

                @Override
                public void onProviderEnabled(String provider) {}

                @Override
                public void onProviderDisabled(String provider) {
                    callback.onLocationReceived(new LocationInfo("请开启定位服务"));
                }
            };

            // 优先使用GPS，其次使用网络定位
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES,
                        locationListener
                );
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BETWEEN_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES,
                        locationListener
                );
            } else {
                callback.onLocationReceived(new LocationInfo("无法获取位置信息，请检查定位服务"));
            }

            // 设置超时处理
            new android.os.Handler().postDelayed(() -> {
                if (locationListener != null) {
                    stopLocationUpdates();
                    Location lastLocation = getLastKnownLocation();
                    if (lastLocation != null) {
                        callback.onLocationReceived(new LocationInfo(lastLocation.getLatitude(), lastLocation.getLongitude()));
                    } else {
                        callback.onLocationReceived(new LocationInfo("定位超时，请重试"));
                    }
                }
            }, 10000); // 10秒超时

        } catch (SecurityException e) {
            Log.e(TAG, "Error requesting location: " + e.getMessage());
            callback.onLocationReceived(new LocationInfo("定位权限被拒绝"));
        }
    }

    public void stopLocationUpdates() {
        if (locationListener != null && locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
                locationListener = null;
            } catch (SecurityException e) {
                Log.e(TAG, "Error stopping location updates: " + e.getMessage());
            }
        }
    }

    public interface LocationCallback {
        void onLocationReceived(LocationInfo locationInfo);
    }

    public static boolean onRequestPermissionsResult(int requestCode, int[] grantResults) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }
        return false;
    }
}