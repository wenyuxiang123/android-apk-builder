package com.deltaaim.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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

public class FloatingWindowService extends Service {
    
    private static final String TAG = "FloatingWindowService";
    private static final String CHANNEL_ID = "deltaaim_floating";
    private static final int NOTIFICATION_ID = 1002;
    
    private WindowManager windowManager;
    private FrameLayout floatingView;
    private WindowManager.LayoutParams params;
    
    private boolean isAimActive = false;
    private Handler handler;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        handler = new Handler(Looper.getMainLooper());
        
        createNotificationChannel();
        createFloatingWindow();
        startForeground(NOTIFICATION_ID, createNotification());
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
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
                "DeltaAim Floating Window",
                NotificationManager.IMPORTANCE_LOW
            );
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
            .setContentTitle("DeltaAim Active")
            .setContentText("Aim assist is running")
            .setSmallIcon(android.R.drawable.ic_menu_myplaces)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    private void createFloatingWindow() {
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
        toggleBtn.setText("START");
        toggleBtn.setTextColor(Color.WHITE);
        toggleBtn.setBackgroundColor(Color.parseColor("#0F3460"));
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        );
        btnParams.topMargin = 50;
        toggleBtn.setLayoutParams(btnParams);
        
        toggleBtn.setOnClickListener(v -> {
            isAimActive = !isAimActive;
            toggleBtn.setText(isAimActive ? "STOP" : "START");
            toggleBtn.setBackgroundColor(isAimActive ? Color.parseColor("#D32F2F") : Color.parseColor("#0F3460"));
            statusText.setText(isAimActive ? "Active" : "DeltaAim");
        });
        
        floatingView.addView(toggleBtn);
        
        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            
            @Override
            public boolean onTouch(View v, MotionEvent event) {
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
                return false;
            }
        });
        
        windowManager.addView(floatingView, params);
    }
}
