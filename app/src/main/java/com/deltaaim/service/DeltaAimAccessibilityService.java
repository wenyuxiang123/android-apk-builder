package com.deltaaim.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.deltaaim.util.GestureSimulator;

public class DeltaAimAccessibilityService extends AccessibilityService {
    
    private static final String TAG = "DeltaAimAccessibility";
    private static DeltaAimAccessibilityService instance;
    private GestureSimulator gestureSimulator;
    
    public static boolean isServiceEnabled(Context context) {
        try {
            String enabledServices = android.provider.Settings.Secure.getString(
                context.getContentResolver(),
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (enabledServices == null) return false;
            String packageName = context.getPackageName();
            String serviceName = packageName + "/" + DeltaAimAccessibilityService.class.getName();
            return enabledServices.contains(serviceName);
        } catch (Exception e) {
            Log.e(TAG, "Error checking service status", e);
            return false;
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        gestureSimulator = new GestureSimulator(this);
        Log.d(TAG, "Accessibility Service Created");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        Log.d(TAG, "Accessibility Service Destroyed");
    }
    
    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d(TAG, "Accessibility Service Connected");
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted");
    }
    
    public static boolean performClick(float x, float y) {
        if (instance != null && instance.gestureSimulator != null) {
            return instance.gestureSimulator.performClick(x, y);
        }
        return false;
    }
    
    public static boolean performSwipe(float startX, float startY, float endX, float endY, long duration) {
        if (instance != null && instance.gestureSimulator != null) {
            return instance.gestureSimulator.performSwipe(startX, startY, endX, endY, duration);
        }
        return false;
    }
    
    public static boolean performDrag(float startX, float startY, float endX, float endY, long duration) {
        if (instance != null && instance.gestureSimulator != null) {
            return instance.gestureSimulator.performDrag(startX, startY, endX, endY, duration);
        }
        return false;
    }
    
    public static boolean performAim(float targetX, float targetY) {
        if (instance != null && instance.gestureSimulator != null) {
            return instance.gestureSimulator.performAim(targetX, targetY);
        }
        return false;
    }
    
    public static boolean isAvailable() {
        return instance != null;
    }
}
