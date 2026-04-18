package com.deltaaim.shizuku;

import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

/**
 * Shizuku UserService implementation
 * Runs with Shizuku permissions to execute privileged commands like screencap
 */
public class ShizukuUserService extends android.app.Service implements IUserService {
    private static final String TAG = "ShizukuUserService";
    private static final String SCREENSHOT_PATH = "/data/local/tmp/deltaaim_screenshot.png";
    private static final String SCREENSHOT_BACKUP = "/data/local/tmp/deltaaim_screenshot_backup.png";
    
    private static final int SCREEN_WIDTH = 1080;
    private static final int SCREEN_HEIGHT = 2400;
    
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ShizukuUserService created");
    }
    
    @Override
    public int onStartCommand(android.content.Intent intent, int flags, int startId) {
        Log.d(TAG, "ShizukuUserService started");
        return START_STICKY;
    }
    
    @Override
    public android.os.IBinder onBind(android.content.Intent intent) {
        return new IUserService.Stub() {
            @Override
            public String captureScreen() {
                return ShizukuUserService.this.captureScreen();
            }
            
            @Override
            public boolean isReady() {
                return true;
            }
            
            @Override
            public String getVersion() {
                return "1.0.0";
            }
        };
    }
    
    @Override
    public String captureScreen() {
        Log.d(TAG, "Capturing screen via Shizuku...");
        
        try {
            // Backup previous screenshot if exists
            File current = new File(SCREENSHOT_PATH);
            if (current.exists()) {
                current.renameTo(new File(SCREENSHOT_BACKUP));
            }
            
            // Execute screencap command
            Process process = Runtime.getRuntime().exec("screencap -p " + SCREENSHOT_PATH);
            int result = process.waitFor();
            
            if (result == 0 && new File(SCREENSHOT_PATH).exists()) {
                Log.d(TAG, "Screenshot captured successfully: " + SCREENSHOT_PATH);
                return SCREENSHOT_PATH;
            } else {
                Log.e(TAG, "Failed to capture screenshot, exit code: " + result);
                // Try to return backup if exists
                File backup = new File(SCREENSHOT_BACKUP);
                if (backup.exists()) {
                    backup.renameTo(new File(SCREENSHOT_PATH));
                    return SCREENSHOT_PATH;
                }
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error capturing screen", e);
            return null;
        }
    }
    
    @Override
    public boolean isReady() {
        return true;
    }
    
    @Override
    public String getVersion() {
        return "1.0.0-Shizuku";
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "ShizukuUserService destroyed");
    }
}
