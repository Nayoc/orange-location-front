package com.example.indoorlocation;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.indoorlocation.constant.SingleSourceTypeEnum;
import com.example.indoorlocation.model.ApDto;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SignalListActivity extends AppCompatActivity {
    private static final int REQUEST_PERMISSION_CODE = 100;
    private TextView signalText;
    private ProgressBar loadingPb;
    private Button refreshBtn;

    private long lastWifiTimestamp = -1;
    private long lastCellTimestamp = -1;
    // 所需权限（信号采集依赖定位+WiFi权限）
    private String[] requiredPermissions = new String[]{
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_signal_list);

        // 初始化控件
        initView();

        // 检查权限，有权限则加载信号，无则申请
        if (checkAllPermissionsGranted()) {
            loadAndShowSignals();
        } else {
            ActivityCompat.requestPermissions(this, requiredPermissions, REQUEST_PERMISSION_CODE);
        }

        // 刷新按钮点击事件
        refreshBtn.setOnClickListener(v -> loadAndShowSignals());
    }

    /**
     * 初始化控件
     */
    private void initView() {
        signalText = findViewById(R.id.signal_text);
        loadingPb = findViewById(R.id.loading_pb);
        refreshBtn = findViewById(R.id.refresh_btn);
    }

    /**
     * 加载并展示信号（子线程执行，避免阻塞UI）
     */
    private void loadAndShowSignals() {
        loadingPb.setVisibility(View.VISIBLE);
        signalText.setText("正在扫描信号...");

        new Thread(() -> {
            // 调用你的现成方法，获取基站和WiFi列表
            List<ApDto> cellList = getCellList(); // 基站列表（先展示）
            List<ApDto> wifiList = getWifiList(); // WiFi列表（后展示）

            // 拼接信号文本
            StringBuilder signalSb = new StringBuilder();
            // 1. 拼接基站信号
            signalSb.append("【基站信号】\n");
            if (cellList == null || cellList.isEmpty()) {
                signalSb.append("未扫描到任何基站信号\n");
            } else {
                for (int i = 0; i < cellList.size(); i++) {
                    ApDto cell = cellList.get(i);
                    signalSb.append(String.format(
                            "基站%d：ID=%s | 类型=%s |名称=%s | RSRP=%d dBm | RSRQ=%d dB | SINR=%d dB\n",
                            i + 1,
                            cell.getApId() == null ? "未知" : cell.getApId(),
                            cell.getCellType() == null ? "未知" : cell.getCellType(),
                            cell.getApName() == null ? "未知" : cell.getApName(),
                            cell.getRsrp() == null ? -999 : cell.getRsrp(),
                            cell.getRsrq() == null ? -999 : cell.getRsrq(),
                            cell.getSinr() == null ? -999 : cell.getSinr()
                    ));
                }
            }

            // 2. 拼接WiFi信号（换行分隔）
            signalSb.append("\n【WiFi信号】\n");
            if (wifiList == null || wifiList.isEmpty()) {
                signalSb.append("未扫描到任何WiFi信号\n");
            } else {
                for (int i = 0; i < wifiList.size(); i++) {
                    ApDto wifi = wifiList.get(i);
                    signalSb.append(String.format(
                            "WiFi%d：ID=%s | 名称=%s | RSSI=%d dBm\n",
                            i + 1,
                            wifi.getApId() == null ? "未知" : wifi.getApId(), // WiFi的apId应为BSSID
                            wifi.getApName() == null ? "未知" : wifi.getApName(), // WiFi的apName应为SSID
                            wifi.getRssi() == null ? -999 : wifi.getRssi()
                    ));
                }
            }

            // 主线程更新文本
            runOnUiThread(() -> {
                loadingPb.setVisibility(View.GONE);
                signalText.setText(signalSb.toString());
            });
        }).start();
    }

    /**
     * 检查所有权限是否已授予
     */
    private boolean checkAllPermissionsGranted() {
        for (String permission : requiredPermissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * 权限申请结果回调
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_CODE) {
            if (checkAllPermissionsGranted()) {
                loadAndShowSignals();
            } else {
                signalText.setText("未授予必要权限，无法扫描信号");
            }
        }
    }


    private List<ApDto> getWifiList() {
        // ========== Wi-Fi ==========
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            wifiManager.startScan();
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                List<ApDto> results = wifiManager.getScanResults()
                        .stream()
                        .map(scanResult -> {

                            ApDto ap = new ApDto();

                            ap.setApId(scanResult.BSSID);
                            ap.setApName(scanResult.SSID);
                            ap.setRssi(scanResult.level);
                            ap.setSource(SingleSourceTypeEnum.WIFI.getValue());

                            return ap;
                        })
                        // 过滤保留信号最好的前20个
                        .sorted(Comparator.comparing(ApDto::getRssi).reversed())
                        .limit(20)
                        .collect(Collectors.toList());


                return results;
            }

        }
        return Collections.emptyList();
    }

    // 获取5G RSRP值（需要实现实际逻辑）
    private List<ApDto> getCellList() {
        TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        if (telephonyManager != null) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                List<CellInfo> cellInfos = telephonyManager.getAllCellInfo();
                if (cellInfos != null && !cellInfos.isEmpty()) {
                    List<ApDto> results = cellInfos.stream()
                            .map(cellInfo -> {
                                ApDto apDto = new ApDto();
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    if (cellInfo instanceof CellInfoLte) {
                                        CellInfoLte lte = (CellInfoLte) cellInfo;
                                        CellSignalStrengthLte signal = (CellSignalStrengthLte) lte.getCellSignalStrength();
                                        int rsrp = signal.getRsrp();
                                        int rsrq = lte.getCellSignalStrength().getRsrq();
                                        int sinr = signal.getRssnr();

                                        // 使用物理小区id，也可以用全局id
                                        apDto.setApId(String.valueOf(lte.getCellIdentity().getCi()));
                                        apDto.setRsrp(rsrp);
                                        apDto.setRsrq(rsrq);
                                        apDto.setSinr(sinr > -20 && sinr < 30 ? sinr : -20);
                                        apDto.setCellType("lte");
                                    } else if (cellInfo instanceof CellInfoNr) {
                                        CellInfoNr nr = (CellInfoNr) cellInfo;
                                        CellSignalStrengthNr signal = (CellSignalStrengthNr) nr.getCellSignalStrength();
                                        int rsrp = signal.getSsRsrp();
                                        int rsrq = signal.getSsRsrq();
                                        int sinr = signal.getSsSinr();
                                        apDto.setApId(String.valueOf(((CellIdentityNr) nr.getCellIdentity()).getNci()));
                                        apDto.setRsrp(rsrp);
                                        apDto.setRsrq(rsrq);
                                        apDto.setSinr(sinr);
                                        apDto.setCellType("nr");
                                    }
                                    apDto.setSource(SingleSourceTypeEnum.CELL.getValue());
                                }
                                return apDto;
                            })
                            .filter(apDto -> apDto.getRsrp() != null)
                            .collect(Collectors.toList());

                    Set<String> seenFields = new LinkedHashSet<>();
                    return results.stream()
                            .filter(item -> seenFields.add(item.getApId()))
                            .collect(Collectors.toList());
                }
            }
        }
        return Collections.emptyList();
    }
}