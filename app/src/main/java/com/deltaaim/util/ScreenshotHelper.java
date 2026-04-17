package com.deltaaim.util;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.util.Log;

/**
 * 截图权限管理器 - 单例模式
 * 直接在内存中持有MediaProjection，不依赖服务
 */
public class ScreenshotHelper {
    private static final String TAG = "ScreenshotHelper";
    
    private static ScreenshotHelper instance;
    
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private int resultCode = -1;
    private Intent resultData = null;
    private boolean permissionGranted = false;
    
    private ScreenshotHelper(Context context) {
        projectionManager = (MediaProjectionManager) 
            context.getApplicationContext().getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    }
    
    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new ScreenshotHelper(context);
        }
    }
    
    public static ScreenshotHelper getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ScreenshotHelper not initialized");
        }
        return instance;
    }
    
    /**
     * 设置授权数据并创建MediaProjection
     */
    public boolean setPermission(int code, Intent data, Context context) {
        Log.d(TAG, "setPermission: resultCode=" + code + ", data=" + (data != null));
        
        if (code == -1 || data == null) {
            Log.e(TAG, "Invalid permission data");
            return false;
        }
        
        this.resultCode = code;
        this.resultData = data;
        
        try {
            // 立即创建MediaProjection
            if (projectionManager != null) {
                mediaProjection = projectionManager.getMediaProjection(code, data);
                if (mediaProjection != null) {
                    permissionGranted = true;
                    Log.i(TAG, "MediaProjection created successfully");
                    
                    // 注册回调，检测MediaProjection是否被系统回收
                    mediaProjection.registerCallback(new MediaProjection.Callback() {
                        @Override
                        public void onStop() {
                            Log.w(TAG, "MediaProjection stopped by system");
                            permissionGranted = false;
                            mediaProjection = null;
                        }
                    }, null);
                    
                    return true;
                } else {
                    Log.e(TAG, "getMediaProjection returned null");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to create MediaProjection", e);
        }
        
        permissionGranted = false;
        return false;
    }
    
    public boolean hasPermission() {
        return permissionGranted && mediaProjection != null;
    }
    
    public MediaProjection getMediaProjection() {
        return mediaProjection;
    }
    
    public int getResultCode() {
        return resultCode;
    }
    
    public Intent getResultData() {
        return resultData;
    }
    
    public void clearPermission() {
        if (mediaProjection != null) {
            try {
                mediaProjection.stop();
            } catch (Exception e) {
                Log.e(TAG, "Error stopping MediaProjection", e);
            }
            mediaProjection = null;
        }
        permissionGranted = false;
        resultCode = -1;
        resultData = null;
        Log.i(TAG, "Permission cleared");
    }
}
