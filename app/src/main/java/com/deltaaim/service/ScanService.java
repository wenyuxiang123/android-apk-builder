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
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.deltaaim.MainActivity;
import com.deltaaim.util.ErrorLogger;
import com.deltaaim.util.ScreenshotManager;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScanService extends Service {
    
    private static final String TAG = "ScanService";
    private static final String CHANNEL_ID = "deltaaim_scan";
    private static final String STATUS_CHANNEL_ID = "deltaaim_status";
    private static final int NOTIFICATION_ID = 1006;
    private static final int STATUS_NOTIFICATION_ID = 1007;
    
    // 扫描参数
    private static final int SCAN_INTERVAL_MS = 16; // 约60fps
    private static final int DETECT_MIN_WIDTH = 30;  // 最小人物宽度
    private static final int DETECT_MIN_HEIGHT = 60; // 最小人物高度
    private static final int DETECT_MAX_WIDTH = 200; // 最大人物宽度
    private static final int DETECT_MAX_HEIGHT = 400; // 最大人物高度
    
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    private ExecutorService executorService;
    private Handler handler;
    private NotificationManager notificationManager;
    
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean isPaused = new AtomicBoolean(false);
    
    private static ScanService instance;
    
    // 检测结果回调
    public interface DetectionCallback {
        void onTargetsDetected(List<TargetRect> targets);
    }
    
    private static DetectionCallback detectionCallback;
    
    public static class TargetRect {
        public int x;
        public int y;
        public int width;
        public int height;
        public float confidence;
        
        public TargetRect(int x, int y, int width, int height, float confidence) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = confidence;
        }
        
        public int getCenterX() {
            return x + width / 2;
        }
        
        public int getCenterY() {
            return y + height / 4; // 头部位置
        }
    }
    
    public static void setDetectionCallback(DetectionCallback callback) {
        detectionCallback = callback;
    }
    
    public static boolean isServiceRunning() {
        return instance != null && instance.isRunning.get();
    }
    
    public static void pause() {
        if (instance != null) {
            instance.isPaused.set(true);
        }
    }
    
    public static void resume() {
        if (instance != null) {
            instance.isPaused.set(false);
        }
    }
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        instance = this;
        
        ErrorLogger.init(this);
        ErrorLogger.getInstance().logInfo(TAG, "ScanService created");
        
        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        
        createNotificationChannel();
        createStatusNotificationChannel();
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        
        isRunning.set(true);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            if (!ScreenshotManager.getInstance().hasPermission()) {
                ErrorLogger.getInstance().logError(TAG, "No screenshot permission", null);
                showErrorNotification("请先授权截图权限");
                stopSelf();
                return START_NOT_STICKY;
            }
            
            int resultCode = ScreenshotManager.getInstance().getResultCode();
            Intent data = ScreenshotManager.getInstance().getIntentData();
            
            if (resultCode == -1 || data == null) {
                ErrorLogger.getInstance().logError(TAG, "Invalid permission data", null);
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
                    ErrorLogger.getInstance().logInfo(TAG, "Scan service started");
                    showRunningNotification();
                } else {
                    ErrorLogger.getInstance().logError(TAG, "Failed to create media projection", null);
                    stopSelf();
                }
            }
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error in onStartCommand", e);
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
            
            ErrorLogger.getInstance().logInfo(TAG, "Scan service stopped");
            showStoppedNotification("扫描服务已停止");
            
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error in onDestroy", e);
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
                "DeltaAim 扫描服务",
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
            .setContentTitle("DeltaAim 实时扫描")
            .setContentText("正在扫描屏幕目标")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    private void showRunningNotification() {
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 扫描服务运行正常")
            .setContentText("实时扫描中...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setAutoCancel(true)
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    private void showErrorNotification(String errorMessage) {
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 扫描服务异常")
            .setContentText(errorMessage)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    private void showStoppedNotification(String reason) {
        Notification notification = new NotificationCompat.Builder(this, STATUS_CHANNEL_ID)
            .setContentTitle("DeltaAim 扫描服务已停止")
            .setContentText(reason)
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .setAutoCancel(true)
            .build();
        
        notificationManager.notify(STATUS_NOTIFICATION_ID, notification);
    }
    
    @SuppressLint("WrongConstant")
    private void setupImageReader() {
        try {
            // 使用较低分辨率提高性能
            int captureWidth = screenWidth / 2;
            int captureHeight = screenHeight / 2;
            
            imageReader = ImageReader.newInstance(
                captureWidth, captureHeight,
                PixelFormat.RGBA_8888, 2
            );
            
            imageReader.setOnImageAvailableListener(reader -> {
                if (!isRunning.get() || isPaused.get()) return;
                
                try {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        processFrame(image, captureWidth, captureHeight);
                        image.close();
                    }
                } catch (Exception e) {
                    // 静默处理单帧错误
                }
            }, handler);
            
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "DeltaAimScan",
                captureWidth, captureHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null, handler
            );
            
            ErrorLogger.getInstance().logInfo(TAG, "Image reader setup complete");
            
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Failed to setup image reader", e);
            stopSelf();
        }
    }
    
    private void processFrame(Image image, int width, int height) {
        executorService.execute(() -> {
            try {
                Bitmap bitmap = imageToBitmap(image, width, height);
                if (bitmap != null) {
                    List<TargetRect> targets = detectTargets(bitmap);
                    
                    if (targets != null && !targets.isEmpty() && detectionCallback != null) {
                        handler.post(() -> {
                            if (detectionCallback != null) {
                                detectionCallback.onTargetsDetected(targets);
                            }
                        });
                    }
                    
                    bitmap.recycle();
                }
            } catch (Exception e) {
                // 静默处理单帧错误
            }
        });
    }
    
    private Bitmap imageToBitmap(Image image, int expectedWidth, int expectedHeight) {
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
            return null;
        }
    }
    
    /**
     * 检测画面中的人物轮廓
     * 使用边缘检测和轮廓分析
     */
    private List<TargetRect> detectTargets(Bitmap bitmap) {
        List<TargetRect> targets = new ArrayList<>();
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // 降采样加速处理
        int sampleRate = 4;
        int sw = width / sampleRate;
        int sh = height / sampleRate;
        
        // 转换为灰度并计算边缘
        int[] pixels = new int[sw * sh];
        Bitmap smallBitmap = Bitmap.createScaledBitmap(bitmap, sw, sh, false);
        smallBitmap.getPixels(pixels, 0, sw, 0, 0, sw, sh);
        
        // 灰度化
        int[] gray = new int[sw * sh];
        for (int i = 0; i < pixels.length; i++) {
            int p = pixels[i];
            int r = (p >> 16) & 0xFF;
            int g = (p >> 8) & 0xFF;
            int b = p & 0xFF;
            gray[i] = (int)(0.299f * r + 0.587f * g + 0.114f * b);
        }
        
        // Sobel边缘检测
        int[] edges = new int[sw * sh];
        for (int y = 1; y < sh - 1; y++) {
            for (int x = 1; x < sw - 1; x++) {
                int idx = y * sw + x;
                
                // Sobel算子
                int gx = -gray[(y-1)*sw + (x-1)] + gray[(y-1)*sw + (x+1)]
                        -2*gray[y*sw + (x-1)] + 2*gray[y*sw + (x+1)]
                        -gray[(y+1)*sw + (x-1)] + gray[(y+1)*sw + (x+1)];
                
                int gy = -gray[(y-1)*sw + (x-1)] - 2*gray[(y-1)*sw + x] - gray[(y-1)*sw + (x+1)]
                        +gray[(y+1)*sw + (x-1)] + 2*gray[(y+1)*sw + x] + gray[(y+1)*sw + (x+1)];
                
                int g = (int)Math.sqrt(gx*gx + gy*gy);
                edges[idx] = g > 50 ? 255 : 0;
            }
        }
        
        // 简单的连通区域分析，寻找垂直矩形
        boolean[] visited = new boolean[sw * sh];
        
        for (int y = 0; y < sh; y++) {
            for (int x = 0; x < sw; x++) {
                int idx = y * sw + x;
                if (edges[idx] > 0 && !visited[idx]) {
                    // BFS寻找连通区域
                    int[] bounds = findConnectedRegion(edges, visited, sw, sh, x, y);
                    
                    if (bounds != null) {
                        int minX = bounds[0] * sampleRate;
                        int minY = bounds[1] * sampleRate;
                        int maxX = bounds[2] * sampleRate;
                        int maxY = bounds[3] * sampleRate;
                        
                        int w = maxX - minX;
                        int h = maxY - minY;
                        
                        // 检查是否为人物大小（宽高比约1:2到1:3）
                        if (w >= DETECT_MIN_WIDTH / sampleRate && w <= DETECT_MAX_WIDTH / sampleRate &&
                            h >= DETECT_MIN_HEIGHT / sampleRate && h <= DETECT_MAX_HEIGHT / sampleRate) {
                            float aspectRatio = (float)h / w;
                            if (aspectRatio >= 1.5f && aspectRatio <= 4.0f) {
                                // 可能是人物
                                float confidence = Math.min(1.0f, (aspectRatio - 1.5f) / 1.5f + 0.5f);
                                targets.add(new TargetRect(minX, minY, w, h, confidence));
                            }
                        }
                    }
                }
            }
        }
        
        smallBitmap.recycle();
        
        return targets;
    }
    
    private int[] findConnectedRegion(int[] edges, boolean[] visited, int sw, int sh, int startX, int startY) {
        int minX = startX, maxX = startX;
        int minY = startY, maxY = startY;
        int count = 0;
        
        // 简单BFS
        List<Integer> queue = new ArrayList<>();
        queue.add(startY * sw + startX);
        visited[startY * sw + startX] = true;
        
        int head = 0;
        while (head < queue.size() && queue.size() < 1000) { // 限制大小
            int idx = queue.get(head++);
            int x = idx % sw;
            int y = idx / sw;
            count++;
            
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            
            // 检查4邻域
            int[] dx = {-1, 1, 0, 0};
            int[] dy = {0, 0, -1, 1};
            
            for (int d = 0; d < 4; d++) {
                int nx = x + dx[d];
                int ny = y + dy[d];
                
                if (nx >= 0 && nx < sw && ny >= 0 && ny < sh) {
                    int nidx = ny * sw + nx;
                    if (!visited[nidx] && edges[nidx] > 0) {
                        visited[nidx] = true;
                        queue.add(nidx);
                    }
                }
            }
        }
        
        // 过滤太小的区域
        int w = maxX - minX;
        int h = maxY - minY;
        if (count < 50 || w < 5 || h < 10) {
            return null;
        }
        
        return new int[]{minX, minY, maxX, maxY};
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
        } catch (Exception e) {
            ErrorLogger.getInstance().logException(TAG, "Error stopping capture", e);
        }
    }
}
