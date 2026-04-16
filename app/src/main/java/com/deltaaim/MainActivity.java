package com.deltaaim;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * DeltaAim 主界面
 */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        TextView status = findViewById(R.id.text_status);
        status.setText(R.string.ready);
        
        Button btnData = findViewById(R.id.btn_data_collection);
        Button btnAim = findViewById(R.id.btn_start_aim);
        Button btnSettings = findViewById(R.id.btn_accessibility_settings);
        
        btnData.setOnClickListener(v -> 
            Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show());
        
        btnAim.setOnClickListener(v -> 
            Toast.makeText(this, R.string.aim_started, Toast.LENGTH_SHORT).show());
        
        btnSettings.setOnClickListener(v -> 
            Toast.makeText(this, "功能开发中", Toast.LENGTH_SHORT).show());
    }
}
