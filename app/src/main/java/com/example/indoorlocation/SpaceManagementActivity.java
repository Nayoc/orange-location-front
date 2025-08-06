package com.example.indoorlocation;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.indoorlocation.adapter.SpaceAdapter;
import com.example.indoorlocation.model.Space;
import com.example.indoorlocation.util.FileUtil;
import com.example.indoorlocation.util.LocationHelper;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SpaceManagementActivity extends Activity {
    private RecyclerView recyclerView;
    private SpaceAdapter adapter;
    private List<Space> spaceList = new ArrayList<>();
    private OkHttpClient client;
    private Gson gson;
    private ImageView imagePreview;
    private Uri selectedImageUri;
    private File imageFile;
    private AlertDialog currentDialog;
    private LocationHelper locationHelper;
    private static final String BASE_URL = "http://10.8.13.38:50555/space";
    private static final int STORAGE_PERMISSION_REQUEST_CODE = 103;
    private static final int PICK_IMAGE_FROM_GALLERY = 101; // 图库选择请求码
    private static final int REQUEST_STORAGE_PERMISSION = 102; // 存储权限请求码
    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.space_main);

        // 初始化控件
        recyclerView = findViewById(R.id.space_recycler_view);
        Button addButton = findViewById(R.id.add_space_btn);

        // 初始化工具
        client = new OkHttpClient();
        gson = new Gson();
        locationHelper = new LocationHelper(this);

        // 检查定位权限
        locationHelper.checkLocationPermission(this);

        // 配置RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SpaceAdapter(spaceList);
        recyclerView.setAdapter(adapter);

        // 设置列表项点击事件
        adapter.setOnItemClickListener(new SpaceAdapter.OnItemClickListener() {
            @Override
            public void onEditClick(int position) {
                Space space = spaceList.get(position);
                showAddSpaceDialog(true, space, position);
            }

            @Override
            public void onDeleteClick(int position) {
                Space space = spaceList.get(position);
                showDeleteConfirmationDialog(space, position);
            }

            @Override
            public void onNameClick(int position, Space space) {
                // 点击空间名称进入编辑状态
                showEditNameDialog(space, position);
            }
        });

        // 新增空间按钮点击事件
        addButton.setOnClickListener(v -> showAddSpaceDialog(false, null, -1));

        // 加载空间列表
        fetchSpaces();
    }

    // 获取空间列表
    private void fetchSpaces() {

        Request request = new Request.Builder()
                .url(BASE_URL)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(SpaceManagementActivity.this,
                        "获取空间列表失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    String responseData = response.body().string();
                    Type listType = new TypeToken<List<Space>>(){}.getType();
                    List<Space> spaces = gson.fromJson(responseData, listType);

                    runOnUiThread(() -> {
                        spaceList.clear();
                        spaceList.addAll(spaces);
                        adapter.updateData(spaceList);
                    });
                } else {
                    runOnUiThread(() -> Toast.makeText(SpaceManagementActivity.this,
                            "获取空间列表失败", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    // 新增空间
    private void addSpace(Space space) {
        locationHelper.getCurrentLocation(locationInfo -> {
            runOnUiThread(() -> {
                if (!locationInfo.isSuccess()) {
                    Toast.makeText(this, locationInfo.getErrorMsg(), Toast.LENGTH_SHORT).show();
                    return;
                }

                // 构建multipart请求体
                MultipartBody.Builder builder = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("spaceName", space.getSpaceName())

                        .addFormDataPart("latitude", new DecimalFormat("#.0000").format(locationInfo.getLatitude()))
                        .addFormDataPart("longitude", new DecimalFormat("#.0000").format(locationInfo.getLongitude()));

                // 若有图片文件，添加文件参数
                if (space.getSpacePlan() != null && space.getSpacePlan().exists()) {
                    builder.addFormDataPart(
                            "spacePlan",
                            space.getSpacePlan().getName(),
                            RequestBody.create(MediaType.parse("image/jpeg"), space.getSpacePlan())
                    );
                }

                Request request = new Request.Builder()
                        .url(BASE_URL)
                        .post(builder.build())
                        .build();

                client.newCall(request).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        runOnUiThread(() -> Toast.makeText(SpaceManagementActivity.this,
                                "新增空间失败", Toast.LENGTH_SHORT).show());
                    }

                    @Override
                    public void onResponse(Call call, Response response) {
                        runOnUiThread(() -> {
                            if (response.isSuccessful()) {
                                Toast.makeText(SpaceManagementActivity.this,
                                        "新增空间成功", Toast.LENGTH_SHORT).show();
                                fetchSpaces();
                            } else {
                                Toast.makeText(SpaceManagementActivity.this,
                                        "新增空间失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                });
            });
        });
    }

    // 删除空间
    private void deleteSpace(String spaceId, int position) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/" + spaceId)
                .delete()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(SpaceManagementActivity.this,
                        "删除空间失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(SpaceManagementActivity.this,
                                "删除空间成功", Toast.LENGTH_SHORT).show();
                        // 从列表中移除并更新UI
                        spaceList.remove(position);
                        adapter.updateData(spaceList);
                    } else {
                        Toast.makeText(SpaceManagementActivity.this,
                                "删除空间失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // 显示新增/编辑空间对话框（核心修改部分）
    private void showAddSpaceDialog(boolean isEdit, Space space, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LayoutInflater inflater = getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_add_space, null);

        // 获取控件引用
        EditText nameEditText = view.findViewById(R.id.et_space_name);
        Button uploadButton = view.findViewById(R.id.btn_upload_image);
        imagePreview = view.findViewById(R.id.iv_preview);
        Button saveButton = view.findViewById(R.id.btn_save);

        // 编辑模式下填充数据
        if (isEdit && space != null) {
            builder.setTitle("编辑空间");
            nameEditText.setText(space.getSpaceName());

        } else {
            builder.setTitle("新增空间");
            selectedImageUri = null;
            imagePreview.setVisibility(View.GONE);
        }

        // 设置对话框不可通过外部点击关闭
        builder.setCancelable(false);

        // 图片选择按钮点击事件
        uploadButton.setOnClickListener(v -> {
            openGallery();
        });

        // 保存按钮点击事件（使用spacePlan字段）
        saveButton.setOnClickListener(v -> {
            String spaceName = nameEditText.getText().toString().trim();
            if (TextUtils.isEmpty(spaceName)) {
                Toast.makeText(this, "请输入空间名称", Toast.LENGTH_SHORT).show();
                return;
            }

            if (isEdit && space != null) {
                // 编辑模式：更新名称和图片
                space.setSpaceName(spaceName);
                updateSpace(space);
            } else {
                // 新增模式：创建Space对象并设置spacePlan
                Space newSpace = new Space(spaceName);
                // 获取从图库选择的图片Uri（通过onActivityResult存储到spacePlan）
                newSpace.setSpacePlan(imageFile);
                addSpace(newSpace);
            }

            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.dismiss();
            }
        });

        // 显示对话框时设置为不可取消（避免误触关闭）
        currentDialog = builder.setView(view)
                .setCancelable(false) // 关键：禁止外部点击关闭
                .setNegativeButton("取消", (dialog, id) -> {
                    dialog.dismiss();
                })
                .create();

        currentDialog.show();
    }

    // 打开手机图库
    private void openGallery() {
        if (checkAndRequestStoragePermission()) {
            // 明确 Intent 是打开图库选择图片
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*"); // 只显示图片
            // 启动图库，等待返回结果
            startActivityForResult(Intent.createChooser(intent, "选择图片"), PICK_IMAGE_FROM_GALLERY);
        }
    }

    // 检查并请求存储权限
    private boolean checkAndRequestStoragePermission() {
        String[] permissions;
        // 明确使用API级别判断（33及以上需要READ_MEDIA_IMAGES）
        if (Build.VERSION.SDK_INT >= 33) {
            // Android 13+（包括API 35）使用新权限
            permissions = new String[]{android.Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            // 低版本使用旧权限
            permissions = new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE};
        }

        // 检查权限是否已授予
        if (ContextCompat.checkSelfPermission(this, permissions[0])
                != PackageManager.PERMISSION_GRANTED) {
            // 请求权限
            ActivityCompat.requestPermissions(this, permissions, STORAGE_PERMISSION_REQUEST_CODE);
            return false;
        }
        return true;
    }

    // 处理权限请求结果
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openGallery();
            } else {
                Toast.makeText(this, "需要存储权限才能选择图片", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == 1001) { // Location permission
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // 权限已授予，可以正常使用
                Toast.makeText(this, "定位权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要定位权限才能获取位置信息", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_FROM_GALLERY && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            if(selectedImageUri != null){
                imageFile = FileUtil.uriToFile(this,selectedImageUri);
                try {
                    Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                    imagePreview.setImageBitmap(bitmap);
                    imagePreview.setVisibility(View.VISIBLE);
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, "无法加载图片", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }


    //
    // 显示删除确认对话框
    private void showDeleteConfirmationDialog(Space space, int position) {
        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除 " + space.getSpaceName() + " 吗？")
                .setPositiveButton("删除", (dialog, which) -> {
                    // 调用删除接口
                    deleteSpace(space.getId(), position);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // 更新空间信息
    private void updateSpace(Space space) {
        String json = gson.toJson(space);
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(BASE_URL + "/" + space.getId())
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(SpaceManagementActivity.this,
                        "更新空间失败", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(SpaceManagementActivity.this,
                                "更新空间成功", Toast.LENGTH_SHORT).show();
                        fetchSpaces();
                    } else {
                        Toast.makeText(SpaceManagementActivity.this,
                                "更新空间失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }

    // 显示编辑空间名称对话框
    private void showEditNameDialog(Space space, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("编辑空间名称");

        final EditText input = new EditText(this);
        input.setText(space.getSpaceName());
        input.setSelection(space.getSpaceName().length());
        builder.setView(input);

        builder.setPositiveButton("保存", (dialog, which) -> {
            String newName = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newName)) {
                space.setSpaceName(newName);
                updateSpaceName(space);
            } else {
                Toast.makeText(this, "空间名称不能为空", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    // 更新空间名称
    private void updateSpaceName(Space space) {
        String json = gson.toJson(space);
        RequestBody body = RequestBody.create(json, JSON);

        Request request = new Request.Builder()
                .url(BASE_URL)
                .put(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(SpaceManagementActivity.this,
                        "更新失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) {
                runOnUiThread(() -> {
                    if (response.isSuccessful()) {
                        Toast.makeText(SpaceManagementActivity.this,
                                "更新成功", Toast.LENGTH_SHORT).show();
                        fetchSpaces(); // 重新加载列表
                    } else {
                        Toast.makeText(SpaceManagementActivity.this,
                                "更新失败", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}