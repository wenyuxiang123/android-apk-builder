package com.deltaaim.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Parcel;
import android.util.Log;

public class ScreenshotManager {
    private static final String TAG = "ScreenshotManager";
    private static final String PREFS_NAME = "DeltaAimScreenshot";
    private static final String KEY_RESULT_CODE = "result_code";
    private static final String KEY_INTENT_DATA = "intent_data";
    private static final String KEY_HAS_PERMISSION = "has_permission";
    
    private static ScreenshotManager instance;
    private SharedPreferences prefs;
    private Context context;
    
    // 缓存授权数据
    private int cachedResultCode = -1;
    private Intent cachedIntent = null;
    private boolean permissionCached = false;
    
    private ScreenshotManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadCachedPermission();
    }
    
    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new ScreenshotManager(context);
        }
    }
    
    public static ScreenshotManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ScreenshotManager not initialized");
        }
        return instance;
    }
    
    private void loadCachedPermission() {
        boolean hasPermission = prefs.getBoolean(KEY_HAS_PERMISSION, false);
        if (hasPermission) {
            cachedResultCode = prefs.getInt(KEY_RESULT_CODE, -1);
            String base64 = prefs.getString(KEY_INTENT_DATA, null);
            
            if (base64 != null && cachedResultCode != -1) {
                try {
                    byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    Parcel parcel = Parcel.obtain();
                    parcel.unmarshall(bytes, 0, bytes.length);
                    parcel.setDataPosition(0);
                    cachedIntent = Intent.CREATOR.createFromParcel(parcel);
                    parcel.recycle();
                    permissionCached = true;
                    Log.i(TAG, "Loaded cached permission: resultCode=" + cachedResultCode);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load cached intent", e);
                    permissionCached = false;
                }
            }
        }
    }
    
    public void savePermission(int resultCode, Intent data) {
        if (data == null) {
            Log.w(TAG, "Cannot save null intent data");
            return;
        }
        
        try {
            // 保存到SharedPreferences
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(KEY_RESULT_CODE, resultCode);
            editor.putBoolean(KEY_HAS_PERMISSION, true);
            
            // 序列化Intent
            Parcel parcel = Parcel.obtain();
            data.writeToParcel(parcel, 0);
            byte[] bytes = parcel.marshall();
            parcel.recycle();
            
            String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);
            editor.putString(KEY_INTENT_DATA, base64);
            
            boolean success = editor.commit();
            
            // 同时缓存到内存
            cachedResultCode = resultCode;
            cachedIntent = data;
            permissionCached = true;
            
            Log.i(TAG, "Permission saved successfully, commit=" + success);
            
            if (ErrorLogger.getInstance() != null) {
                ErrorLogger.getInstance().logInfo(TAG, "Screenshot permission saved: resultCode=" + resultCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save permission", e);
            if (ErrorLogger.getInstance() != null) {
                ErrorLogger.getInstance().logException(TAG, "Failed to save permission", e);
            }
        }
    }
    
    public boolean hasPermission() {
        // 优先检查内存缓存
        if (permissionCached && cachedResultCode != -1 && cachedIntent != null) {
            return true;
        }
        
        // 检查SharedPreferences
        boolean hasPermission = prefs.getBoolean(KEY_HAS_PERMISSION, false);
        if (hasPermission) {
            loadCachedPermission();
            return permissionCached;
        }
        
        return false;
    }
    
    public int getResultCode() {
        if (permissionCached) {
            return cachedResultCode;
        }
        return prefs.getInt(KEY_RESULT_CODE, -1);
    }
    
    public Intent getIntentData() {
        if (permissionCached && cachedIntent != null) {
            return cachedIntent;
        }
        
        loadCachedPermission();
        return cachedIntent;
    }
    
    public void clearPermission() {
        prefs.edit().clear().commit();
        cachedResultCode = -1;
        cachedIntent = null;
        permissionCached = false;
        Log.i(TAG, "Screenshot permission cleared");
    }
}
