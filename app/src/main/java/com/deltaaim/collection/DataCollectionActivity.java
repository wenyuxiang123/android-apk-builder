package com.deltaaim.collection;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.deltaaim.R;

import java.io.File;

/**
 * 数据采集控制界面
 */
public class DataCollectionActivity extends AppCompatActivity {
    private static final String TAG = "CollectionActivity";
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final int REQUEST_STORAGE_PERMISSION = 1002;

    // UI组件
    private Spinner spinnerInterval;
    private EditText editCount;
    private Button btnStartCapture;
    private Button btnStopCapture;
    private Button btnSingleCapture;
    private Button btnOpenFolder;
    private TextView textStatus;
    private TextView textCount;
    private TextView textPath;

    // 服务
    private DataCollectionService collectionService;
    private Intent serviceIntent;

    // 状态
    private boolean hasProjectionPermission = false;
    private int targetCount = 0;

    // 间隔选项
    private final String[] intervalOptions = {"0.5秒", "1秒", "2秒", "3秒", "5秒"};
    private final int[] intervalValues = {500, 1000, 2000, 3000, 5000};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);

        initViews();
        checkPermissions();
    }

    private void initViews() {
        spinnerInterval = findViewById(R.id.spinner_interval);
        editCount = findViewById(R.id.edit_count);
        btnStartCapture = findViewById(R.id.btn_start_capture);
        btnStopCapture = findViewById(R.id.btn_stop_capture);
        btnSingleCapture = findViewById(R.id.btn_single_capture);
        btnOpenFolder = findViewById(R.id.btn_open_folder);
        textStatus = findViewById(R.id.text_status);
        textCount = findViewById(R.id.text_count);
        textPath = findViewById(R.id.text_path);

        // 设置间隔选项
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, intervalOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerInterval.setAdapter(adapter);
        spinnerInterval.setSelection(2); // 默认2秒

        // 按钮事件
        btnStartCapture.setOnClickListener(v -> startBatchCapture());
        btnStopCapture.setOnClickListener(v -> stopCapture());
        btnSingleCapture.setOnClickListener(v -> singleCapture());
        btnOpenFolder.setOnClickListener(v -> openCollectionFolder());

        // 初始状态
        updateUI(false);

        // 显示存储路径
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File collectionDir = new File(storageDir, "DeltaAim_Dataset");
        textPath.setText("存储位置: " + collectionDir.getAbsolutePath());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 获取服务实例
        collectionService = DataCollectionService.getInstance();
        if (collectionService != null) {
            collectionService.setCallback(callback);
            updateUI(collectionService.isCollecting());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (collectionService != null) {
            collectionService.setCallback(null);
        }
    }

    /**
     * 检查权限
     */
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用MANAGE_EXTERNAL_STORAGE或分区存储
            if (!Environment.isExternalStorageManager()) {
                textStatus.setText("请授予存储权限");
                requestStoragePermission();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                 Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_STORAGE_PERMISSION);
            }
        }
    }

    private void requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } catch (Exception e) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            }
        }
    }

    /**
     * 请求MediaProjection权限
     */
    private void requestProjectionPermission() {
        if (hasProjectionPermission) {
            startService();
            return;
        }

        MediaProjectionManager manager = (MediaProjectionManager) 
            getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        startActivityForResult(
            manager.createScreenCaptureIntent(),
            REQUEST_MEDIA_PROJECTION
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                hasProjectionPermission = true;
                startService();
                
                // 初始化服务
                if (collectionService != null) {
                    collectionService.initProjection(resultCode, data);
                    startBatchCaptureInternal();
                }
            } else {
                textStatus.setText("需要屏幕录制权限才能采集");
                Toast.makeText(this, "授权失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * 启动服务
     */
    private void startService() {
        serviceIntent = new Intent(this, DataCollectionService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    /**
     * 开始批量采集
     */
    private void startBatchCapture() {
        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "请先授予存储权限", Toast.LENGTH_SHORT).show();
                checkPermissions();
                return;
            }
        }

        // 解析目标数量
        String countStr = editCount.getText().toString().trim();
        if (countStr.isEmpty()) {
            targetCount = 0; // 无限采集
        } else {
            targetCount = Integer.parseInt(countStr);
        }

        // 请求权限并开始
        requestProjectionPermission();
    }

    /**
     * 内部开始批量采集
     */
    private void startBatchCaptureInternal() {
        if (collectionService == null) {
            collectionService = DataCollectionService.getInstance();
        }

        if (collectionService == null) {
            Toast.makeText(this, "服务未启动", Toast.LENGTH_SHORT).show();
            return;
        }

        collectionService.setCallback(callback);

        // 获取间隔时间
        int intervalIndex = spinnerInterval.getSelectedItemPosition();
        int intervalMs = intervalValues[intervalIndex];

        // 开始采集
        boolean success = collectionService.startCollection(intervalMs);
        if (success) {
            updateUI(true);
            textStatus.setText("采集中... 间隔: " + intervalOptions[intervalIndex]);
        } else {
            textStatus.setText("启动采集失败");
        }
    }

    /**
     * 停止采集
     */
    private void stopCapture() {
        if (collectionService != null) {
            collectionService.stopCollection();
        }
        updateUI(false);
        textStatus.setText("已停止采集");
    }

    /**
     * 单张截图
     */
    private void singleCapture() {
        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                Toast.makeText(this, "请先授予存储权限", Toast.LENGTH_SHORT).show();
                checkPermissions();
                return;
            }
        }

        if (collectionService == null || !hasProjectionPermission) {
            // 需要先授权
            MediaProjectionManager manager = (MediaProjectionManager) 
                getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(
                manager.createScreenCaptureIntent(),
                REQUEST_MEDIA_PROJECTION
            );
            Toast.makeText(this, "请先授权，然后再次点击单张截图", Toast.LENGTH_SHORT).show();
            return;
        }

        String path = collectionService.captureOnce();
        if (path != null) {
            Toast.makeText(this, "已保存: " + path, Toast.LENGTH_SHORT).show();
            textCount.setText("已采集: 1 张");
        } else {
            Toast.makeText(this, "截图失败", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 打开采集目录
     */
    private void openCollectionFolder() {
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File collectionDir = new File(storageDir, "DeltaAim_Dataset");
        
        // 使用系统文件管理器打开
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(android.net.Uri.parse(collectionDir.getAbsolutePath()), "resource/folder");
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        try {
            startActivity(intent);
        } catch (Exception e) {
            // 如果没有文件管理器，显示路径
            Toast.makeText(this, "目录: " + collectionDir.getAbsolutePath(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 更新UI状态
     */
    private void updateUI(boolean isCollecting) {
        btnStartCapture.setEnabled(!isCollecting);
        btnStopCapture.setEnabled(isCollecting);
        spinnerInterval.setEnabled(!isCollecting);
        editCount.setEnabled(!isCollecting);

        if (isCollecting) {
            btnStartCapture.setText("采集中...");
            btnStartCapture.setBackgroundColor(0xFF888888);
        } else {
            btnStartCapture.setText("开始采集");
            btnStartCapture.setBackgroundColor(0xFF4CAF50);
        }
    }

    /**
     * 采集回调
     */
    private DataCollectionService.CollectionCallback callback = 
        new DataCollectionService.CollectionCallback() {
        @Override
        public void onCaptureComplete(String path, int totalCount) {
            runOnUiThread(() -> {
                textCount.setText("已采集: " + totalCount + " 张");
                
                // 检查是否达到目标数量
                if (targetCount > 0 && totalCount >= targetCount) {
                    stopCapture();
                    textStatus.setText("采集完成! 共 " + totalCount + " 张");
                }
            });
        }

        @Override
        public void onError(String message) {
            runOnUiThread(() -> {
                textStatus.setText("错误: " + message);
                updateUI(false);
            });
        }

        @Override
        public void onCollectionStopped(int totalCount) {
            runOnUiThread(() -> {
                textStatus.setText("已停止，共采集 " + totalCount + " 张");
                updateUI(false);
            });
        }
    };
}
