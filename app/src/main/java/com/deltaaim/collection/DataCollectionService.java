package com.deltaaim.collection;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 数据采集服务
 * 在手机端直接采集游戏截图，无需PC连接
 */
public class DataCollectionService extends Service {
    private static final String TAG = "DataCollection";
    private static final String CHANNEL_ID = "deltaaim_collection";
    private static final int NOTIFICATION_ID = 1002;

    // 单例引用
    private static DataCollectionService instance;

    // MediaProjection相关
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;

    // 采集参数
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    // 采集控制
    private boolean isCollecting = false;
    private int captureInterval = 2000; // 默认2秒间隔
    private AtomicInteger capturedCount = new AtomicInteger(0);
    private Handler handler = new Handler(Looper.getMainLooper());

    // 存储路径
    private File collectionDir;

    // 回调接口
    public interface CollectionCallback {
        void onCaptureComplete(String path, int totalCount);
        void onError(String message);
        void onCollectionStopped(int totalCount);
    }

    private CollectionCallback callback;

    public static DataCollectionService getInstance() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        createNotificationChannel();
        initStoragePath();
        Log.d(TAG, "数据采集服务已创建");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopCollection();
        instance = null;
        super.onDestroy();
    }

    /**
     * 设置回调
     */
    public void setCallback(CollectionCallback callback) {
        this.callback = callback;
    }

    /**
     * 初始化存储路径
     */
    private void initStoragePath() {
        File storageDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        collectionDir = new File(storageDir, "DeltaAim_Dataset");
        if (!collectionDir.exists()) {
            collectionDir.mkdirs();
        }
        Log.d(TAG, "采集目录: " + collectionDir.getAbsolutePath());
    }

    /**
     * 初始化MediaProjection
     */
    public boolean initProjection(int resultCode, Intent data) {
        try {
            projectionManager = (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);

            if (mediaProjection == null) {
                Log.e(TAG, "MediaProjection初始化失败");
                return false;
            }

            // 获取屏幕参数
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
            screenDensity = metrics.densityDpi;

            // 创建ImageReader
            imageReader = ImageReader.newInstance(
                screenWidth, screenHeight,
                PixelFormat.RGBA_8888, 2
            );

            // 创建VirtualDisplay
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "DeltaAimCollection",
                screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null
            );

            Log.d(TAG, "MediaProjection初始化成功: " + screenWidth + "x" + screenHeight);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "初始化异常: " + e.getMessage());
            return false;
        }
    }

    /**
     * 开始采集
     */
    public boolean startCollection(int intervalMs) {
        if (isCollecting) {
            Log.w(TAG, "已在采集中");
            return false;
        }

        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection未初始化");
            if (callback != null) {
                callback.onError("请先授权屏幕录制权限");
            }
            return false;
        }

        captureInterval = intervalMs;
        isCollecting = true;
        capturedCount.set(0);

        // 开始定时采集
        handler.post(captureRunnable);

        Log.d(TAG, "开始采集，间隔: " + captureInterval + "ms");
        Toast.makeText(this, "开始采集数据", Toast.LENGTH_SHORT).show();

        return true;
    }

    /**
     * 停止采集
     */
    public void stopCollection() {
        if (!isCollecting) return;

        isCollecting = false;
        handler.removeCallbacks(captureRunnable);

        int total = capturedCount.get();
        Log.d(TAG, "停止采集，共采集: " + total + " 张");
        Toast.makeText(this, "采集完成，共 " + total + " 张", Toast.LENGTH_SHORT).show();

        if (callback != null) {
            callback.onCollectionStopped(total);
        }
    }

    /**
     * 单次截图
     */
    public String captureOnce() {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection未初始化");
            return null;
        }

        Image image = imageReader.acquireLatestImage();
        if (image == null) {
            return null;
        }

        try {
            String path = saveImage(image);
            return path;
        } finally {
            image.close();
        }
    }

    /**
     * 采集任务
     */
    private Runnable captureRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isCollecting) return;

            try {
                Image image = imageReader.acquireLatestImage();
                if (image != null) {
                    String path = saveImage(image);
                    image.close();

                    if (path != null) {
                        int count = capturedCount.incrementAndGet();
                        Log.d(TAG, "采集成功 [" + count + "]: " + path);

                        if (callback != null) {
                            callback.onCaptureComplete(path, count);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "采集异常: " + e.getMessage());
            }

            // 继续下一次采集
            if (isCollecting) {
                handler.postDelayed(this, captureInterval);
            }
        }
    };

    /**
     * 保存图片
     */
    private String saveImage(Image image) {
        if (image == null) return null;

        try {
            // 转换为Bitmap
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * screenWidth;

            Bitmap bitmap = Bitmap.createBitmap(
                screenWidth + rowPadding / pixelStride,
                screenHeight,
                Bitmap.Config.ARGB_8888
            );
            bitmap.copyPixelsFromBuffer(buffer);

            // 裁剪掉多余部分
            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
            bitmap.recycle();

            // 生成文件名
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.getDefault());
            String timestamp = sdf.format(new Date());
            String filename = "capture_" + timestamp + ".png";
            File outputFile = new File(collectionDir, filename);

            // 保存PNG
            FileOutputStream fos = new FileOutputStream(outputFile);
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            croppedBitmap.recycle();

            return outputFile.getAbsolutePath();

        } catch (Exception e) {
            Log.e(TAG, "保存图片失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取采集统计
     */
    public int getCapturedCount() {
        return capturedCount.get();
    }

    /**
     * 是否正在采集
     */
    public boolean isCollecting() {
        return isCollecting;
    }

    /**
     * 获取存储目录
     */
    public File getCollectionDir() {
        return collectionDir;
    }

    /**
     * 创建通知渠道
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "DeltaAim数据采集",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("数据采集后台服务");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    /**
     * 创建通知
     */
    private Notification createNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeltaAim 数据采集")
            .setContentText("准备采集...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    /**
     * 更新通知
     */
    private void updateNotification(String content) {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeltaAim 数据采集")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, notification);
    }

    /**
     * 释放资源
     */
    public void release() {
        stopCollection();

        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }

        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader = null;
        }

        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
    }
}
