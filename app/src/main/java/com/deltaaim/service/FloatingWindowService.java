package com.deltaaim.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
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
import com.deltaaim.util.TargetDetector;

public class FloatingWindowService extends Service {
    
    private static final String TAG = "FloatingWindowService";
    private static final int NOTIFICATION_ID = 1002;
    private static final int WINDOW_WIDTH = 180;
    private static final int WINDOW_HEIGHT = 70;
    
    private WindowManager windowManager;
    private FrameLayout floatingView;
    private WindowManager.LayoutParams params;
    
    private TargetDetector targetDetector;
    private boolean isAimActive = false;
    private int screenWidth;
    private int screenHeight;
    
    private Handler handler;
    private Runnable aimRunnable;
    
    private Button btnToggle;
    private TextView textStatus;
    
    private float initialX, initialY;
    private float dX, dY;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        targetDetector = new TargetDetector();
        handler = new Handler(Looper.getMainLooper());
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        
        createFloatingWindow();
        startForeground(NOTIFICATION_ID, createNotification());
        
        Log.d(TAG, "FloatingWindowService created");
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAimLoop();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
        Log.d(TAG, "FloatingWindowService destroyed");
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @SuppressLint("ClickableViewAccessibility")
    private void createFloatingWindow() {
        int layoutFlag;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }
        
        params = new WindowManager.LayoutParams(
            dpToPx(WINDOW_WIDTH),
            dpToPx(WINDOW_HEIGHT),
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = screenWidth - dpToPx(WINDOW_WIDTH + 20);
        params.y = dpToPx(100);
        
        floatingView = new FrameLayout(this) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        dX = event.getRawX() - initialX;
                        dY = event.getRawY() - initialY;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = (int) (event.getRawX() - dX);
                        params.y = (int) (event.getRawY() - dY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                }
                return super.onTouchEvent(event);
            }
        };
        
        floatingView.setBackgroundColor(Color.parseColor("#E6000000"));
        floatingView.setAlpha(0.9f);
        
        // Status indicator
        View indicator = new View(this);
        indicator.setBackgroundColor(Color.RED);
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
            dpToPx(16), dpToPx(16)
        );
        indicatorParams.gravity = Gravity.CENTER_VERTICAL;
        indicatorParams.setMargins(dpToPx(12), 0, 0, 0);
        floatingView.addView(indicator, indicatorParams);
        
        // Status text
        textStatus = new TextView(this);
        textStatus.setText("STOP");
        textStatus.setTextColor(Color.WHITE);
        textStatus.setTextSize(14);
        textStatus.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams textParams = new FrameLayout.LayoutParams(
            dpToPx(70), dpToPx(40)
        );
        textParams.gravity = Gravity.CENTER_VERTICAL;
        textParams.setMargins(dpToPx(8), 0, 0, 0);
        floatingView.addView(textStatus, textParams);
        
        // Toggle button
        btnToggle = new Button(this);
        btnToggle.setText("▶");
        btnToggle.setTextSize(16);
        btnToggle.setTextColor(Color.WHITE);
        btnToggle.setBackgroundColor(Color.parseColor("#4CAF50"));
        FrameLayout.LayoutParams btnParams = new FrameLayout.LayoutParams(
            dpToPx(40), dpToPx(40)
        );
        btnParams.gravity = Gravity.CENTER_VERTICAL;
        btnParams.setMargins(dpToPx(16), 0, dpToPx(8), 0);
        btnToggle.setOnClickListener(v -> toggleAimAssist());
        floatingView.addView(btnToggle, btnParams);
        
        windowManager.addView(floatingView, params);
    }
    
    private void toggleAimAssist() {
        if (isAimActive) {
            stopAimLoop();
        } else {
            startAimLoop();
        }
    }
    
    private void startAimLoop() {
        if (!DeltaAimAccessibilityService.isAvailable()) {
            Log.e(TAG, "Accessibility service not available");
            return;
        }
        
        isAimActive = true;
        updateUI(true);
        
        aimRunnable = new Runnable() {
            private long lastScanTime = 0;
            private static final long SCAN_INTERVAL = 100;
            
            @Override
            public void run() {
                if (!isAimActive) return;
                
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastScanTime >= SCAN_INTERVAL) {
                    lastScanTime = currentTime;
                    performAimDetection();
                }
                handler.postDelayed(this, SCAN_INTERVAL);
            }
        };
        
        handler.post(aimRunnable);
        Log.d(TAG, "Aim loop started");
    }
    
    private void stopAimLoop() {
        isAimActive = false;
        updateUI(false);
        
        if (aimRunnable != null) {
            handler.removeCallbacks(aimRunnable);
            aimRunnable = null;
        }
        Log.d(TAG, "Aim loop stopped");
    }
    
    private void performAimDetection() {
        // Placeholder for target detection
        // Actual implementation should capture screen and analyze
    }
    
    private void updateUI(boolean active) {
        if (btnToggle != null) {
            btnToggle.setText(active ? "■" : "▶");
            btnToggle.setBackgroundColor(
                active ? Color.parseColor("#F44336") : Color.parseColor("#4CAF50")
            );
        }
        if (textStatus != null) {
            textStatus.setText(active ? "ACTIVE" : "STOP");
            textStatus.setTextColor(active ? Color.GREEN : Color.WHITE);
        }
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        return new NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_aim_title))
            .setContentText(getString(R.string.notification_aim_text))
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}
