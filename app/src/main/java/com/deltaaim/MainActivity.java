package com.deltaaim;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.deltaaim.capture.ScreenCapture;

public class MainActivity extends AppCompatActivity {
    
    private static final String TAG = "MainActivity";
    private static final int REQUEST_MEDIA_PROJECTION = 1;
    
    private TextView textStatus;
    private TextView textInfo;
    private ImageView imagePreview;
    private Button btnRequestPermission;
    private Button btnStartCapture;
    private Button btnStopCapture;
    private Button btnTestCapture;
    
    private Handler handler = new Handler(Looper.getMainLooper());
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 初始化截屏管理器
        ScreenCapture.init(this);
        
        initViews();
        setupListeners();
        updateStatus();
    }
    
    private void initViews() {
        textStatus = findViewById(R.id.text_status);
        textInfo = findViewById(R.id.text_info);
        imagePreview = findViewById(R.id.image_preview);
        btnRequestPermission = findViewById(R.id.btn_request_permission);
        btnStartCapture = findViewById(R.id.btn_start_capture);
        btnStopCapture = findViewById(R.id.btn_stop_capture);
        btnTestCapture = findViewById(R.id.btn_test_capture);
    }
    
    private void setupListeners() {
        btnRequestPermission.setOnClickListener(v -> requestScreenCapturePermission());
        btnStartCapture.setOnClickListener(v -> startCapture());
        btnStopCapture.setOnClickListener(v -> stopCapture());
        btnTestCapture.setOnClickListener(v -> testCapture());
    }
    
    private void requestScreenCapturePermission() {
        MediaProjectionManager mpm = (MediaProjectionManager) 
            getSystemService(MEDIA_PROJECTION_SERVICE);
        
        if (mpm != null) {
            startActivityForResult(mpm.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION);
            Toast.makeText(this, "请点击「立即开始」授权", Toast.LENGTH_LONG).show();
        }
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_MEDIA_PROJECTION) {
            Log.d(TAG, "onActivityResult: resultCode=" + resultCode + ", data=" + data);
            
            if (resultCode == Activity.RESULT_OK && data != null) {
                // 立即创建MediaProjection
                boolean success = ScreenCapture.getInstance().createProjection(resultCode, data);
                
                if (success) {
                    textStatus.setText("权限状态：已授权 ✓");
                    textStatus.setTextColor(0xFF4CAF50);
                    Toast.makeText(this, "截图权限授权成功！", Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "MediaProjection created successfully");
                } else {
                    textStatus.setText("权限状态：创建失败 ✗");
                    textStatus.setTextColor(0xFFF44336);
                    Toast.makeText(this, "MediaProjection创建失败", Toast.LENGTH_SHORT).show();
                }
            } else {
                textStatus.setText("权限状态：未授权 ✗");
                textStatus.setTextColor(0xFFF44336);
                Toast.makeText(this, "授权被拒绝", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void startCapture() {
        if (!ScreenCapture.getInstance().hasPermission()) {
            Toast.makeText(this, "请先授权截图权限", Toast.LENGTH_SHORT).show();
            return;
        }
        
        boolean success = ScreenCapture.getInstance().startCapture();
        
        if (success) {
            textInfo.setText("截屏状态：正在截屏中...\n分辨率：" + 
                ScreenCapture.getInstance().getScreenWidth() + "x" + 
                ScreenCapture.getInstance().getScreenHeight());
            Toast.makeText(this, "开始截屏", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "启动截屏失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void stopCapture() {
        ScreenCapture.getInstance().stopCapture();
        textInfo.setText("截屏状态：已停止");
        imagePreview.setImageBitmap(null);
        Toast.makeText(this, "已停止截屏", Toast.LENGTH_SHORT).show();
    }
    
    private void testCapture() {
        Bitmap bitmap = ScreenCapture.getInstance().getLatestBitmap();
        
        if (bitmap != null) {
            imagePreview.setImageBitmap(bitmap);
            textInfo.setText("截屏状态：成功获取截图\n" +
                "分辨率：" + bitmap.getWidth() + "x" + bitmap.getHeight());
            Toast.makeText(this, "截图成功！", Toast.LENGTH_SHORT).show();
        } else {
            textInfo.setText("截屏状态：未获取到截图\n请先启动截屏并等待几秒");
            Toast.makeText(this, "暂无截图", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void updateStatus() {
        if (ScreenCapture.getInstance().hasPermission()) {
            textStatus.setText("权限状态：已授权 ✓");
            textStatus.setTextColor(0xFF4CAF50);
        } else {
            textStatus.setText("权限状态：未授权 ✗");
            textStatus.setTextColor(0xFFF44336);
        }
        textInfo.setText("截屏状态：未启动");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        updateStatus();
    }
}
