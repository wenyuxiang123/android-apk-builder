package com.deltaaim;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.deltaaim.service.DeltaAimAccessibilityService;
import com.deltaaim.service.FloatingWindowService;
import com.deltaaim.service.ScreenshotService;

public class MainActivity extends AppCompatActivity {
    
    public static final String CHANNEL_ID = "deltaaim_service";
    private static final int REQUEST_SCREENSHOT = 1001;
    
    private TextView textStatus;
    private TextView textAccessibilityStatus;
    private TextView textOverlayStatus;
    private TextView textScreenshotStatus;
    private Button btnDataCollection;
    private Button btnStartAim;
    private Button btnAccessibilitySettings;
    
    private boolean isAimAssistActive = false;
    private boolean isCapturing = false;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        createNotificationChannel();
        initViews();
        setupListeners();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkAllPermissions();
    }
    
    private void initViews() {
        textStatus = findViewById(R.id.text_status);
        textAccessibilityStatus = findViewById(R.id.text_accessibility_status);
        textOverlayStatus = findViewById(R.id.text_overlay_status);
        textScreenshotStatus = findViewById(R.id.text_screenshot_status);
        btnDataCollection = findViewById(R.id.btn_data_collection);
        btnStartAim = findViewById(R.id.btn_start_aim);
        btnAccessibilitySettings = findViewById(R.id.btn_accessibility_settings);
    }
    
    private void setupListeners() {
        btnDataCollection.setOnClickListener(v -> handleDataCollection());
        btnStartAim.setOnClickListener(v -> handleAimAssist());
        btnAccessibilitySettings.setOnClickListener(v -> showSettingsDialog());
    }
    
    private void handleDataCollection() {
        if (isCapturing) {
            stopCapture();
        } else {
            requestScreenshotPermission();
        }
    }
    
    private void handleAimAssist() {
        if (!checkAccessibilityPermission()) {
            showPermissionDialog(
                "需要开启无障碍服务才能使用瞄准辅助功能",
                () -> openAccessibilitySettings()
            );
            return;
        }
        
        if (!Settings.canDrawOverlays(this)) {
            showPermissionDialog(
                "需要悬浮窗权限才能显示瞄准辅助界面",
                () -> requestOverlayPermission()
            );
            return;
        }
        
        if (isAimAssistActive) {
            stopAimAssist();
        } else {
            startAimAssist();
        }
    }
    
    private void startAimAssist() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        isAimAssistActive = true;
        btnStartAim.setText("Stop Aim Assist");
        textStatus.setText("Active");
        Toast.makeText(this, "瞄准辅助已启动", Toast.LENGTH_SHORT).show();
    }
    
    private void stopAimAssist() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        stopService(serviceIntent);
        
        isAimAssistActive = false;
        btnStartAim.setText("Start Aim Assist");
        textStatus.setText("Ready");
        Toast.makeText(this, "瞄准辅助已停止", Toast.LENGTH_SHORT).show();
    }
    
    private void requestScreenshotPermission() {
        MediaProjectionManager projectionManager = 
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_SCREENSHOT);
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_SCREENSHOT && resultCode == RESULT_OK) {
            startScreenshotService(resultCode, data);
        }
    }
    
    private void startScreenshotService(int resultCode, Intent data) {
        Intent serviceIntent = new Intent(this, ScreenshotService.class);
        serviceIntent.putExtra("resultCode", resultCode);
        serviceIntent.putExtra("data", data);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        isCapturing = true;
        btnDataCollection.setText("Stop Capture");
        textScreenshotStatus.setText("✓ Capturing");
        Toast.makeText(this, "截图采集已开始", Toast.LENGTH_SHORT).show();
    }
    
    private void stopCapture() {
        Intent serviceIntent = new Intent(this, ScreenshotService.class);
        stopService(serviceIntent);
        
        isCapturing = false;
        btnDataCollection.setText("Data Collection");
        textScreenshotStatus.setText("✗ Inactive");
        Toast.makeText(this, "截图采集已停止", Toast.LENGTH_SHORT).show();
    }
    
    private void checkAllPermissions() {
        boolean accessibilityEnabled = checkAccessibilityPermission();
        boolean overlayEnabled = Settings.canDrawOverlays(this);
        
        textAccessibilityStatus.setText(accessibilityEnabled ? "✓ Enabled" : "✗ Disabled");
        textOverlayStatus.setText(overlayEnabled ? "✓ Enabled" : "✗ Disabled");
        textScreenshotStatus.setText(isCapturing ? "✓ Capturing" : "✗ Inactive");
        
        if (accessibilityEnabled && overlayEnabled) {
            textStatus.setText("Ready");
        } else {
            textStatus.setText("Permission Required");
        }
    }
    
    private boolean checkAccessibilityPermission() {
        return DeltaAimAccessibilityService.isServiceEnabled(this);
    }
    
    private void openAccessibilitySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Toast.makeText(this, "请开启 DeltaAim 无障碍服务", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void requestOverlayPermission() {
        Intent intent = new Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:" + getPackageName())
        );
        startActivity(intent);
    }
    
    private void showSettingsDialog() {
        StringBuilder status = new StringBuilder();
        status.append("权限状态：\n\n");
        status.append("• 无障碍服务：");
        status.append(checkAccessibilityPermission() ? "已开启" : "未开启");
        status.append("\n\n• 悬浮窗权限：");
        status.append(Settings.canDrawOverlays(this) ? "已授权" : "未授权");
        status.append("\n\n• 截图状态：");
        status.append(isCapturing ? "采集中" : "未启动");
        
        new AlertDialog.Builder(this)
            .setTitle("DeltaAim 设置")
            .setMessage(status.toString())
            .setPositiveButton("打开无障碍设置", (dialog, which) -> openAccessibilitySettings())
            .setNegativeButton("打开应用设置", (dialog, which) -> openAppSettings())
            .setNeutralButton("关闭", null)
            .show();
    }
    
    private void showPermissionDialog(String message, Runnable onConfirm) {
        new AlertDialog.Builder(this)
            .setTitle("需要权限")
            .setMessage(message)
            .setPositiveButton("去设置", (dialog, which) -> onConfirm.run())
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DeltaAim Service",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("DeltaAim 服务通知");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
