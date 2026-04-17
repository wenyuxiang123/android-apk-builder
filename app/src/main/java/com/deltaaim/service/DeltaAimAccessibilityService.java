package com.deltaaim.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

public class DeltaAimAccessibilityService extends AccessibilityService {
    
    private static final String TAG = "DeltaAimAccessibility";
    private static final String PREFS_NAME = "DeltaAimPrefs";
    private static final String KEY_AUTO_MODE = "auto_mode";
    
    // 游戏包名列表
    private static final String[] GAME_PACKAGES = {
        "com.tencent.tmgp.dfm",  // 三角洲行动 国服
        "com.proxima.dfm"        // 三角洲行动 国际服
    };
    
    private static DeltaAimAccessibilityService instance;
    private String lastForegroundPackage = "";
    
    public static final String ACTION_GAME_DETECTED = "com.deltaaim.GAME_DETECTED";
    public static final String ACTION_GAME_EXITED = "com.deltaaim.GAME_EXITED";
    public static final String EXTRA_PACKAGE_NAME = "package_name";
    
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
    
    public static boolean isAutoModeEnabled(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(KEY_AUTO_MODE, false);
    }
    
    public static void setAutoModeEnabled(Context context, boolean enabled) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putBoolean(KEY_AUTO_MODE, enabled).apply();
        Log.d(TAG, "Auto mode set to: " + enabled);
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
        // 监听窗口状态变化事件
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            CharSequence packageNameSeq = event.getPackageName();
            if (packageNameSeq != null) {
                String packageName = packageNameSeq.toString();
                
                // 忽略自身应用的事件
                if (packageName.equals(getPackageName())) {
                    return;
                }
                
                // 检测是否为游戏包名
                if (!packageName.equals(lastForegroundPackage)) {
                    lastForegroundPackage = packageName;
                    
                    if (isTargetGame(packageName)) {
                        onGameDetected(packageName);
                    } else if (isTargetGame(lastForegroundPackage)) {
                        // 从游戏切换到其他应用
                        onGameExited();
                    }
                }
            }
        }
    }
    
    private boolean isTargetGame(String packageName) {
        for (String gamePackage : GAME_PACKAGES) {
            if (gamePackage.equals(packageName)) {
                return true;
            }
        }
        return false;
    }
    
    private void onGameDetected(String packageName) {
        Log.d(TAG, "Game detected: " + packageName);
        
        // 检查是否启用自动模式
        if (!isAutoModeEnabled(this)) {
            Log.d(TAG, "Auto mode disabled, skipping auto-start");
            return;
        }
        
        // 发送广播通知游戏已启动
        Intent intent = new Intent(ACTION_GAME_DETECTED);
        intent.putExtra(EXTRA_PACKAGE_NAME, packageName);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        
        Log.d(TAG, "Game detected broadcast sent");
    }
    
    private void onGameExited() {
        Log.d(TAG, "Game exited");
        
        // 发送广播通知游戏已退出
        Intent intent = new Intent(ACTION_GAME_EXITED);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
        
        Log.d(TAG, "Game exited broadcast sent");
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
