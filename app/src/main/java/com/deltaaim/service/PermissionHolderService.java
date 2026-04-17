package com.deltaaim.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.core.app.NotificationCompat;

import com.deltaaim.MainActivity;
import com.deltaaim.R;
import com.deltaaim.util.ErrorLogger;

/**
 * 轻量级前台服务，用于持有MediaProjection权限
 * 只要这个服务在运行，截图权限就不会丢失
 */
public class PermissionHolderService extends Service {
    private static final String TAG = "PermissionHolderService";
    private static final int NOTIFICATION_ID = 1001;
    public static final String EXTRA_RESULT_CODE = "result_code";
    public static final String EXTRA_RESULT_DATA = "result_data";
    
    private static MediaProjection mediaProjection;
    private static boolean isRunning = false;
    private static int cachedResultCode = -1;
    private static Intent cachedIntent = null;
    
    public static boolean isRunning() {
        return isRunning;
    }
    
    public static boolean hasPermission() {
        return isRunning && cachedResultCode != -1 && cachedIntent != null;
    }
    
    public static int getResultCode() {
        return cachedResultCode;
    }
    
    public static Intent getIntent() {
        return cachedIntent;
    }
    
    public static MediaProjection getMediaProjection() {
        return mediaProjection;
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        isRunning = true;
        Log.i(TAG, "PermissionHolderService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1);
            Intent resultData = intent.getParcelableExtra(EXTRA_RESULT_DATA);
            
            if (resultCode != -1 && resultData != null) {
                cachedResultCode = resultCode;
                cachedIntent = resultData;
                
                // 创建MediaProjection来持有权限
                try {
                    MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
                    mediaProjection = mpm.getMediaProjection(resultCode, resultData);
                    Log.i(TAG, "MediaProjection created successfully");
                } catch (Exception e) {
                    Log.e(TAG, "Failed to create MediaProjection", e);
                }
            }
        }
        
        // 启动前台通知
        startForeground(NOTIFICATION_ID, createNotification());
        Log.i(TAG, "PermissionHolderService started as foreground");
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
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
}
