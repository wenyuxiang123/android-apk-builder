package com.deltaaim.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.deltaaim.MainActivity;
import com.deltaaim.R;
import com.deltaaim.util.ErrorLogger;

import java.util.concurrent.atomic.AtomicBoolean;

public class FloatingWindowService extends Service {
    
    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "deltaaim_floating";
    private static final String STATUS_CHANNEL_ID = "deltaaim_status";
    private static final int NOTIFICATION_ID = 1002;
    private static final int STATUS_NOTIFICATION_ID = 1004;
    
    private WindowManager windowManager;
    private FrameLayout floatingView;
    private WindowManager.LayoutParams params;
    
    private boolean isAimActive = false;
    private Handler handler;
    private Handler statusHandler;
    
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final long STATUS_CHECK_INTERVAL = 5000; // 5秒检查一次
    
    private NotificationManager notificationManager;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        try {
            ErrorLogger.init(this);
            ErrorLogger.getInstance().logInfo(TAG, "FloatingWindowService created");
        } catch (Exception e) {
            Log.e(TAG, "Failed to init ErrorLogger", e);
        }
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        statusHandler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        createNotificationChannel();
        createStatusNotificationChannel();
        createFloatingWindow();
        startForeground(NOTIFICATION_ID, createNotification());
        
        isRunning.set(true);
        startStatusMonitor();
        showRunningNotification();
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        try {
            isRunning.set(false);
            stopStatusMonitor();
            
            if (floatingView != null && windowManager != null) {
                windowManager.removeView(floatingView);
            }
            
            ErrorLogger.getInstance().logInfo(TAG, "FloatingWindowService destroyed normally");
            showStoppedNotification("服务已停止");
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error in onDestroy", e);
            showErrorNotification("服务异常停止: " + e.getMessage());
        }
        
        super.onDestroy();
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DeltaAim 悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private void createStatusNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                STATUS_CHANNEL_ID,
                "DeltaAim 状态通知",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("DeltaAim 运行状态和异常通知");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeltaAim 运行中")
            .setContentText("瞄准辅助服务已启动")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    private void showRunningNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 运行正常")
            .setContentText("瞄准辅助服务正在后台运行")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    private void showErrorNotification(String errorMessage) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 运行异常")
            .setContentText(errorMessage)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setStyle(new NotificationCompat.BigTextStyle().bigText(errorMessage))
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    private void showStoppedNotification(String reason) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 已停止")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    private void startStatusMonitor() {
        statusHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isRunning.get()) return;
                
                try {
                    checkServiceHealth();
                    statusHandler.postDelayed(this, STATUS_CHECK_INTERVAL);
                } catch (Exception e) {
                    ErrorLogger.getInstance().logException(TAG, "Status monitor error", e);
                    showErrorNotification("状态监控异常: " + e.getMessage());
                }
            }
        }, STATUS_CHECK_INTERVAL);
    }
    
    private void stopStatusMonitor() {
        statusHandler.removeCallbacksAndMessages(null);
    }
    
    private void checkServiceHealth() {
        if (floatingView == null || !floatingView.isShown()) {
            ErrorLogger.getInstance().logWarning(TAG, "Floating window not visible");
        }
        
        if (isAimActive) {
            ErrorLogger.getInstance().logInfo(TAG, "Service health check OK, aim active");
        }
    }
    
    private void createFloatingWindow() {
        try {
            int layoutFlag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
            }
            
            params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            );
            
            params.gravity = Gravity.TOP | Gravity.END;
            params.x = 20;
            params.y = 200;
            
            floatingView = new FrameLayout(this);
            floatingView.setBackgroundColor(Color.parseColor("#E6000000"));
            floatingView.setPadding(16, 16, 16, 16);
            
            TextView statusText = new TextView(this);
            statusText.setText("DeltaAim");
            statusText.setTextColor(Color.WHITE);
            statusText.setTextSize(14);
            floatingView.addView(statusText);
            
            Button toggleBtn = new Button(this);
            toggleBtn.setText("启动");
            toggleBtn.setTextColor(Color.WHITE);
            toggleBtn.setBackgroundColor(Color.parseColor("#0F3460"));
            FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            );
            btnParams.topMargin = 50;
            toggleBtn.setLayoutParams(btnParams);
            
            toggleBtn.setOnClickListener(v -> {
                try {
                    isAimActive = !isAimActive;
                    toggleBtn.setText(isAimActive ? "停止" : "启动");
                    toggleBtn.setBackgroundColor(isAimActive ? Color.parseColor("#D32F2F") : Color.parseColor("#0F3460"));
                    statusText.setText(isAimActive ? "运行中" : "DeltaAim");
                    
                    String status = isAimActive ? "瞄准辅助已激活" : "瞄准辅助已暂停";
                    ErrorLogger.getInstance().logInfo(TAG, status);
                    
                    updateNotification(isAimActive);
                } catch (Exception e) {
                    ErrorLogger.getInstance().logException(TAG, "Toggle button error", e);
                    showErrorNotification("切换状态失败: " + e.getMessage());
                }
            });
            
            floatingView.addView(toggleBtn);
            
            floatingView.setOnTouchListener(new View.OnTouchListener() {
                private int initialX, initialY;
                private float initialTouchX, initialTouchY;
                
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    try {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                initialX = params.x;
                                initialY = params.y;
                                initialTouchX = event.getRawX();
                                initialTouchY = event.getRawY();
                                return true;
                                
                            case MotionEvent.ACTION_MOVE:
                                params.x = initialX + (int) (initialTouchX - event.getRawX());
                                params.y = initialY + (int) (event.getRawY() - initialTouchY);
                                windowManager.updateViewLayout(floatingView, params);
                                return true;
                        }
                    } catch (Exception e) {
                        ErrorLogger.getInstance().logException(TAG, "Touch event error", e);
                    }
                    return false;
                }
            });
            
            windowManager.addView(floatingView, params);
            ErrorLogger.getInstance().logInfo(TAG, "Floating window created successfully");
            
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Failed to create floating window", e);
            showErrorNotification("创建悬浮窗失败: " + e.getMessage());
            stopSelf();
        }
    }
    
    private void updateNotification(boolean isActive) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeltaAim " + (isActive ? "激活中" : "已暂停"))
            .setContentText(isActive ? "瞄准辅助正在运行" : "瞄准辅助已暂停")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setOngoing(true)
            .build();
        
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
    
    public boolean isAimActive() {
        return isAimActive;
    }
    
    public static boolean isServiceRunning() {
        return instance != null && instance.isRunning.get();
    }
    
    private static FloatingWindowService instance;
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        ErrorLogger.getInstance().logWarning(TAG, "System low memory warning");
    }
    
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        ErrorLogger.getInstance().logWarning(TAG, "Trim memory level: " + level);
    }
}
