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
    
    private static ScreenshotManager instance;
    private SharedPreferences prefs;
    
    private ScreenshotManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new ScreenshotManager(context.getApplicationContext());
        }
    }
    
    public static ScreenshotManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ScreenshotManager not initialized");
        }
        return instance;
    }
    
    public void savePermission(int resultCode, Intent data) {
        if (data == null) {
            Log.w(TAG, "Cannot save null intent data");
            return;
        }
        
        try {
            // 保存resultCode
            prefs.edit().putInt(KEY_RESULT_CODE, resultCode).apply();
            
            // 将Intent序列化保存
            Parcel parcel = Parcel.obtain();
            data.writeToParcel(parcel, 0);
            byte[] bytes = parcel.marshall();
            parcel.recycle();
            
            String base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT);
            prefs.edit().putString(KEY_INTENT_DATA, base64).apply();
            
            Log.i(TAG, "Screenshot permission saved successfully");
            ErrorLogger.getInstance().logInfo(TAG, "Screenshot permission saved");
        } catch (Exception e) {
            Log.e(TAG, "Failed to save permission", e);
            ErrorLogger.getInstance().logException(TAG, "Failed to save permission", e);
        }
    }
    
    public boolean hasPermission() {
        return prefs.contains(KEY_RESULT_CODE) && prefs.contains(KEY_INTENT_DATA);
    }
    
    public int getResultCode() {
        return prefs.getInt(KEY_RESULT_CODE, -1);
    }
    
    public Intent getIntentData() {
        String base64 = prefs.getString(KEY_INTENT_DATA, null);
        if (base64 == null) return null;
        
        try {
            byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            Parcel parcel = Parcel.obtain();
            parcel.unmarshall(bytes, 0, bytes.length);
            parcel.setDataPosition(0);
            Intent intent = Intent.CREATOR.createFromParcel(parcel);
            parcel.recycle();
            return intent;
        } catch (Exception e) {
            Log.e(TAG, "Failed to restore intent", e);
            ErrorLogger.getInstance().logException(TAG, "Failed to restore intent", e);
            return null;
        }
    }
    
    public void clearPermission() {
        prefs.edit().clear().apply();
        Log.i(TAG, "Screenshot permission cleared");
    }
}
