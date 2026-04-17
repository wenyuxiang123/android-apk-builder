package com.deltaaim.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.os.Parcel;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.deltaaim.util.ErrorLogger;

/**
 * 前台服务，用于持有MediaProjection权限
 * 使用双重机制：内存缓存 + SharedPreferences持久化
 */
public class PermissionHolderService extends Service {
    private static final String TAG = "PermissionHolderService";
    private static final int NOTIFICATION_ID = 1001;
    private static final String PREFS_NAME = "DeltaAimScreenshot";
    private static final String KEY_RESULT_CODE = "result_code";
    private static final String KEY_INTENT_DATA = "intent_data";
    private static final String KEY_HAS_PERMISSION = "has_permission";
    
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    
    private static MediaProjection mediaProjection;
    private static boolean serviceRunning = false;
    private static int cachedResultCode = -1;
    private static Intent cachedIntent = null;
    private static boolean permissionReady = false;
    
    // 权限状态变化回调
    public interface PermissionCallback {
        void onPermissionReady();
    }
    private static PermissionCallback callback;
    
    public static void setCallback(PermissionCallback cb) {
        callback = cb;
    }
    
    public static boolean isServiceRunning() {
        return serviceRunning;
    }
    
    /**
     * 检查是否有有效的截图权限
     * 优先检查内存缓存，其次检查持久化存储
     */
    public static boolean hasPermission() {
        // 检查内存缓存
        if (permissionReady && cachedResultCode != -1 && cachedIntent != null) {
            return true;
        }
        
        // 检查持久化存储（服务可能被杀死后重启）
        if (cachedResultCode == -1) {
            loadFromPrefs();
        }
        
        return permissionReady && cachedResultCode != -1 && cachedIntent != null;
    }
    
    public static int getResultCode() {
        if (cachedResultCode == -1) {
            loadFromPrefs();
        }
        return cachedResultCode;
    }
    
    public static Intent getIntent() {
        if (cachedIntent == null) {
            loadFromPrefs();
        }
        return cachedIntent;
    }
    
    public static MediaProjection getMediaProjection() {
        return mediaProjection;
    }
    
    private static void loadFromPrefs() {
        // 这个方法需要Context，但静态方法无法访问
        // 所以在Application或Service中初始化时加载
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        serviceRunning = true;
        
        // 尝试从SharedPreferences加载权限
        loadPermissionFromPrefs();
        
        Log.i(TAG, "PermissionHolderService created, hasPermission=" + hasPermission());
    }
    
    private void loadPermissionFromPrefs() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean hasPerm = prefs.getBoolean(KEY_HAS_PERMISSION, false);
        
        if (hasPerm) {
            int code = prefs.getInt(KEY_RESULT_CODE, -1);
            String base64 = prefs.getString(KEY_INTENT_DATA, null);
            
            if (code != -1 && base64 != null) {
                try {
                    byte[] bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
                    Parcel parcel = Parcel.obtain();
                    parcel.unmarshall(bytes, 0, bytes.length);
                    parcel.setDataPosition(0);
                    cachedIntent = Intent.CREATOR.createFromParcel(parcel);
                    parcel.recycle();
                    cachedResultCode = code;
                    permissionReady = true;
                    
                    Log.i(TAG, "Loaded permission from prefs: resultCode=" + code);
                    
                    // 尝试重建MediaProjection
                    createMediaProjection();
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load intent from prefs", e);
                    permissionReady = false;
                }
            }
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand called");
        
        // 启动前台通知（必须立即执行）
        startForeground(NOTIFICATION_ID, createNotification());
        
        if (intent != null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            
            Log.d(TAG, "Received: resultCode=" + resultCode + ", resultData=" + (resultData != null));
            
            if (resultCode != -1 && resultData != null) {
                // 保存到内存
                cachedResultCode = resultCode;
                cachedIntent = resultData;
                permissionReady = true;
                
                // 持久化保存
                savePermissionToPrefs(resultCode, resultData);
                
                // 创建MediaProjection
                createMediaProjection();
                
                Log.i(TAG, "Permission saved and ready");
            }
        }
        
        return START_STICKY;
    }
    
    private void savePermissionToPrefs(int resultCode, Intent data) {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
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
            Log.i(TAG, "Permission saved to prefs: " + success);
            
            if (ErrorLogger.getInstance() != null) {
                ErrorLogger.getInstance().logInfo(TAG, "Screenshot permission saved: resultCode=" + resultCode);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to save permission to prefs", e);
            if (ErrorLogger.getInstance() != null) {
                ErrorLogger.getInstance().logException(TAG, "Failed to save permission", e);
            }
        }
    }
    
    private void createMediaProjection() {
        if (cachedResultCode != -1 && cachedIntent != null) {
            try {
                MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                if (mpm != null) {
                    mediaProjection = mpm.getMediaProjection(cachedResultCode, cachedIntent);
                    Log.i(TAG, "MediaProjection created: " + (mediaProjection != null));
                    
                    // 通知权限已就绪
                    if (callback != null) {
                        callback.onPermissionReady();
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create MediaProjection", e);
                ErrorLogger.getInstance().logException(TAG, "Failed to create MediaProjection", e);
            }
        }
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        serviceRunning = false;
        permissionReady = false;
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        Log.i(TAG, "PermissionHolderService destroyed");
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                "permission_holder",
                "截图权限持有服务",
                NotificationManager.IMPORTANCE_MIN
            );
            channel.setDescription("保持截图权限有效");
            channel.setShowBadge(false);
            
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, "permission_holder")
            .setContentTitle("DeltaAim")
            .setContentText("截图权限已授权")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .build();
    }
    
    /**
     * 清除保存的权限（用于重新授权）
     */
    public static void clearPermission(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().commit();
        
        cachedResultCode = -1;
        cachedIntent = null;
        permissionReady = false;
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        Log.i(TAG, "Permission cleared");
    }
}
