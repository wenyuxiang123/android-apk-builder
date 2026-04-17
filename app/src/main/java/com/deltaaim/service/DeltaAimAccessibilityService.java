package com.deltaaim.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class DeltaAimAccessibilityService extends AccessibilityService {
    
    private static final String TAG = "DeltaAimAccessibility";
    private static DeltaAimAccessibilityService instance;
    
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
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 可以监听窗口变化等事件
    }
    
    @Override
    public void onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted");
    }
    
    public static boolean performClick(float x, float y) {
        if (instance == null) return false;
        
        try {
            Path path = new Path();
            path.moveTo(x, y);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            
            return instance.dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error performing click", e);
            return false;
        }
    }
    
    public static boolean performDrag(float startX, float startY, float endX, float endY, long duration) {
        if (instance == null) return false;
        
        try {
            Path path = new Path();
            path.moveTo(startX, startY);
            path.lineTo(endX, endY);
            
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
            
            return instance.dispatchGesture(builder.build(), null, null);
        } catch (Exception e) {
            Log.e(TAG, "Error performing drag", e);
            return false;
        }
    }
    
    public static boolean isAvailable() {
        return instance != null;
    }
}
