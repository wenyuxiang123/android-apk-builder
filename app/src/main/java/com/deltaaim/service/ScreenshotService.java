package com.deltaaim.service;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.deltaaim.MainActivity;
import com.deltaaim.util.ErrorLogger;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ScreenshotService extends Service {
    
    private static final String TAG = "ScreenshotService";
    private static final String CHANNEL_ID = "deltaaim_screenshot";
    private static final String STATUS_CHANNEL_ID = "deltaaim_status";
    private static final int NOTIFICATION_ID = 1003;
    private static final int STATUS_NOTIFICATION_ID = 1005;
    private static final String SCREENSHOT_DIR = "DeltaAim";
    
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    private ExecutorService executorService;
    private Handler handler;
    private File screenshotDirectory;
    
    private NotificationManager notificationManager;
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicLong captureCount = new AtomicLong(0);
    private AtomicLong errorCount = new AtomicLong(0);
    private long startTime;
    
    private static final long MAX_ERRORS = 10;
    
    private static ScreenshotService instance;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        instance = this;
        
        try {
            ErrorLogger.init(this);
            ErrorLogger.getInstance().logInfo(TAG, "ScreenshotService created");
        } catch (Exception e) {
            Log.e(TAG, "Failed to init ErrorLogger", e);
        }
        
        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        createNotificationChannel();
        createStatusNotificationChannel();
        createScreenshotDirectory();
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        
        startTime = System.currentTimeMillis();
        isRunning.set(true);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            int resultCode = intent != null ? intent.getIntExtra("resultCode", -1) : -1;
            Intent data = intent != null ? intent.getParcelableExtra("data") : null;
            
            if (resultCode == -1 || data == null) {
                ErrorLogger.getInstance().logError(TAG, "No result code or data provided", null);
                showErrorNotification("截图权限获取失败");
                stopSelf();
                return START_NOT_STICKY;
            }
            
            startForeground(NOTIFICATION_ID, createNotification());
            
            MediaProjectionManager projectionManager = 
                (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            
            if (projectionManager != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                
                if (mediaProjection != null) {
                    setupImageReader();
                    ErrorLogger.getInstance().logInfo(TAG, "Screenshot capture started successfully");
                    showRunningNotification();
                } else {
                    ErrorLogger.getInstance().logError(TAG, "Failed to create media projection", null);
                    showErrorNotification("无法创建屏幕投影");
                    stopSelf();
                }
            }
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error in onStartCommand", e);
            showErrorNotification("截图服务启动失败: " + e.getMessage());
            stopSelf();
        }
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        try {
            isRunning.set(false);
            stopCapture();
            
            if (executorService != null && !executorService.isShutdown()) {
                executorService.shutdown();
            }
            
            long duration = System.currentTimeMillis() - startTime;
            long captures = captureCount.get();
            long errors = errorCount.get();
            
            String stats = String.format(Locale.getDefault(), 
                "截图服务已停止 - 运行时长: %d秒, 截图数: %d, 错误数: %d",
                duration / 1000, captures, errors);
            
            ErrorLogger.getInstance().logInfo(TAG, stats);
            showStoppedNotification(stats);
            
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error in onDestroy", e);
            showErrorNotification("截图服务异常停止: " + e.getMessage());
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
                "DeltaAim 截图服务",
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
            .setContentTitle("DeltaAim 数据采集")
            .setContentText("正在采集训练数据")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    private void showRunningNotification() {
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 截图服务运行正常")
            .setContentText("数据采集中...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
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
        
        String logPath = ErrorLogger.getInstance().getLogDirectory();
        
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 截图服务异常")
            .setContentText(errorMessage)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(errorMessage + "\n\n日志路径: " + logPath))
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    private void showStoppedNotification(String reason) {
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 截图服务已停止")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setAutoCancel(true)
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    @SuppressLint("WrongConstant")
    private void setupImageReader() {
        try {
            int captureWidth = screenWidth / 2;
            int captureHeight = screenHeight / 2;
            
            imageReader = ImageReader.newInstance(
                captureWidth, captureHeight,
                PixelFormat.RGBA_8888, 2
            );
            
            imageReader.setOnImageAvailableListener(reader -> {
                if (!isRunning.get()) return;
                
                try {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        processImage(image);
                        image.close();
                    }
                } catch (Exception e) {
                    handleCaptureError("Image available callback error", e);
                }
            }, handler);
            
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "DeltaAimCapture",
                captureWidth, captureHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, handler
            );
            
            ErrorLogger.getInstance().logInfo(TAG, "Image reader setup complete");
            
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Failed to setup image reader", e);
            showErrorNotification("图像读取器初始化失败: " + e.getMessage());
            stopSelf();
        }
    }
    
    private void processImage(Image image) {
        executorService.execute(() -> {
            try {
                Bitmap bitmap = imageToBitmap(image);
                if (bitmap != null) {
                    saveBitmap(bitmap);
                    bitmap.recycle();
                    
                    long count = captureCount.incrementAndGet();
                    if (count % 100 == 0) {
                        updateProgressNotification(count);
                    }
                }
            } catch (Exception e) {
                handleCaptureError("Error processing image", e);
            }
        });
    }
    
    private void handleCaptureError(String context, Exception e) {
        long errors = errorCount.incrementAndGet();
        ErrorLogger.getInstance().logException(TAG, context, e);
        
        if (errors >= MAX_ERRORS) {
            ErrorLogger.getInstance().logError(TAG, "Too many errors, stopping service", null);
            showErrorNotification("截图错误过多，服务已停止。错误: " + errors);
            stopSelf();
        } else if (errors % 5 == 0) {
            showErrorNotification("截图出现错误 (" + errors + "次): " + e.getMessage());
        }
    }
    
    private Bitmap imageToBitmap(Image image) {
        try {
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * image.getWidth();
            
            Bitmap bitmap = Bitmap.createBitmap(
                image.getWidth() + rowPadding / pixelStride,
                image.getHeight(),
                Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);
            
            return Bitmap.createBitmap(bitmap, 0, 0, image.getWidth(), image.getHeight());
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error converting image to bitmap", e);
            return null;
        }
    }
    
    private void saveBitmap(Bitmap bitmap) {
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(new Date());
        String filename = "capture_" + timestamp + ".png";
        
        File file = new File(screenshotDirectory, filename);
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error saving screenshot", e);
        }
    }
    
    private void updateProgressNotification(long count) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeltaAim 数据采集")
            .setContentText("已采集 " + count + " 张截图")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build();
        
        notificationManager.notify(NOTIFICATION_ID, notification);
    }
    
    private void createScreenshotDirectory() {
        File picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        );
        screenshotDirectory = new File(picturesDir, SCREENSHOT_DIR);
        
        if (!screenshotDirectory.exists()) {
            boolean created = screenshotDirectory.mkdirs();
            if (created) {
                ErrorLogger.getInstance().logInfo(TAG, "Screenshot directory created: " + screenshotDirectory.getAbsolutePath());
            }
        }
    }
    
    private void stopCapture() {
        try {
            if (virtualDisplay != null) {
                virtualDisplay.release();
                virtualDisplay = null;
            }
            
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            
            if (mediaProjection != null) {
                mediaProjection.stop();
                mediaProjection = null;
            }
            
            ErrorLogger.getInstance().logInfo(TAG, "Capture stopped cleanly");
            
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error stopping capture", e);
        }
    }
    
    public static boolean isServiceRunning() {
        return instance != null && instance.isRunning.get();
    }
}
