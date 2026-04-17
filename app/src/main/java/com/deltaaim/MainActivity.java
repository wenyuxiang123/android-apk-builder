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
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.deltaaim.service.DeltaAimAccessibilityService;
import com.deltaaim.service.FloatingWindowService;
import com.deltaaim.service.PermissionHolderService;
import com.deltaaim.util.ErrorLogger;

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
    private Button btnStartAim;
    private Button btnAccessibilitySettings;
    private Button btnViewLogs;
    
    private boolean isAimAssistActive = false;
    private boolean waitingForPermission = false;
    
    private GameDetectionReceiver gameDetectionReceiver;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        ErrorLogger.init(this);
        
        createNotificationChannel();
        initViews();
        setupListeners();
        registerGameDetectionReceiver();
        
        // 设置权限回调
        PermissionHolderService.setCallback(() -> {
            handler.post(() -> {
                Log.d("MainActivity", "Permission ready callback received");
                updateScreenshotStatus(true);
                textStatus.setText("就绪");
                Toast.makeText(MainActivity.this, "截图权限已就绪", Toast.LENGTH_SHORT).show();
            });
        });
        
        // 应用启动时检查截图权限
        checkScreenshotPermissionOnStart();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkAllPermissions();
        updateAutoModeSwitch();
    }
    
    /**
     * 应用启动时检查截图权限状态
     */
    private void checkScreenshotPermissionOnStart() {
        boolean hasPermission = PermissionHolderService.hasPermission();
        Log.d("MainActivity", "checkScreenshotPermissionOnStart: " + hasPermission);
        
        if (hasPermission) {
            updateScreenshotStatus(true);
            textStatus.setText("就绪");
        } else {
            // 没有权限，请求授权
            handler.postDelayed(() -> {
                requestScreenshotPermission();
            }, 500);
        }
    }
    
    private void requestScreenshotPermission() {
        MediaProjectionManager projectionManager = 
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        if (projectionManager != null) {
            Intent intent = projectionManager.createScreenCaptureIntent();
            startActivityForResult(intent, REQUEST_SCREENSHOT);
            Toast.makeText(this, "请点击「立即开始」授权截图权限", Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d("MainActivity", "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);
        
        if (requestCode == REQUEST_SCREENSHOT) {
            if (resultCode == RESULT_OK && data != null) {
                // 启动PermissionHolderService来持有权限
                Intent serviceIntent = new Intent(this, PermissionHolderService.class);
                serviceIntent.putExtra(PermissionHolderService.EXTRA_RESULT_CODE, resultCode);
                serviceIntent.putExtra(PermissionHolderService.EXTRA_RESULT_DATA, data);
                
                waitingForPermission = true;
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                
                Log.i("MainActivity", "PermissionHolderService started");
                
                // 延迟检查权限状态（等待服务启动完成）
                handler.postDelayed(() -> {
                    checkPermissionStatus();
                }, 1000);
                
            } else {
                Toast.makeText(this, "截图权限被拒绝，部分功能将无法使用", Toast.LENGTH_SHORT).show();
                updateScreenshotStatus(false);
            }
        }
    }
    
    /**
     * 延迟检查权限状态
     */
    private void checkPermissionStatus() {
        boolean hasPermission = PermissionHolderService.hasPermission();
        Log.d("MainActivity", "checkPermissionStatus: " + hasPermission);
        
        if (hasPermission) {
            updateScreenshotStatus(true);
            textStatus.setText("就绪");
            Toast.makeText(this, "截图权限已授权！", Toast.LENGTH_SHORT).show();
        } else {
            // 如果服务还没准备好，继续等待
            if (waitingForPermission) {
                handler.postDelayed(() -> {
                    checkPermissionStatus();
                }, 500);
            }
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (gameDetectionReceiver != null) {
            unregisterReceiver(gameDetectionReceiver);
        }
        PermissionHolderService.setCallback(null);
    }
    
    private void initViews() {
        textStatus = findViewById(R.id.text_status);
        textAccessibilityStatus = findViewById(R.id.text_accessibility_status);
        textOverlayStatus = findViewById(R.id.text_overlay_status);
        textScreenshotStatus = findViewById(R.id.text_screenshot_status);
        textAutoModeStatus = findViewById(R.id.text_auto_mode_status);
        switchAutoMode = findViewById(R.id.switch_auto_mode);
        btnStartAim = findViewById(R.id.btn_start_aim);
        btnAccessibilitySettings = findViewById(R.id.btn_accessibility_settings);
        btnViewLogs = findViewById(R.id.btn_view_logs);
    }
    
    private void setupListeners() {
        btnStartAim.setOnClickListener(v -> handleAimAssist());
        btnAccessibilitySettings.setOnClickListener(v -> showSettingsDialog());
        btnViewLogs.setOnClickListener(v -> showLogsDialog());
        
        switchAutoMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            DeltaAimAccessibilityService.setAutoModeEnabled(this, isChecked);
            updateAutoModeStatus(isChecked);
            Toast.makeText(this, isChecked ? "自动模式已开启" : "自动模式已关闭", Toast.LENGTH_SHORT).show();
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
    
    private void updateScreenshotStatus(boolean granted) {
        waitingForPermission = false;
        textScreenshotStatus.setText(granted ? "✓ 已授权" : "✗ 未授权");
        textScreenshotStatus.setTextColor(granted ? 0xFF4CAF50 : 0xFFF44336);
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
        
        if (!PermissionHolderService.hasPermission()) {
            requestScreenshotPermission();
            return;
        }
        
        startAimAssist();
    }
    
    private void startAimAssist() {
        Intent serviceIntent = new Intent(this, FloatingWindowService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(this, serviceIntent);
        } else {
            startService(serviceIntent);
        }
        
        isAimAssistActive = true;
        btnStartAim.setText("停止服务");
        textStatus.setText("运行中");
        Toast.makeText(this, "瞄准辅助已启动", Toast.LENGTH_SHORT).show();
    }
    
    private void checkAllPermissions() {
        boolean accessibilityEnabled = checkAccessibilityPermission();
        boolean overlayEnabled = Settings.canDrawOverlays(this);
        boolean hasScreenshotPermission = PermissionHolderService.hasPermission();
        
        textAccessibilityStatus.setText(accessibilityEnabled ? "✓ 已启用" : "✗ 未启用");
        textAccessibilityStatus.setTextColor(accessibilityEnabled ? 0xFF4CAF50 : 0xFFF44336);
        
        textOverlayStatus.setText(overlayEnabled ? "✓ 已授权" : "✗ 未授权");
        textOverlayStatus.setTextColor(overlayEnabled ? 0xFF4CAF50 : 0xFFF44336);
        
        textScreenshotStatus.setText(hasScreenshotPermission ? "✓ 已授权" : "✗ 未授权");
        textScreenshotStatus.setTextColor(hasScreenshotPermission ? 0xFF4CAF50 : 0xFFF44336);
        
        if (accessibilityEnabled && overlayEnabled && hasScreenshotPermission) {
            textStatus.setText("就绪");
            textStatus.setTextColor(0xFF4CAF50);
        } else if (accessibilityEnabled && overlayEnabled) {
            textStatus.setText("需要截图权限");
            textStatus.setTextColor(0xFFFF9800);
        } else {
            textStatus.setText("需要权限");
            textStatus.setTextColor(0xFFF44336);
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
        status.append(PermissionHolderService.hasPermission() ? "已授权" : "未授权");
        status.append("\n\n• 自动模式：");
        status.append(DeltaAimAccessibilityService.isAutoModeEnabled(this) ? "已开启" : "已关闭");
        
        new AlertDialog.Builder(this)
            .setTitle("DeltaAim 设置")
            .setMessage(status.toString())
            .setPositiveButton("打开无障碍设置", (dialog, which) -> openAccessibilitySettings())
            .setNegativeButton("重新授权截图", (dialog, which) -> {
                PermissionHolderService.clearPermission(this);
                requestScreenshotPermission();
            })
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
    
    private class GameDetectionReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            
            if (DeltaAimAccessibilityService.ACTION_GAME_DETECTED.equals(action)) {
                String packageName = intent.getStringExtra(DeltaAimAccessibilityService.EXTRA_PACKAGE_NAME);
                ErrorLogger.getInstance().logInfo("GameDetection", "Game detected: " + packageName);
                
                if (!isAimAssistActive && checkAccessibilityPermission() && 
                    Settings.canDrawOverlays(MainActivity.this) &&
                    PermissionHolderService.hasPermission()) {
                    try {
                        Intent serviceIntent = new Intent(MainActivity.this, FloatingWindowService.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ContextCompat.startForegroundService(MainActivity.this, serviceIntent);
                        } else {
                            MainActivity.this.startService(serviceIntent);
                        }
                        isAimAssistActive = true;
                    } catch (Exception e) {
                        ErrorLogger.getInstance().logException("GameDetection", "Failed to start service", e);
                    }
                }
            }
        }
    }
}
