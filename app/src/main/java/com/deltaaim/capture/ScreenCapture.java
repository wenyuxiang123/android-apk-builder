package com.deltaaim.capture;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.File;

/**
 * Screen Capture Manager using Shizuku
 * Captures screenshots via Shizuku UserService or direct screencap
 */
public class ScreenCapture {
    private static final String TAG = "ScreenCapture";
    private static final String SCREENSHOT_PATH = "/data/local/tmp/deltaaim_screenshot.png";
    
    private static ScreenCapture instance;
    private boolean initialized = false;
    
    private ScreenCapture() {
        ensureScreenshotDir();
        initialized = true;
    }
    
    public static synchronized ScreenCapture getInstance() {
        if (instance == null) {
            instance = new ScreenCapture();
        }
        return instance;
    }
    
    private void ensureScreenshotDir() {
        try {
            File screenshotDir = new File("/data/local/tmp");
            if (!screenshotDir.exists()) {
                screenshotDir.mkdirs();
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create screenshot directory", e);
        }
    }
    
    /**
     * Capture screenshot using screencap command
     * @return Bitmap of the screenshot, or null if failed
     */
    public Bitmap capture() {
        Log.d(TAG, "Starting screenshot capture...");
        
        try {
            // Try Shizuku first if available
            if (isShizukuAvailable()) {
                Bitmap result = captureViaShizuku();
                if (result != null) {
                    return result;
                }
            }
            
            // Fallback: direct screencap
            return captureDirect();
            
        } catch (Exception e) {
            Log.e(TAG, "Screenshot capture failed", e);
            return null;
        }
    }
    
    /**
     * Check if Shizuku is available
     */
    private boolean isShizukuAvailable() {
        try {
            return rikka.shizuku.Shizuku.pingBinder();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Capture screenshot via Shizuku
     */
    private Bitmap captureViaShizuku() {
        try {
            // Execute screencap via Shizuku process
            Process process = Runtime.getRuntime().exec("shizuku exec screencap -p " + SCREENSHOT_PATH);
            int result = process.waitFor();
            
            if (result == 0 && new File(SCREENSHOT_PATH).exists()) {
                Log.d(TAG, "Screenshot captured via Shizuku");
                return loadBitmap(SCREENSHOT_PATH);
            }
            
            Log.w(TAG, "Shizuku capture failed with code: " + result);
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error capturing via Shizuku", e);
            return null;
        }
    }
    
    /**
     * Direct screencap fallback
     */
    private Bitmap captureDirect() {
        try {
            Process process = Runtime.getRuntime().exec("screencap -p " + SCREENSHOT_PATH);
            int result = process.waitFor();
            
            if (result == 0) {
                return loadBitmap(SCREENSHOT_PATH);
            }
            
            Log.e(TAG, "Direct screencap failed with code: " + result);
            return null;
            
        } catch (Exception e) {
            Log.e(TAG, "Error in direct capture", e);
            return null;
        }
    }
    
    /**
     * Load bitmap from file path
     */
    private Bitmap loadBitmap(String path) {
        try {
            File file = new File(path);
            if (!file.exists()) {
                Log.e(TAG, "Screenshot file not found: " + path);
                return null;
            }
            
            // Load with ARGB_8888 for better quality
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            
            return BitmapFactory.decodeFile(path, options);
            
        } catch (Exception e) {
            Log.e(TAG, "Error loading bitmap from: " + path, e);
            return null;
        }
    }
    
    /**
     * Get pixel color at specific coordinates from current screenshot
     */
    public int getPixelColor(int x, int y) {
        Bitmap bitmap = capture();
        if (bitmap != null && x >= 0 && x < bitmap.getWidth() && y >= 0 && y < bitmap.getHeight()) {
            int color = bitmap.getPixel(x, y);
            bitmap.recycle();
            return color;
        }
        return 0;
    }
    
    /**
     * Check if Shizuku is available
     */
    public boolean isShizukuAvailable() {
        try {
            return rikka.shizuku.Shizuku.pingBinder();
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Check if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }
    
    /**
     * Release resources
     */
    public void release() {
        // Clean up if needed
    }
}
