package com.deltaaim;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.deltaaim.service.DeltaAimAccessibilityService;
import com.deltaaim.service.FloatingWindowService;
import com.deltaaim.service.ScreenshotService;
import com.deltaaim.util.ErrorLogger;
import com.deltaaim.util.ScreenshotManager;

import java.io.File;

public class MainActivity extends AppCompatActivity {
    
    public static final String CHANNEL_ID = "deltaaim_service";
    private static final int REQUEST_SCREENSHOT = 1001;
    
    private TextView textStatus;
    private TextView textAccessibilityStatus;
    private TextView textOverlayStatus;
    private TextView textScreenshotStatus;
    private TextView textAutoModeStatus;
    private Switch switchAutoMode;
    private Button btnDataCollection;
    private Button btnStartAim;
    private Button btnAccessibilitySettings;
    private Button btnViewLogs;
    
    private boolean isAimAssistActive = false;
    private boolean isCapturing = false;
    
    private GameDetectionReceiver gameDetectionReceiver;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化
        ErrorLogger.init(this);
        ScreenshotManager.init(this);
        
        createNotificationChannel();
        initViews();
        setupListeners();
        registerGameDetectionReceiver();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkAllPermissions();
        updateAutoModeSwitch();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameDetectionReceiver != null) {
            unregisterReceiver(gameDetectionReceiver);
        }
    }
    
    private void initViews() {
        textStatus = findViewById(R.id.text_status);
        textAccessibilityStatus = findViewById(R.id.text_accessibility_status);
        textOverlayStatus = findViewById(R.id.text_overlay_status);
        textScreenshotStatus = findViewById(R.id.text_screenshot_status);
        textAutoModeStatus = findViewById(R.id.text_auto_mode_status);
        switchAutoMode = findViewById(R.id.switch_auto_mode);
        btnDataCollection = findViewById(R.id.btn_data_collection);
        btnStartAim = findViewById(R.id.btn_start_aim);
        btnAccessibilitySettings = findViewById(R.id.btn_accessibility_settings);
        btnViewLogs = findViewById(R.id.btn_view_logs);
    }
    
    private void setupListeners() {
        btnDataCollection.setOnClickListener(v -> handleDataCollection());
        btnStartAim.setOnClickListener(v -> handleAimAssist());
        btnAccessibilitySettings.setOnClickListener(v -> showSettingsDialog());
        btnViewLogs.setOnClickListener(v -> showLogsDialog());
        
        switchAutoMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DeltaAimAccessibilityService.setAutoModeEnabled(this, isChecked);
            updateAutoModeStatus(isChecked);
            String msg = isChecked ? 
                "自动模式已开启，检测到游戏时将自动启动服务" : 
                "自动模式已关闭";
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        });
    }
    
    private void updateAutoModeSwitch() {
        boolean autoModeEnabled = DeltaAimAccessibilityService.isAutoModeEnabled(this);
        switchAutoMode.setChecked(autoModeEnabled);
        updateAutoModeStatus(autoModeEnabled);
    }
    
    private void updateAutoModeStatus(boolean enabled) {
        textAutoModeStatus.setText(enabled ? "✓ 已开启" : "✗ 已关闭");
        textAutoModeStatus.setTextColor(enabled ? 0xFF4CAF50 : 0xFFF44336);
    }
    
    private void registerGameDetectionReceiver() {
        gameDetectionReceiver = new GameDetectionReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(DeltaAimAccessibilityService.ACTION_GAME_DETECTED);
        filter.addAction(DeltaAimAccessibilityService.ACTION_GAME_EXITED);
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(gameDetectionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(gameDetectionReceiver, filter);
        }
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
        btnStartAim.setText("停止瞄准辅助");
        textStatus.setText("运行中");
        Toast.makeText(this, "瞄准辅助已启动", Toast.LENGTH_SHORT).show();
    }
    
    private void stopAimAssist() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        stopService(serviceIntent);
        
        isAimAssistActive = false;
        btnStartAim.setText("启动瞄准辅助");
        textStatus.setText("就绪");
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
        
        if (requestCode == REQUEST_SCREENSHOT) {
            if (resultCode == RESULT_OK && data != null) {
                // 保存截图权限供后续自动使用
                ScreenshotManager.getInstance().savePermission(resultCode, data);
                ErrorLogger.getInstance().logInfo("MainActivity", "Screenshot permission granted and saved");
                
                startScreenshotService(resultCode, data);
            } else {
                Toast.makeText(this, "截图权限被拒绝", Toast.LENGTH_SHORT).show();
            }
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
        btnDataCollection.setText("停止采集");
        textScreenshotStatus.setText("✓ 采集中");
        Toast.makeText(this, "截图采集已开始", Toast.LENGTH_SHORT).show();
    }
    
    public void startScreenshotFromSaved() {
        if (!ScreenshotManager.getInstance().hasPermission()) {
            ErrorLogger.getInstance().logWarning("MainActivity", "No saved screenshot permission");
            return;
        }
        
        int resultCode = ScreenshotManager.getInstance().getResultCode();
        Intent data = ScreenshotManager.getInstance().getIntentData();
        
        if (resultCode != -1 && data != null) {
            ErrorLogger.getInstance().logInfo("MainActivity", "Starting screenshot service with saved permission");
            startScreenshotService(resultCode, data);
        }
    }
    
    private void stopCapture() {
        Intent serviceIntent = new Intent(this, ScreenshotService.class);
        stopService(serviceIntent);
        
        isCapturing = false;
        btnDataCollection.setText("数据采集");
        textScreenshotStatus.setText("✗ 未激活");
        Toast.makeText(this, "截图采集已停止", Toast.LENGTH_SHORT).show();
    }
    
    private void checkAllPermissions() {
        boolean accessibilityEnabled = checkAccessibilityPermission();
        boolean overlayEnabled = Settings.canDrawOverlays(this);
        boolean hasScreenshotPermission = ScreenshotManager.getInstance().hasPermission();
        
        textAccessibilityStatus.setText(accessibilityEnabled ? "✓ 已启用" : "✗ 未启用");
        textOverlayStatus.setText(overlayEnabled ? "✓ 已授权" : "✗ 未授权");
        textScreenshotStatus.setText(isCapturing ? "✓ 采集中" : (hasScreenshotPermission ? "✓ 已授权" : "✗ 未授权"));
        
        if (accessibilityEnabled && overlayEnabled) {
            textStatus.setText("就绪");
        } else {
            textStatus.setText("需要权限");
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
        status.append("\n\n• 截图权限：");
        status.append(ScreenshotManager.getInstance().hasPermission() ? "已授权" : "未授权");
        status.append("\n\n• 自动模式：");
        status.append(DeltaAimAccessibilityService.isAutoModeEnabled(this) ? "已开启" : "已关闭");
        
        new AlertDialog.Builder(this)
            .setTitle("DeltaAim 设置")
            .setMessage(status.toString())
            .setPositiveButton("打开无障碍设置", (dialog, which) -> openAccessibilitySettings())
            .setNegativeButton("打开应用设置", (dialog, which) -> openAppSettings())
            .setNeutralButton("关闭", null)
            .show();
    }
    
    private void showLogsDialog() {
        String logPath = ErrorLogger.getInstance().getLogDirectory();
        String latestLog = ErrorLogger.getInstance().getLatestLogFile();
        
        StringBuilder message = new StringBuilder();
        message.append("日志目录：\n").append(logPath).append("\n\n");
        
        if (latestLog != null) {
            File logFile = new File(latestLog);
            message.append("最新日志：\n").append(logFile.getName());
        } else {
            message.append("暂无日志文件");
        }
        
        new AlertDialog.Builder(this)
            .setTitle("运行日志")
            .setMessage(message.toString())
            .setPositiveButton("打开日志目录", (dialog, which) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(Uri.parse(logPath), "resource/folder");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(this, "无法打开文件管理器", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("清除旧日志", (dialog, which) -> {
                ErrorLogger.getInstance().clearOldLogs(3);
                Toast.makeText(this, "已清除3天前的日志", Toast.LENGTH_SHORT).show();
            })
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
                "DeltaAim 服务",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("DeltaAim 服务通知");
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    // 游戏检测广播接收器
    private class GameDetectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (DeltaAimAccessibilityService.ACTION_GAME_DETECTED.equals(action)) {
                String packageName = intent.getStringExtra(DeltaAimAccessibilityService.EXTRA_PACKAGE_NAME);
                onGameDetected(packageName);
            } else if (DeltaAimAccessibilityService.ACTION_GAME_EXITED.equals(action)) {
                onGameExited();
            }
        }
        
        private void onGameDetected(String packageName) {
            ErrorLogger.getInstance().logInfo("GameDetection", "Game detected: " + packageName);
            
            // 自动启动悬浮窗
            if (!isAimAssistActive && checkAccessibilityPermission() && Settings.canDrawOverlays(MainActivity.this)) {
                try {
                    Intent serviceIntent = new Intent(MainActivity.this, FloatingWindowService.class);
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        ContextCompat.startForegroundService(MainActivity.this, serviceIntent);
                    } else {
                        MainActivity.this.startService(serviceIntent);
                    }
                    isAimAssistActive = true;
                    ErrorLogger.getInstance().logInfo("GameDetection", "Auto-started floating window");
                } catch (Exception e) {
                    ErrorLogger.getInstance().logException("GameDetection", "Failed to start floating window", e);
                }
            }
            
            // 自动启动截图服务（使用保存的权限）
            if (!isCapturing && ScreenshotManager.getInstance().hasPermission()) {
                try {
                    MainActivity.this.startScreenshotFromSaved();
                    ErrorLogger.getInstance().logInfo("GameDetection", "Auto-started screenshot service");
                } catch (Exception e) {
                    ErrorLogger.getInstance().logException("GameDetection", "Failed to start screenshot", e);
                }
            }
        }
        
        private void onGameExited() {
            ErrorLogger.getInstance().logInfo("GameDetection", "Game exited");
        }
    }
}
