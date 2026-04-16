package com.deltaaim.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * 屏幕捕获服务 - 使用MediaProjection实现免Root截图
 * 目标：60FPS稳定输出
 */
public class ScreenCaptureService {
    private static final String TAG = "ScreenCapture";
    
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    
    private int screenWidth;
    private int screenHeight;
    private int captureWidth;
    private int captureHeight;
    private int captureX;
    private int captureY;
    
    private Bitmap latestFrame;
    private final Object frameLock = new Object();
    private volatile boolean isRunning = false;
    
    // 帧率统计
    private long lastFrameTime = 0;
    private int frameCount = 0;
    private float currentFPS = 0;
    
    public interface FrameCallback {
        void onFrame(Bitmap frame, float fps);
    }
    
    private FrameCallback callback;
    
    public ScreenCaptureService(Context context, int resultCode, android.content.Intent data) {
        MediaProjectionManager manager = (MediaProjectionManager) 
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        this.mediaProjection = manager.getMediaProjection(resultCode, data);
        
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);
        
        this.screenWidth = metrics.widthPixels;
        this.screenHeight = metrics.heightPixels;
        
        // 中心区域捕获（性能优化）
        this.captureWidth = Math.min(screenWidth, 640);
        this.captureHeight = Math.min(screenHeight, 480);
        this.captureX = (screenWidth - captureWidth) / 2;
        this.captureY = (screenHeight - captureHeight) / 2;
    }
    
    /**
     * 启动屏幕捕获
     */
    public void start(FrameCallback callback) {
        this.callback = callback;
        isRunning = true;
        
        imageReader = ImageReader.newInstance(
            captureWidth, captureHeight,
            PixelFormat.RGBA_8888, 2
        );
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "DeltaAimCapture",
            captureWidth, captureHeight, 
            DisplayMetrics.DENSITY_MEDIUM,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(),
            null, null
        );
        
        imageReader.setOnImageAvailableListener(reader -> {
            try (Image image = reader.acquireLatestImage()) {
                if (image != null) {
                    processFrame(image);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, new Handler(Looper.getMainLooper()));
    }
    
    /**
     * 处理帧数据
     */
    private void processFrame(Image image) {
        long currentTime = System.currentTimeMillis();
        
        // 帧率计算
        frameCount++;
        if (currentTime - lastFrameTime >= 1000) {
            currentFPS = frameCount * 1000f / (currentTime - lastFrameTime);
            frameCount = 0;
            lastFrameTime = currentTime;
        }
        
        // 转换为Bitmap
        Image.Plane[] planes = image.getPlanes();
        int width = image.getWidth();
        int height = image.getHeight();
        
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(planes[0].getBuffer());
        
        synchronized (frameLock) {
            latestFrame = bitmap;
        }
        
        if (callback != null) {
            callback.onFrame(bitmap, currentFPS);
        }
    }
    
    /**
     * 获取最新帧
     */
    public Bitmap getLatestFrame() {
        synchronized (frameLock) {
            return latestFrame;
        }
    }
    
    /**
     * 停止捕获
     */
    public void stop() {
        isRunning = false;
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
        }
    }
    
    public float getCurrentFPS() {
        return currentFPS;
    }
    
    public int getCaptureWidth() {
        return captureWidth;
    }
    
    public int getCaptureHeight() {
        return captureHeight;
    }
    
    public int getScreenOffsetX() {
        return captureX;
    }
    
    public int getScreenOffsetY() {
        return captureY;
    }
}
