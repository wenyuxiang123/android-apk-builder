package com.deltaaim;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deltaaim.DeltaAim.R;
import com.deltaaim.collection.DataCollectionActivity;
import com.deltaaim.core.TouchExecutionService;

/**
 * DeltaAim 主界面
 */
public class MainActivity extends AppCompatActivity {

    private Button btnDataCollection;
    private Button btnStartAim;
    private Button btnAccessibilitySettings;
    private TextView textStatus;
    private TextView textPermissionStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        checkPermissions();
    }

    private void initViews() {
        btnDataCollection = findViewById(R.id.btn_data_collection);
        btnStartAim = findViewById(R.id.btn_start_aim);
        btnAccessibilitySettings = findViewById(R.id.btn_accessibility_settings);
        textStatus = findViewById(R.id.text_status);
        textPermissionStatus = findViewById(R.id.text_permission_status);

        btnDataCollection.setOnClickListener(v -> {
            Intent intent = new Intent(this, DataCollectionActivity.class);
            startActivity(intent);
        });

        btnStartAim.setOnClickListener(v -> {
            if (checkAccessibilityPermission()) {
                startAimAssist();
            } else {
                Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_SHORT).show();
                openAccessibilitySettings();
            }
        });

        btnAccessibilitySettings.setOnClickListener(v -> openAccessibilitySettings());
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void checkPermissions() {
        // 检查悬浮窗权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            intent.setData(android.net.Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 1001);
        }
    }

    private void updatePermissionStatus() {
        StringBuilder status = new StringBuilder();
        status.append("权限状态:\n");

        // 悬浮窗权限
        boolean overlay = Settings.canDrawOverlays(this);
        status.append("悬浮窗权限: ").append(overlay ? "✓" : "✗").append("\n");

        // 无障碍服务
        boolean accessibility = checkAccessibilityPermission();
        status.append("无障碍服务: ").append(accessibility ? "✓" : "✗").append("\n");

        // 存储
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean storage = android.os.Environment.isExternalStorageManager();
            status.append("存储权限: ").append(storage ? "✓" : "✗");
        } else {
            status.append("存储权限: ✓");
        }

        textPermissionStatus.setText(status.toString());
    }

    private boolean checkAccessibilityPermission() {
        int enabled = 0;
        try {
            enabled = Settings.Secure.getInt(
                getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException e) {
            e.printStackTrace();
        }

        if (enabled == 1) {
            String service = getPackageName() + "/" + TouchExecutionService.class.getCanonicalName();
            String enabledServices = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            return enabledServices != null && enabledServices.contains(service);
        }
        return false;
    }

    private void openAccessibilitySettings() {
        Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
        startActivity(intent);
    }

    private void startAimAssist() {
        textStatus.setText("辅助瞄准已启动");
        Toast.makeText(this, "辅助瞄准启动中...", Toast.LENGTH_SHORT).show();
        
        // TODO: 启动ScreenCaptureService和瞄准控制器
        // 需要先请求MediaProjection权限
    }
}
