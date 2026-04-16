package com.deltaaim;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deltaaim.DeltaAim.R;
import com.deltaaim.collection.DataCollectionActivity;

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
        
        try {
            setContentView(R.layout.activity_main);
            initViews();
            updatePermissionStatus();
        } catch (Exception e) {
            e.printStackTrace();
            // 如果布局加载失败，使用简单布局
            showErrorLayout(e.getMessage());
        }
    }

    private void initViews() {
        btnDataCollection = findViewById(R.id.btn_data_collection);
        btnStartAim = findViewById(R.id.btn_start_aim);
        btnAccessibilitySettings = findViewById(R.id.btn_accessibility_settings);
        textStatus = findViewById(R.id.text_status);
        textPermissionStatus = findViewById(R.id.text_permission_status);

        btnDataCollection.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(this, DataCollectionActivity.class);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        btnStartAim.setOnClickListener(v -> {
            Toast.makeText(this, R.string.aim_started, Toast.LENGTH_SHORT).show();
        });

        btnAccessibilitySettings.setOnClickListener(v -> {
            try {
                Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            updatePermissionStatus();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void updatePermissionStatus() {
        StringBuilder status = new StringBuilder();
        status.append(getString(R.string.permission_status)).append("\n");

        boolean overlay = Settings.canDrawOverlays(this);
        status.append(getString(R.string.overlay_permission)).append(": ").append(overlay ? "✓" : "✗").append("\n");

        status.append(getString(R.string.accessibility_service)).append(": ").append("✗").append("\n");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            boolean storage = android.os.Environment.isExternalStorageManager();
            status.append(getString(R.string.storage_permission)).append(": ").append(storage ? "✓" : "✗");
        } else {
            status.append(getString(R.string.storage_permission)).append(": ✓");
        }

        textPermissionStatus.setText(status.toString());
    }

    private void showErrorLayout(String error) {
        TextView errorText = new TextView(this);
        errorText.setText("Error: " + error);
        errorText.setPadding(32, 32, 32, 32);
        setContentView(errorText);
    }
}
