package com.example.indoorlocation;

import static com.example.indoorlocation.SpaceManagementActivity.JSON;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PointF;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.telephony.CellIdentityNr;
import android.telephony.CellInfo;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoNr;
import android.telephony.CellSignalStrengthLte;
import android.telephony.CellSignalStrengthNr;
import android.telephony.TelephonyManager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.indoorlocation.api.CommonCallback;
import com.example.indoorlocation.api.req.CollectionReq;
import com.example.indoorlocation.api.req.LocationReq;
import com.example.indoorlocation.api.req.NavigationReq;
import com.example.indoorlocation.api.vo.Point;
import com.example.indoorlocation.constant.HttpConstant;
import com.example.indoorlocation.constant.SingleSourceTypeEnum;
import com.example.indoorlocation.model.ApDto;
import com.example.indoorlocation.model.Space;
import com.example.indoorlocation.util.FileUtil;
import com.example.indoorlocation.view.ZoomableImageView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MapActivity extends AppCompatActivity {

    private static final String TAG = "MapActivity";

    // UI组件
    private TextView tvSpaceName;
    private ImageButton btnBack;
    private LinearLayout layoutUpload;
    private ZoomableImageView ivMap;
    private Button btnReset;
    //    private TextView btnBuildCoord;
    private Button btnUploadMap;
    private TextView tvLog;
    private ScrollView scrollViewLog;
    private EditText etX, etY;
    private LinearLayout layoutSampleCount;
    private Spinner spinnerSampleCount, spinnerMode;
    private List<PointF> rpTracePoints;
    private Button btnBuildFingerprint;
    private Button btnStart;
    private Button btnRpTraceSwitch;
    private Button btnResetData;

    private Boolean isOpenRpTrace = Boolean.TRUE;
    // 弹窗
    private AlertDialog buildCoordDialog;

    // 数据
    private String spaceId;
    private String spaceName;
    private String mapUrl = "";
    private double scaleX;
    private double scaleRate;
    private boolean hasMap = false;
    private boolean isCollecting = false;
    private boolean isPositioning = false;
    private Handler handler = new Handler();
    private Runnable positioningRunnable;
    private Runnable collectRunnable;
    private int currentCollectCount = 0; // 当前采集次数
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss", Locale.CHINA);

    // 采集次数选项
    private static final Integer MIN_RSSI = -90;
    private final String[] sampleCounts = {"1次", "10次", "100次", "500次"};
    private final int[] sampleCountValues = {1, 10, 100, 500};
    private int selectedSampleCount = 1;

    private AtomicInteger collectionFailCount = new AtomicInteger(0);

    // 模式选项
    private final String[] modes = {"采集", "定位"};
    private String currentMode = "采集";

    // 采集批次号
    private String collectionBatchId = "";
    // 导航批次号
    private String navigationBatchId = "";
    // 定位批次号
    private String locationBatchId = "";

    // 网络
    private OkHttpClient client;
    private Gson gson;

    // 图片选择器
    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    if (imageUri != null) {
                        uploadMapImage(imageUri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        client = new OkHttpClient();
        gson = new Gson();

        // 获取传递的参数
        Intent intent = getIntent();
        spaceId = intent.getStringExtra("spaceId");
        spaceName = intent.getStringExtra("spaceName");

        initViews();
        setupListeners();
        loadSpaceData(spaceId);
        loadRpTrace();
    }

    private void initViews() {
        tvSpaceName = findViewById(R.id.tv_space_name);
        btnBack = findViewById(R.id.btn_back);
        layoutUpload = findViewById(R.id.layout_upload);
        ivMap = findViewById(R.id.iv_map);
        btnReset = findViewById(R.id.btn_reset);
//        btnBuildCoord = findViewById(R.id.btn_build_coord);
        btnUploadMap = findViewById(R.id.btn_upload_map);
        tvLog = findViewById(R.id.tv_log);
        scrollViewLog = findViewById(R.id.scrollView_log);
        etX = findViewById(R.id.et_x);
        etY = findViewById(R.id.et_y);
        layoutSampleCount = findViewById(R.id.layout_sample_count);
        spinnerSampleCount = findViewById(R.id.spinner_sample_count);
        spinnerMode = findViewById(R.id.spinner_mode);
        btnBuildFingerprint = findViewById(R.id.btn_build_fingerprint);
        btnStart = findViewById(R.id.btn_start);
        btnRpTraceSwitch = findViewById(R.id.btn_rp_trace_switch);
        btnResetData = findViewById(R.id.btn_reset_data);

        // 设置空间名称
        tvSpaceName.setText(spaceName);

        // 设置采集次数选择器
        ArrayAdapter<String> sampleCountAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, sampleCounts);
        sampleCountAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSampleCount.setAdapter(sampleCountAdapter);

        // 设置模式选择器
        ArrayAdapter<String> modeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, modes);
        modeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerMode.setAdapter(modeAdapter);
    }

    private void setupListeners() {
        // 返回按钮
        btnBack.setOnClickListener(v -> finish());

        // 上传地图按钮
        btnUploadMap.setOnClickListener(v -> openImagePicker());

//        btnBuildCoord.setOnClickListener(v -> showBuildCoordDialog());

        btnReset.setOnClickListener(v -> ivMap.reset());

        // 地图点击事件
        ivMap.setOnMapClickListener((x, y) -> {
            if(!currentMode.equals("采集")){
                return;
            }

            // 保留两位小数
            x = (float) (Math.round(x * 100) / 100.0);
            y = (float) (Math.round(y * 100) / 100.0);

            // 更新输入框
            etX.setText(String.valueOf(x));
            etY.setText(String.valueOf(y));

            // 绘制标记点
            ivMap.setMarkerPoint(x, y);
        });

        // 坐标输入框文本变化监听
        TextWatcher coordinateWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String xStr = etX.getText().toString().trim();
                    String yStr = etY.getText().toString().trim();

                    if (!TextUtils.isEmpty(xStr) && !TextUtils.isEmpty(yStr)) {
                        float x = Float.parseFloat(xStr);
                        float y = Float.parseFloat(yStr);
                        ivMap.setMarkerPoint(x, y);
                    } else {
                        ivMap.clearMarker();
                    }
                } catch (NumberFormatException e) {
                    // 输入格式错误，清除标记
                    ivMap.clearMarker();
                }
            }
        };

        etX.addTextChangedListener(coordinateWatcher);
        etY.addTextChangedListener(coordinateWatcher);

        // 采集次数选择
        spinnerSampleCount.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSampleCount = sampleCountValues[position];
                appendLog("设置采样次数为: " + sampleCounts[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 模式选择
        spinnerMode.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentMode = modes[position];
                updateBatchId();
                updateUIForMode();
                appendLog("切换到" + currentMode + "模式");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 构建指纹库
        btnBuildFingerprint.setOnClickListener(v -> showBuildFingerprintDialog());

        // 开始按钮
        btnStart.setOnClickListener(v -> toggleOperation());

        btnRpTraceSwitch.setOnClickListener(v -> switchRpTrace());

        btnResetData.setOnClickListener(v -> showResetDataDialog());
    }

    private void showResetDataDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重置数据");
        builder.setCancelable(false);
        builder.setNegativeButton("取消", null);

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_reset_data, null);

        // 获取控件引用
        Button confirmButton = view.findViewById(R.id.btn_confirm);

        // 保存按钮点击事件
        confirmButton.setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    // 修改数据
                    String url = HttpConstant.SPACE_URL + "/reset" + "/" + spaceId;
                    RequestBody body = RequestBody.create(null, "");

                    Request request = new Request.Builder()
                            .url(url)
                            .post(body)
                            .build();

                    Response response = client.newCall(request).execute();

                    if (response.isSuccessful()) {
                        ivMap.clearRpTracePoints();
                        runOnUiThread(() -> {
                            buildCoordDialog.dismiss();
                            appendLog("已重置空间所有数据");
                        });

                    } else {
                        runOnUiThread(() -> {
                            buildCoordDialog.dismiss();
                            appendLog("重置空间数据失败");
                        });
                    }

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        buildCoordDialog.dismiss();
                        appendLog("重置空间数据异常: " + e.getMessage());
                    });
                }
            }).start();

        });

        buildCoordDialog = builder.setView(view)
                .setCancelable(false)
                .setNegativeButton("取消", (dialog, id) -> {
                    dialog.dismiss();
                })
                .create();

        buildCoordDialog.show();

    }

    private void updateBatchId() {
        if ("采集".equals(currentMode)) {
            collectionBatchId = String.valueOf(System.currentTimeMillis());
        } else {
            locationBatchId = String.valueOf(System.currentTimeMillis());
        }
    }

    private void updateUIForMode() {
        if ("采集".equals(currentMode)) {

            layoutSampleCount.setVisibility(View.VISIBLE);
            btnBuildFingerprint.setVisibility(View.VISIBLE);
            btnStart.setText(isCollecting ? "停止采集" : "开始采集");
            ivMap.clearMarker();
            // 设置按钮颜色
            if (isCollecting) {
                btnStart.setBackgroundTintList(getResources().getColorStateList(android.R.color.holo_red_dark));
            } else {
                btnStart.setBackgroundTintList(getResources().getColorStateList(R.color.blue));
            }
        } else {

            layoutSampleCount.setVisibility(View.GONE);
            btnBuildFingerprint.setVisibility(View.GONE);
            btnStart.setText(isPositioning ? "停止定位" : "开始定位");
            btnStart.setBackgroundTintList(getResources().getColorStateList(R.color.blue));
            ivMap.clearMarker();
            ivMap.clearRpTracePoints();
        }
    }

    private void loadSpaceData(String spaceId) {
        // 模拟检查地图状态
        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(HttpConstant.SPACE_URL + "/" + spaceId)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Type listType = new TypeToken<Space>() {
                    }.getType();
                    Space space = gson.fromJson(responseData, listType);
                    scaleX = space.getScaleX();
                    scaleRate = space.getScaleRate();

                    String mapUrl = space.getSpacePlanUrl();

                    runOnUiThread(() -> {
                        if (!mapUrl.isEmpty()) {
                            this.mapUrl = mapUrl;
                            hasMap = true;
                            showMap();

                            // 初始坐标系绘制
                            double maxYValue = scaleX / scaleRate;
                            ivMap.setCoordinateSystem((float) scaleX, (float) maxYValue);
                        } else {
                            showUploadButton();
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        showUploadButton();
                        appendLog("获取室内图失败: " + response.code());
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    showUploadButton();
                    appendLog("获取室内图错误: " + e.getMessage());
                });
            }
        }).start();
    }

    private void showMap() {
        layoutUpload.setVisibility(View.GONE);
        ivMap.setVisibility(View.VISIBLE);

        // 使用Glide加载地图图片
        Glide.with(this)
                .load(mapUrl)
                .apply(RequestOptions.placeholderOf(R.color.white).error(R.color.white))
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .into(ivMap);

        appendLog("地图加载成功");
    }

    private void showUploadButton() {
        layoutUpload.setVisibility(View.VISIBLE);
        ivMap.setVisibility(View.GONE);
        appendLog("请上传地图");
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        imagePickerLauncher.launch(intent);
    }

    private void showBuildCoordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("重建坐标系");
        builder.setCancelable(false);
        builder.setNegativeButton("取消", null);

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_build_coord, null);

        // 获取控件引用
        EditText editTextWidth = view.findViewById(R.id.et_space_width);
        TextView viewTextRatio = view.findViewById(R.id.tv_space_ratio);
        Button buildButton = view.findViewById(R.id.btn_build);

        editTextWidth.setText(String.valueOf(scaleX));
        viewTextRatio.setText(String.valueOf(scaleRate));


        // 保存按钮点击事件
        buildButton.setOnClickListener(v -> {
            Double editScaleX = Double.valueOf(editTextWidth.getText().toString().trim());

            new Thread(() -> {
                try {
                    // 修改数据
                    Space editSpace = new Space();
                    editSpace.setId(spaceId);
                    editSpace.setScaleX(editScaleX);
                    String json = gson.toJson(editSpace);
                    RequestBody body = RequestBody.create(json, JSON);

                    Request request = new Request.Builder()
                            .url(HttpConstant.SPACE_URL)
                            .put(body)
                            .build();

                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            scaleX = editScaleX;
                            // 开始绘制坐标系
                            double maxYValue = scaleX / scaleRate;
                            ivMap.setCoordinateSystem((float) scaleX, (float) maxYValue);

                            if (buildCoordDialog != null && buildCoordDialog.isShowing()) {
                                buildCoordDialog.dismiss();
                            }
                        });

                    } else {
                        runOnUiThread(() -> {
                            appendLog("重建坐标系失败: " + response.code());

                            if (buildCoordDialog != null && buildCoordDialog.isShowing()) {
                                buildCoordDialog.dismiss();
                            }
                        });
                    }

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        appendLog("重建坐标系失败: " + e.getMessage());

                        if (buildCoordDialog != null && buildCoordDialog.isShowing()) {
                            buildCoordDialog.dismiss();
                        }
                    });
                }
            }).start();
            ;

        });

        buildCoordDialog = builder.setView(view)
                .setCancelable(false)
                .setNegativeButton("取消", (dialog, id) -> {
                    dialog.dismiss();
                })
                .create();

        buildCoordDialog.show();

    }

    private void loadRpTrace() {
        new Thread(() -> {
            try {
                String url = HttpConstant.DATA_URL + "/rp" + "/" + spaceId;

                Request request = new Request.Builder()
                        .url(url)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Type listType = new TypeToken<List<PointF>>() {
                    }.getType();
                    rpTracePoints = gson.fromJson(responseData, listType);

                    runOnUiThread(() -> {
                        ivMap.setRpTracePoints(rpTracePoints);
                    });
                } else {
                    runOnUiThread(() -> {
                        appendLog("历史rp点绘制失败");
                    });
                }

            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("历史rp点绘制失败");
                });
            }
        }).start();
    }


    private void showBuildFingerprintDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("构建指纹库");
        builder.setCancelable(false);
        builder.setNegativeButton("取消", null);

        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_build_fingerprint, null);

        // 获取控件引用
        Button buildButton = view.findViewById(R.id.btn_build);

        // 保存按钮点击事件
        buildButton.setOnClickListener(v -> {
            new Thread(() -> {
                try {
                    // 修改数据
                    String url = HttpConstant.DATA_URL + "/build" + "/" + spaceId;
                    RequestBody body = RequestBody.create(null, "");

                    Request request = new Request.Builder()
                            .url(url)
                            .post(body)
                            .build();

                    Response response = client.newCall(request).execute();

                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            buildCoordDialog.dismiss();
                            appendLog("指纹库构建已开启，完成后空间状态会自动变更为可定位");
                        });

                    } else {
                        runOnUiThread(() -> {
                            buildCoordDialog.dismiss();
                            appendLog("指纹库构建开启失败");
                        });
                    }

                } catch (Exception e) {
                    runOnUiThread(() -> {
                        buildCoordDialog.dismiss();
                        appendLog("指纹库构建开启异常: " + e.getMessage());
                    });
                }
            }).start();

        });

        buildCoordDialog = builder.setView(view)
                .setCancelable(false)
                .setNegativeButton("取消", (dialog, id) -> {
                    dialog.dismiss();
                })
                .create();

        buildCoordDialog.show();

    }

    private void switchRpTrace() {
        isOpenRpTrace = !isOpenRpTrace;

        if (isOpenRpTrace) {
            ivMap.setRpTracePoints(rpTracePoints);
        } else {
            ivMap.clearRpTracePoints();
        }
    }

    private void uploadMapImage(Uri imageUri) {
        appendLog("开始上传地图...");

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .writeTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build();

                // 将Uri转换为File
                File imageFile = FileUtil.getFileFromUri(this, imageUri);
                if (imageFile == null) {
                    runOnUiThread(() -> {
                        appendLog("无法获取图片文件");
                    });
                    return;
                }

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("spaceId", spaceId)
                        .addFormDataPart("mapImage", imageFile.getName(),
                                RequestBody.create(imageFile, MediaType.parse("image/*")))
                        .build();

                Request request = new Request.Builder()
                        .url(HttpConstant.SPACE_URL + "/upload")
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    JSONObject jsonObject = new JSONObject(responseData);
                    mapUrl = jsonObject.optString("mapUrl", "");

                    runOnUiThread(() -> {
                        if (!mapUrl.isEmpty()) {
                            hasMap = true;
                            showMap();
                            appendLog("地图上传成功");
                        } else {
                            appendLog("上传成功，但未获取到地图URL");
                        }
                    });
                } else {
                    runOnUiThread(() -> {
                        appendLog("上传失败: " + response.code());
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("上传错误: " + e.getMessage());
                });
            }
        }).start();
    }

    private void toggleOperation() {
        if ("采集".equals(currentMode)) {
            toggleCollect();
        } else {
            togglePosition();
        }
    }

    private void toggleCollect() {
        if (isCollecting) {
            // 停止采集
            stopCollecting();
        } else {
            // 开始采集
            startCollecting();
        }
    }

    private void startCollecting() {
        // 检查坐标是否已输入
        String xStr = etX.getText().toString().trim();
        String yStr = etY.getText().toString().trim();

        if (TextUtils.isEmpty(xStr) || TextUtils.isEmpty(yStr)) {
            appendLog("请先输入或选择坐标");
            return;
        }

        try {
            // 验证坐标格式
            Double.parseDouble(xStr);
            Double.parseDouble(yStr);
        } catch (NumberFormatException e) {
            appendLog("坐标格式错误");
            return;
        }

        isCollecting = true;
        currentCollectCount = 0;
        appendLog("开始采集数据，共需采集" + selectedSampleCount + "次");

        // 更新UI
        updateUIForMode();

        // 启动定时采集任务
        startCollectRunnable();
    }

    private void startCollectRunnable() {
        isCollecting = true;
        collectionFailCount.set(0);
        currentCollectCount = 0;

        new Thread(() -> {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .build();

            while (isCollecting && currentCollectCount < selectedSampleCount) {
                boolean success = performCollectSync(client);

                if (!success) {
                    collectionFailCount.incrementAndGet();
                }

                currentCollectCount++;

                int count = currentCollectCount;
                int total = selectedSampleCount;

                runOnUiThread(() -> updateLastLineLog(
                        String.format("当前已采集 %d/%d 次", count, total), 5));

                try {
                    Thread.sleep(200); // 间隔200ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            // 补采逻辑
            if (collectionFailCount.get() > 0 && isCollecting) {
                int failCount = collectionFailCount.getAndSet(0);
                runOnUiThread(() -> appendLog("采集异常 " + failCount + " 次，继续补充采集"));
                selectedSampleCount += failCount; // 补足次数
                startCollectRunnable(); // 递归启动补采
                return;
            }

            runOnUiThread(() -> {
                appendLog("已完成全部 " + selectedSampleCount + " 次采集");
                stopCollecting();
            });
        }).start();
    }

    private boolean performCollectSync(OkHttpClient client) {
        String xStr = etX.getText().toString().trim();
        String yStr = etY.getText().toString().trim();


        try {
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
            String time = sdf.format(new Date());

            List<ApDto> wifiList = getWifiList();
            List<ApDto> cellList = getCellList();
            List<ApDto> apList = Stream.concat(wifiList.stream(), cellList.stream()).collect(Collectors.toList());

            String requestBatchId = String.valueOf(System.currentTimeMillis());

            CollectionReq req = new CollectionReq();
            req.setApList(apList);
            req.setRequestBatchId(requestBatchId);
            req.setCollectionBatchId(collectionBatchId);
            req.setSpaceId(Integer.parseInt(spaceId));
            req.setCreateTime(time);
            req.setRpX(x);
            req.setRpY(y);

            String json = gson.toJson(req);
            RequestBody body = RequestBody.create(json, JSON);

            Request request = new Request.Builder()
                    .url(HttpConstant.COLLECTION_URL)
                    .post(body)
                    .build();

            Response response = client.newCall(request).execute();
            return response.isSuccessful();

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }


//    private void startCollectRunnable() {
//        collectRunnable = new Runnable() {
//            @Override
//            public void run() {
//                if (!isCollecting) return;
//
//                // 执行采集
//                performCollect();
//
//                currentCollectCount++;
//
//                // 记录采集次数
//                runOnUiThread(() -> {
//                    updateLastLineLog(String.format("当前已采集" + currentCollectCount + "/" + selectedSampleCount + "次"), 5);
//                });
//
//                // 检查是否达到采集次数
//                if (currentCollectCount >= selectedSampleCount) {
//                    if (collectionFailCount.get() > 0) {
//                        runOnUiThread(() -> {
//                            appendLog("采集异常" + collectionFailCount + "次，继续补充采集");
//                        });
//                        currentCollectCount = selectedSampleCount - collectionFailCount.get() + 1;
//                        collectionFailCount.set(0);
//                    } else {
//                        runOnUiThread(() -> {
//                            appendLog("已完成全部" + selectedSampleCount + "次采集");
//                            stopCollecting();
//                        });
//                        return;
//                    }
//                }
//
//                // 继续下一次采集
//                handler.postDelayed(this, 200);
//            }
//        };
//
//        // 立即执行第一次采集
//        handler.post(collectRunnable);
//    }
//
//
//    // 执行单次采集
//    private void performCollect() {
//        String xStr = etX.getText().toString().trim();
//        String yStr = etY.getText().toString().trim();
//
//        try {
//            double x = Double.parseDouble(xStr);
//            double y = Double.parseDouble(yStr);
//
//            // 获取当前时间
//            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
//            String time = sdf.format(new Date());
//
//            new Thread(() -> {
//                try {
//                    OkHttpClient client = new OkHttpClient.Builder()
//                            .connectTimeout(30, TimeUnit.SECONDS)
//                            .build();
//
//                    // 获取WiFi列表和5G RSRP值（这里需要根据实际设备API实现）
//                    List<ApDto> wifiList = getWifiList();
//                    List<ApDto> cellList = getCellList();
//
//                    List<ApDto> apList = Stream.concat(wifiList.stream(), cellList.stream()).collect(Collectors.toList());
//
//                    CollectionReq req = new CollectionReq();
//                    req.setApList(apList);
//                    req.setCollectionBatchId(collectionBatchId);
//                    req.setSpaceId(Integer.valueOf(spaceId));
//                    req.setCreatedTime(time);
//                    req.setRpX(Double.parseDouble(etX.getText().toString()));
//                    req.setRpY(Double.parseDouble(etY.getText().toString()));
//
//                    // 构建请求体
//                    String json = gson.toJson(req);
//                    RequestBody body = RequestBody.create(json, JSON);
//
//                    Request request = new Request.Builder()
//                            .url(HttpConstant.COLLECTION_URL)
//                            .post(body)
//                            .build();
//
//                    Response response = client.newCall(request).execute();
//                    if (!response.isSuccessful()) {
//                        collectionFailCount.getAndAdd(1);
//                    }
//                } catch (Exception e) {
//                    collectionFailCount.getAndAdd(1);
//                }
//            }).start();
//
//        } catch (NumberFormatException e) {
//            appendLog("坐标格式错误");
//        }
//    }

    private void stopCollecting() {
        isCollecting = false;
        if (collectRunnable != null) {
            handler.removeCallbacks(collectRunnable);
        }
        appendLog("采集已停止");
        updateUIForMode();
        if (isOpenRpTrace) {
            loadRpTrace();
        }
    }


    // 获取WiFi列表（需要实现实际逻辑）
    private List<ApDto> getWifiList() {
        // ========== Wi-Fi ==========
        WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager != null) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                List<ApDto> results = wifiManager.getScanResults()
                        .stream()
                        .filter(ap -> ap.level > MIN_RSSI)
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
        appendLog("无wifi权限!");
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
                                        apDto.setApId(String.valueOf(lte.getCellIdentity().getPci()));
                                        apDto.setRsrp(rsrp);
                                        apDto.setRsrq(rsrq);
                                        apDto.setSinr(sinr > -20 && sinr < 30 ? sinr : -20);
                                    } else if (cellInfo instanceof CellInfoNr) {
                                        CellInfoNr nr = (CellInfoNr) cellInfo;
                                        CellSignalStrengthNr signal = (CellSignalStrengthNr) nr.getCellSignalStrength();
                                        int rsrp = signal.getSsRsrp();
                                        int rsrq = signal.getSsRsrq();
                                        int sinr = signal.getSsSinr();
                                        apDto.setApId(String.valueOf(((CellIdentityNr) nr.getCellIdentity()).getPci()));
                                        apDto.setRsrp(rsrp);
                                        apDto.setRsrq(rsrq);
                                        apDto.setSinr(sinr);
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
        appendLog("无cell权限!");
        return Collections.emptyList();
    }

    private void togglePosition() {
        isPositioning = !isPositioning;
        if (isPositioning) {
            btnStart.setText("停止定位");
            startNavigation(new CommonCallback() {
                @Override
                public void onSuccess() {
                    startPositioning();
                }

                @Override
                public void onFailure() {
                    appendLog("定位已停止");
                    btnStart.setText("开始定位");
                }
            });


        } else {
            appendLog("定位已停止");
            btnStart.setText("开始定位");
            stopPositioning();
        }
        updateUIForMode();
    }

    private void startPositioning() {
        positioningRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPositioning) return;

                requestLocation();
                handler.postDelayed(this, 1000); // 每秒请求一次
            }
        };
        handler.post(positioningRunnable);
    }

    private void stopPositioning() {
        if (positioningRunnable != null) {
            ivMap.clearMarker();
            handler.removeCallbacks(positioningRunnable);
        }
    }

    private void startNavigation(CommonCallback callback) {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .build();

                navigationBatchId = String.valueOf(System.currentTimeMillis());
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
                String time = sdf.format(new Date());

                NavigationReq req = new NavigationReq();
                req.setSpaceId(Integer.parseInt(spaceId));
                req.setCreateTime(time);
                req.setNavigationBatchId(navigationBatchId);

                String json = gson.toJson(req);

                RequestBody body = RequestBody.create(json, JSON);

                // 调用导航接口
                Request request = new Request.Builder()
                        .url(HttpConstant.LOCATION_URL + "/start")
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    runOnUiThread(() -> {
                        appendLog("定位已开启，批次号:" + navigationBatchId);
                        callback.onSuccess();
                    });
                } else {
                    runOnUiThread(() -> {
                        appendLog("定位开启失败");
                        callback.onFailure();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("定位开启失败");
                    callback.onFailure();
                });
            }
        }).start();
    }

    private void requestLocation() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .build();

                List<ApDto> wifiList = getWifiList();
                List<ApDto> cellList = getCellList();
                List<ApDto> apList = Stream.concat(wifiList.stream(), cellList.stream()).collect(Collectors.toList());

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
                String time = sdf.format(new Date());

                LocationReq req = new LocationReq();
                req.setSpaceId(Integer.parseInt(spaceId));
                req.setCreateTime(time);
                req.setNavigationBatchId(navigationBatchId);
                req.setApList(apList);

                String json = gson.toJson(req);
                RequestBody body = RequestBody.create(json, JSON);

                // 调用定位接口
                Request request = new Request.Builder()
                        .url(HttpConstant.LOCATION_URL)
                        .post(body)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String responseData = response.body().string();

                    Point point = gson.fromJson(responseData, new TypeToken<Point>() {
                    }.getType());

                    float x = point.getX();
                    float y = point.getY();

                    ivMap.setMarkerPoint(x,y);

                    runOnUiThread(() -> {
                        etX.setText(String.valueOf(x));
                        etY.setText(String.valueOf(y));
                        appendLog("当前位置: (" + x + ", " + y + ")");
                    });
                } else {
                    runOnUiThread(() -> {
                        appendLog("定位失败: " + response.code());
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    appendLog("定位错误: " + e.getMessage());
                });
            }
        }).start();
    }

    private void appendLog(String message) {
        runOnUiThread(() -> {
            String currentText = tvLog.getText().toString();
            String newText = currentText + "\n" + message;
            tvLog.setText(newText);

            // 滚动到底部
            scrollViewLog.post(() -> {
                scrollViewLog.fullScroll(View.FOCUS_DOWN);
            });
        });
    }

    private void updateLastLineLog(String message, Integer suffix) {
        runOnUiThread(() -> {
            String currentText = tvLog.getText().toString().trim();

            // 情况1：当前日志为空 → 直接设置新消息（相当于第一行）
            if (currentText.isEmpty()) {
                tvLog.setText(message);
                return;
            }

            String[] logLines = currentText.split("\n");

            String originSuffix = logLines[logLines.length - 1].substring(0, suffix);
            String newSuffix = message.substring(0, suffix);

            // 只有指定数量前缀一样时才更新，否则新加
            if (originSuffix.equals(newSuffix)) {
                // 替换最后一行（数组最后一个元素）
                logLines[logLines.length - 1] = message;

                // 重新拼接所有行（恢复换行格式）
                String newText = String.join("\n", logLines);
                tvLog.setText(newText);
            } else {
                appendLog(message);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPositioning();
        stopCollecting();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}