package com.example.indoorlocation.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.telephony.CellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.example.indoorlocation.R;
import com.example.indoorlocation.cache.CellInfoCache;

import java.util.List;
import java.util.concurrent.Executors;

public class CellMonitorService extends Service {

    private TelephonyManager telephonyManager;
    private PhoneStateListener phoneStateListener;

    @Override
    public void onCreate() {
        super.onCreate();
        telephonyManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
        startForeground(1, createNotification());
        startListen();
    }

    private void startListen() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        phoneStateListener = new PhoneStateListener() {

            @Override
            public void onCellInfoChanged(List<CellInfo> cellInfo) {
                CellInfoCache.update(cellInfo);
            }

            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                requestOnce();
            }
        };

        telephonyManager.listen(
                phoneStateListener,
                PhoneStateListener.LISTEN_CELL_INFO |
                        PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
        );
    }

    private void requestOnce() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            telephonyManager.requestCellInfoUpdate(
                    Executors.newSingleThreadExecutor(),
                    new TelephonyManager.CellInfoCallback() {
                        @Override
                        public void onCellInfo(List<CellInfo> cellInfo) {
                            CellInfoCache.update(cellInfo);
                        }
                    });
        }
    }

    private Notification createNotification() {
        String channelId = "cell_monitor";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Cell Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );

            NotificationManager manager =
                    (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle("Cell 信号监听中")
                .setContentText("基站信号实时采集中")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .build();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
