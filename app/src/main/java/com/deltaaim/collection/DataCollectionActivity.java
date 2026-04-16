package com.deltaaim.collection;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deltaaim.DeltaAim.R;

/**
 * 数据采集控制界面
 */
public class DataCollectionActivity extends AppCompatActivity {

    private Button btnStartCapture;
    private Button btnStopCapture;
    private Button btnSingleCapture;
    private TextView textStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_data_collection);
            initViews();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void initViews() {
        btnStartCapture = findViewById(R.id.btn_start_capture);
        btnStopCapture = findViewById(R.id.btn_stop_capture);
        btnSingleCapture = findViewById(R.id.btn_single_capture);
        textStatus = findViewById(R.id.text_status);

        btnStartCapture.setOnClickListener(v -> {
            textStatus.setText("正在请求屏幕录制权限...");
            Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show();
        });

        btnStopCapture.setOnClickListener(v -> {
            textStatus.setText("已停止");
        });

        btnSingleCapture.setOnClickListener(v -> {
            Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show();
        });
    }
}
