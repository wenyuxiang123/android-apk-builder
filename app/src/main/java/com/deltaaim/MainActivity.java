package com.deltaaim;

import android.Manifest;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.deltaaim.capture.ScreenCapture;
import com.deltaaim.detector.ColorDetector;
import com.deltaaim.service.AimAccessibilityService;

import java.util.List;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuBinderWrapper;
import rikka.shizuku.SystemServiceHelper;

/**
 * MainActivity for DeltaAim
 * Integrates Shizuku, ScreenCapture, ColorDetection, and AccessibilityService
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "DeltaAim";
    private static final int PERMISSION_REQUEST_CODE = 1001;
    
    // Views
    private TextView statusText;
    private TextView targetInfoText;
    private Button startButton;
    private Button stopButton;
    private Button captureButton;
    private ImageView previewImage;
    
    // Components
    private ScreenCapture screenCapture;
    private ColorDetector colorDetector;
    private Handler handler;
    
    // State
    private boolean isRunning = false;
    private boolean shizukuGranted = false;
    
    // Aim loop
    private Runnable aimLoop;
    private int aimIntervalMs = 100;
    
    // Permission launcher
    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allGranted = true;
                for (Boolean granted : result.values()) {
                    if (!granted) {
                        allGranted = false;
                        break;
                    }
                }
                if (allGranted) {
                    onPermissionsGranted();
                } else {
                    Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show();
                }
            });
    
    // Shizuku permission launcher
    private final ActivityResultLauncher<Intent> shizukuPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    shizukuGranted = true;
                    onShizukuGranted();
                } else {
                    Toast.makeText(this, "Shizuku permission denied", Toast.LENGTH_SHORT).show();
                }
            });
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        
        initViews();
        initComponents();
        checkPermissions();
        
        // Setup Shizuku listeners
        setupShizuku();
    }
    
    private void initViews() {
        statusText = findViewById(R.id.statusText);
        targetInfoText = findViewById(R.id.targetInfoText);
        startButton = findViewById(R.id.startButton);
        stopButton = findViewById(R.id.stopButton);
        captureButton = findViewById(R.id.captureButton);
        previewImage = findViewById(R.id.previewImage);
        
        startButton.setOnClickListener(v -> startAim());
        stopButton.setOnClickListener(v -> stopAim());
        captureButton.setOnClickListener(v -> captureAndPreview());
        
        stopButton.setEnabled(false);
    }
    
    private void initComponents() {
        handler = new Handler(Looper.getMainLooper());
        screenCapture = ScreenCapture.getInstance();
        colorDetector = new ColorDetector();
        
        // Set target color (red enemy for example)
        colorDetector.setTargetColor(255, 50, 50);
        colorDetector.setColorThreshold(60);
        
        updateStatus("Initializing...");
    }
    
    private void checkPermissions() {
        String[] permissions = {
                Manifest.permission.INTERNET,
                Manifest.permission.ACCESS_NETWORK_STATE
        };
        
        permissionLauncher.launch(permissions);
    }
    
    private void onPermissionsGranted() {
        // Check accessibility service
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable Accessibility Service", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
        
        // Check Shizuku
        checkShizuku();
    }
    
    private void setupShizuku() {
        // Listen for Shizuku events
        Shizuku.addOnBinderReceivedListener(stub -> {
            Log.d(TAG, "Shizuku binder received");
            runOnUiThread(() -> {
                updateStatus("Shizuku connected");
                checkShizukuPermission();
            });
        });
        
        Shizuku.addOnBinderDeadListener(() -> {
            Log.d(TAG, "Shizuku binder dead");
            runOnUiThread(() -> updateStatus("Shizuku disconnected"));
        });
    }
    
    private void checkShizuku() {
        if (Shizuku.pingBinder()) {
            updateStatus("Shizuku available");
            checkShizukuPermission();
        } else {
            updateStatus("Shizuku not running - Please start Shizuku app");
        }
    }
    
    private void checkShizukuPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.MANAGE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    shizukuGranted = true;
                    onShizukuGranted();
                } else {
                    // Request Shizuku permission
                    try {
                        Intent intent = Shizuku.getPermissionIntent();
                        shizukuPermissionLauncher.launch(intent);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to request Shizuku permission", e);
                    }
                }
            } else {
                shizukuGranted = true;
                onShizukuGranted();
            }
        }
    }
    
    private void onShizukuGranted() {
        Log.d(TAG, "Shizuku permission granted");
        updateStatus("Ready - Shizuku connected");
    }
    
    private boolean isAccessibilityServiceEnabled() {
        AccessibilityServiceInfo info = getAccessibilityServiceInfo();
        return info != null;
    }
    
    private void updateStatus(String status) {
        if (statusText != null) {
            statusText.setText(status);
        }
        Log.d(TAG, status);
    }
    
    private void updateTargetInfo(String info) {
        if (targetInfoText != null) {
            targetInfoText.setText(info);
        }
    }
    
    private void startAim() {
        if (isRunning) return;
        
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(this, "Please enable Accessibility Service first", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (screenCapture == null) {
            screenCapture = ScreenCapture.getInstance();
        }
        
        isRunning = true;
        startButton.setEnabled(false);
        stopButton.setEnabled(true);
        updateStatus("Aim active");
        
        // Start aim loop
        aimLoop = new Runnable() {
            @Override
            public void run() {
                if (!isRunning) return;
                
                try {
                    performAimStep();
                } catch (Exception e) {
                    Log.e(TAG, "Error in aim loop", e);
                }
                
                handler.postDelayed(this, aimIntervalMs);
            }
        };
        
        handler.post(aimLoop);
    }
    
    private void stopAim() {
        isRunning = false;
        startButton.setEnabled(true);
        stopButton.setEnabled(false);
        updateStatus("Aim stopped");
        
        if (aimLoop != null) {
            handler.removeCallbacks(aimLoop);
        }
    }
    
    private void performAimStep() {
        // Capture screen
        Bitmap screenshot = screenCapture.capture();
        if (screenshot == null) {
            updateTargetInfo("Capture failed");
            return;
        }
        
        // Find target
        ColorDetector.TargetPoint target = colorDetector.findTarget(screenshot);
        
        if (target != null) {
            int targetX = target.x;
            int targetY = target.y;
            
            updateTargetInfo("Target: (" + targetX + ", " + targetY + ")");
            
            // Calculate aim offset (aim slightly below center of target)
            int aimX = targetX;
            int aimY = targetY + 50;
            
            // Perform aim action using AccessibilityService
            AimAccessibilityService service = AimAccessibilityService.getInstance();
            if (service != null) {
                service.performClick(aimX, aimY);
            }
        } else {
            updateTargetInfo("No target found");
        }
        
        // Update preview
        if (previewImage != null) {
            previewImage.setImageBitmap(screenshot);
        }
        
        // Recycle bitmap to save memory
        screenshot.recycle();
    }
    
    private void captureAndPreview() {
        new Thread(() -> {
            Bitmap screenshot = screenCapture.capture();
            if (screenshot != null) {
                runOnUiThread(() -> {
                    if (previewImage != null) {
                        previewImage.setImageBitmap(screenshot);
                    }
                    
                    // Find target in preview
                    ColorDetector.TargetPoint target = colorDetector.findTarget(screenshot);
                    if (target != null) {
                        updateTargetInfo("Target found: (" + target.x + ", " + target.y + ")");
                    } else {
                        updateTargetInfo("No target in current frame");
                    }
                });
            } else {
                runOnUiThread(() -> {
                    updateTargetInfo("Capture failed");
                });
            }
        }).start();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        checkShizuku();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAim();
        
        if (screenCapture != null) {
            screenCapture.release();
        }
    }
}
