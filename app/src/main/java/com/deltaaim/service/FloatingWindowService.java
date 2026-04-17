package com.deltaaim.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class FloatingWindowService extends Service {
    
    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "deltaaim_floating";
    private static final String STATUS_CHANNEL_ID = "deltaaim_status";
    private static final int NOTIFICATION_ID = 1002;
    private static final int STATUS_NOTIFICATION_ID = 1004;
    
    private WindowManager windowManager;
    private FrameLayout floatingView;
    private OverlayView overlayView;
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams overlayParams;
    
    private boolean isAimActive = false;
    private Handler handler;
    private Handler statusHandler;
    
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private static final long STATUS_CHECK_INTERVAL = 5000;
    
    private NotificationManager notificationManager;
    
    private static FloatingWindowService instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        instance = this;
        
        ErrorLogger.init(this);
        ErrorLogger.getInstance().logInfo(TAG, "FloatingWindowService created");
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        statusHandler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        createNotificationChannel();
        createStatusNotificationChannel();
        createFloatingWindow();
        createOverlayView();
        startForeground(NOTIFICATION_ID, createNotification());
        
        isRunning.set(true);
        startStatusMonitor();
        showRunningNotification();
        
        // 设置扫描回调
        ScanService.setDetectionCallback(targets -> {
            if (overlayView != null && isAimActive) {
                overlayView.updateTargets(targets);
            }
        });
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
            
            // 停止扫描服务
            Intent scanIntent = new Intent(this, ScanService.class);
            stopService(scanIntent);
            
            if (overlayView != null && windowManager != null) {
                windowManager.removeView(overlayView);
            }
            
            if (floatingView != null && windowManager != null) {
                windowManager.removeView(floatingView);
            }
            
            ErrorLogger.getInstance().logInfo(TAG, "FloatingWindowService destroyed normally");
            showStoppedNotification("服务已停止");
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error in onDestroy", e);
            showErrorNotification("服务异常停止: " + e.getMessage());
        }
        
        instance = null;
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
            .setContentText(isAimActive ? "扫描激活中" : "等待启动扫描")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    private void showRunningNotification() {
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 运行正常")
            .setContentText("瞄准辅助服务正在后台运行")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setAutoCancel(true)
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    private void showErrorNotification(String errorMessage) {
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 运行异常")
            .setContentText(errorMessage)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(false)
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    private void showStoppedNotification(String reason) {
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 已停止")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
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
            toggleBtn.setText("启动扫描");
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
                    
                    if (isAimActive) {
                        toggleBtn.setText("停止扫描");
                        toggleBtn.setBackgroundColor(Color.parseColor("#D32F2F"));
                        statusText.setText("扫描中...");
                        
                        // 启动扫描服务
                        Intent scanIntent = new Intent(FloatingWindowService.this, ScanService.class);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(scanIntent);
                        } else {
                            startService(scanIntent);
                        }
                        
                        ScanService.resume();
                        
                        if (overlayView != null) {
                            overlayView.setVisibility(View.VISIBLE);
                        }
                        
                        ErrorLogger.getInstance().logInfo(TAG, "Scan started");
                    } else {
                        toggleBtn.setText("启动扫描");
                        toggleBtn.setBackgroundColor(Color.parseColor("#0F3460"));
                        statusText.setText("DeltaAim");
                        
                        ScanService.pause();
                        
                        if (overlayView != null) {
                            overlayView.setVisibility(View.GONE);
                            overlayView.clearTargets();
                        }
                        
                        ErrorLogger.getInstance().logInfo(TAG, "Scan paused");
                    }
                    
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
    
    private void createOverlayView() {
        try {
            int layoutFlag;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
            }
            
            overlayParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            );
            
            overlayParams.gravity = Gravity.TOP | Gravity.START;
            
            overlayView = new OverlayView(this);
            overlayView.setVisibility(View.GONE);
            
            windowManager.addView(overlayView, overlayParams);
            
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Failed to create overlay view", e);
        }
    }
    
    private void updateNotification(boolean isActive) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeltaAim " + (isActive ? "扫描中" : "已暂停"))
            .setContentText(isActive ? "正在实时扫描目标" : "扫描已暂停")
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
    
    // 覆盖层视图，用于绘制目标框
    private static class OverlayView extends View {
        private List<ScanService.TargetRect> targets;
        private Paint paint;
        private Paint textPaint;
        
        public OverlayView(Context context) {
            super(context);
            
            paint = new Paint();
            paint.setColor(Color.GREEN);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setAntiAlias(true);
            
            textPaint = new Paint();
            textPaint.setColor(Color.GREEN);
            textPaint.setTextSize(24);
            textPaint.setAntiAlias(true);
        }
        
        public void updateTargets(List<ScanService.TargetRect> newTargets) {
            this.targets = newTargets;
            invalidate();
        }
        
        public void clearTargets() {
            if (targets != null) {
                targets.clear();
            }
            invalidate();
        }
        
        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            
            if (targets == null || targets.isEmpty()) return;
            
            for (ScanService.TargetRect target : targets) {
                // 绘制目标框
                paint.setColor(target.confidence > 0.7f ? Color.GREEN : Color.YELLOW);
                canvas.drawRect(target.x, target.y, 
                    target.x + target.width, target.y + target.height, paint);
                
                // 绘制中心点（头部位置）
                paint.setColor(Color.RED);
                canvas.drawCircle(target.getCenterX(), target.getCenterY(), 5, paint);
                
                // 绘制置信度
                textPaint.setColor(target.confidence > 0.7f ? Color.GREEN : Color.YELLOW);
                canvas.drawText(String.format("%.0f%%", target.confidence * 100), 
                    target.x, target.y - 10, textPaint);
            }
        }
    }
    
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        ErrorLogger.getInstance().logWarning(TAG, "System low memory warning");
    }
}
