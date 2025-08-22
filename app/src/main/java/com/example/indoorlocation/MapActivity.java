package com.example.indoorlocation;

import static com.example.indoorlocation.SpaceManagementActivity.JSON;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
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
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.example.indoorlocation.constant.HttpConstant;
import com.example.indoorlocation.model.Space;
import com.example.indoorlocation.util.FileUtil;
import com.example.indoorlocation.view.ZoomableImageView;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONObject;

import java.io.File;
import java.lang.reflect.Type;
import java.util.concurrent.TimeUnit;

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
    //    private TextView tvDimensions;
    private TextView btnBuildCoord;
    private Button btnUploadMap;
    private TextView tvLog;
    private ScrollView scrollViewLog;
    private EditText etX, etY;
    private LinearLayout layoutSampleCount;
    private Spinner spinnerSampleCount, spinnerMode;
    private ImageButton btnCollect;
    private Button btnStart;
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

    // 采集次数选项
    private final String[] sampleCounts = {"1次", "5次", "10次", "30次"};
    private final int[] sampleCountValues = {1, 5, 10, 30};
    private int selectedSampleCount = 1;

    // 模式选项
    private final String[] modes = {"采集", "定位"};
    private String currentMode = "采集";

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
    }

    private void initViews() {
        tvSpaceName = findViewById(R.id.tv_space_name);
        btnBack = findViewById(R.id.btn_back);
        layoutUpload = findViewById(R.id.layout_upload);
        ivMap = findViewById(R.id.iv_map);
        btnReset = findViewById(R.id.btn_reset);
//        tvDimensions = findViewById(R.id.tv_dimensions);
        btnBuildCoord = findViewById(R.id.btn_build_coord);
        btnUploadMap = findViewById(R.id.btn_upload_map);
        tvLog = findViewById(R.id.tv_log);
        scrollViewLog = findViewById(R.id.scrollView_log);
        etX = findViewById(R.id.et_x);
        etY = findViewById(R.id.et_y);
        layoutSampleCount = findViewById(R.id.layout_sample_count);
        spinnerSampleCount = findViewById(R.id.spinner_sample_count);
        spinnerMode = findViewById(R.id.spinner_mode);
        btnCollect = findViewById(R.id.btn_collect);
        btnStart = findViewById(R.id.btn_start);

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

        btnBuildCoord.setOnClickListener(v -> showBuildCoordDialog());

        btnReset.setOnClickListener(v -> ivMap.reset());

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
                updateUIForMode();
                appendLog("切换到" + currentMode + "模式");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        // 采集按钮
        btnCollect.setOnClickListener(v -> collectLocation());

        // 开始按钮
        btnStart.setOnClickListener(v -> toggleOperation());

        // 坐标输入监听
        TextWatcher coordinateWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 坐标改变时的处理
            }
        };

        etX.addTextChangedListener(coordinateWatcher);
        etY.addTextChangedListener(coordinateWatcher);
    }

    private void updateUIForMode() {
        if ("采集".equals(currentMode)) {
            layoutSampleCount.setVisibility(View.VISIBLE);
            btnStart.setText(isCollecting ? "停止采集" : "开始采集");
            btnCollect.setVisibility(View.VISIBLE);
        } else {
            layoutSampleCount.setVisibility(View.GONE);
            btnStart.setText(isPositioning ? "停止定位" : "开始定位");
            btnCollect.setVisibility(View.GONE);
        }
    }

    private void loadSpaceData(String spaceId) {
        // 模拟检查地图状态
        // 实际应用中应该调用服务器API获取地图URL
        new Thread(() -> {
            try {

                Request request = new Request.Builder()
                        .url(HttpConstant.BASE_URL + "/" + spaceId)
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Type listType = new TypeToken<Space>() {
                    }.getType();
                    Space space = gson.fromJson(responseData, listType);
                    scaleX = space.getScaleX();
                    scaleRate = space.getScaleRate();

//                    tvDimensions.setText("横宽比:" + space.getScaleRate() + "\t 长:" + space.getScaleX() + "米");
                    String mapUrl = space.getSpacePlanUrl();

                    runOnUiThread(() -> {
                        if (!mapUrl.isEmpty()) {
                            this.mapUrl = mapUrl;
                            hasMap = true;
                            showMap();
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


        // 保存按钮点击事件（使用spacePlan字段）
        buildButton.setOnClickListener(v -> {
            Double editScaleX = Double.valueOf(editTextWidth.getText().toString().trim());

            new Thread(()->{
                try {

                    // 修改数据
                    Space editSpace = new Space();
                    editSpace.setId(spaceId);
                    editSpace.setScaleX(editScaleX);
                    String json = gson.toJson(editSpace);
                    RequestBody body = RequestBody.create(json, JSON);

                    Request request = new Request.Builder()
                            .url(HttpConstant.BASE_URL)
                            .put(body)
                            .build();

                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        runOnUiThread(() -> {
                            scaleX = editScaleX;
                            // 开始绘制坐标系

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
            }).start();;

        });

        buildCoordDialog = builder.setView(view)
                .setCancelable(false) // 关键：禁止外部点击关闭
                .setNegativeButton("取消", (dialog, id) -> {
                    dialog.dismiss();
                })
                .create();

        buildCoordDialog.show();

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
                        .url("http://your-api-url.com/api/spaces/upload-map")
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
        isCollecting = !isCollecting;
        if (isCollecting) {
            appendLog("开始采集数据...");
            btnStart.setText("停止采集");
        } else {
            appendLog("采集已停止");
            btnStart.setText("开始采集");
        }
        updateUIForMode();
    }

    private void togglePosition() {
        isPositioning = !isPositioning;
        if (isPositioning) {
            appendLog("开始定位...");
            btnStart.setText("停止定位");
            startPositioning();
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
            handler.removeCallbacks(positioningRunnable);
        }
    }

    private void requestLocation() {
        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .build();

                // 这里应该是调用实际的定位接口
                Request request = new Request.Builder()
                        .url("http://your-api-url.com/api/spaces/" + spaceId + "/location")
                        .build();

                Response response = client.newCall(request).execute();
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    JSONObject jsonObject = new JSONObject(responseData);
                    double x = jsonObject.optDouble("x", 0);
                    double y = jsonObject.optDouble("y", 0);

                    runOnUiThread(() -> {
                        etX.setText(String.valueOf(x));
                        etY.setText(String.valueOf(y));
                        appendLog("定位成功: (" + x + ", " + y + ")");
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

    private void collectLocation() {
        String xStr = etX.getText().toString().trim();
        String yStr = etY.getText().toString().trim();

        if (xStr.isEmpty() || yStr.isEmpty()) {
            appendLog("请先输入坐标");
            return;
        }

        try {
            double x = Double.parseDouble(xStr);
            double y = Double.parseDouble(yStr);

            appendLog("开始采集位置 (" + x + ", " + y + ")，将采集" + selectedSampleCount + "次");

            new Thread(() -> {
                try {
                    OkHttpClient client = new OkHttpClient.Builder()
                            .connectTimeout(30, TimeUnit.SECONDS)
                            .build();

                    // 构建请求体
                    JSONObject jsonBody = new JSONObject();
                    jsonBody.put("spaceId", spaceId);
                    jsonBody.put("x", x);
                    jsonBody.put("y", y);
                    jsonBody.put("sampleCount", selectedSampleCount);

                    RequestBody requestBody = RequestBody.create(
                            jsonBody.toString(),
                            MediaType.parse("application/json")
                    );

                    Request request = new Request.Builder()
                            .url("http://your-api-url.com/api/spaces/collect")
                            .post(requestBody)
                            .build();

                    Response response = client.newCall(request).execute();
                    if (response.isSuccessful()) {
                        String responseData = response.body().string();
                        JSONObject jsonResponse = new JSONObject(responseData);
                        String message = jsonResponse.optString("message", "采集成功");

                        runOnUiThread(() -> {
                            appendLog("采集完成: " + message);
                        });
                    } else {
                        runOnUiThread(() -> {
                            appendLog("采集失败: " + response.code());
                        });
                    }
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        appendLog("采集错误: " + e.getMessage());
                    });
                }
            }).start();

        } catch (NumberFormatException e) {
            appendLog("坐标格式错误");
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopPositioning();
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
    }
}