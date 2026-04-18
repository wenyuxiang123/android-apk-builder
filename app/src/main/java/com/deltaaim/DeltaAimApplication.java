package com.deltaaim;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

import rikka.shizuku.Shizuku;
import rikka.shizuku.ShizukuClientWatcher;

/**
 * DeltaAim Application class
 * Initializes Shizuku and sets up the environment
 */
public class DeltaAimApplication extends Application {
    private static final String TAG = "DeltaAimApplication";
    
    private static DeltaAimApplication instance;
    private boolean shizukuInitialized = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        
        Log.d(TAG, "DeltaAim Application starting...");
        
        // Initialize Shizuku
        initShizuku();
        
        // Ensure screenshot directory exists
        ensureScreenshotDir();
    }
    
    public static DeltaAimApplication getInstance() {
        return instance;
    }
    
    private void initShizuku() {
        try {
            // Check if Shizuku is available
            if (Shizuku.pingBinder()) {
                Log.d(TAG, "Shizuku is available");
                shizukuInitialized = true;
                
                // Add permission request listener
                Shizuku.addRequestPermissionResultListener((requestCode, grantResult) -> {
                    Log.d(TAG, "Permission request result: " + grantResult);
                });
            } else {
                Log.w(TAG, "Shizuku is not running. Please start Shizuku.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize Shizuku", e);
        }
    }
    
    private void ensureScreenshotDir() {
        File screenshotDir = new File("/data/local/tmp");
        if (!screenshotDir.exists()) {
            screenshotDir.mkdirs();
        }
    }
    
    public boolean isShizukuInitialized() {
        return shizukuInitialized && Shizuku.pingBinder();
    }
}
