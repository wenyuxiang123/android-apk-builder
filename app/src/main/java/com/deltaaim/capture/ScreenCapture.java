package com.deltaaim.capture;

import android.content.Context;
import android.graphics.Bitmap;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

/**
 * 截屏管理器 - 单例模式
 * 最简化实现，避免Intent传递问题
 */
public class ScreenCapture {
    private static final String TAG = "ScreenCapture";
    
    private static ScreenCapture instance;
    
    private Context context;
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    private boolean isCapturing = false;
    private Bitmap latestBitmap;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    private ScreenCapture(Context ctx) {
        this.context = ctx.getApplicationContext();
        this.projectionManager = (MediaProjectionManager) 
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }
    
    public static synchronized void init(Context context) {
        if (instance == null) {
            instance = new ScreenCapture(context);
        }
    }
    
    public static ScreenCapture getInstance() {
        if (instance == null) {
            throw new IllegalStateException("ScreenCapture not initialized");
        }
        return instance;
    }
    
    /**
     * 创建MediaProjection
     * 在Activity中授权成功后立即调用
     */
    public boolean createProjection(int resultCode, android.content.Intent data) {
        Log.d(TAG, "createProjection: resultCode=" + resultCode);
        
        if (projectionManager == null) {
            Log.e(TAG, "MediaProjectionManager is null");
            return false;
        }
        
        try {
            mediaProjection = projectionManager.getMediaProjection(resultCode, data);
            
            if (mediaProjection == null) {
                Log.e(TAG, "getMediaProjection returned null");
                return false;
            }
            
            // 注册回调
            mediaProjection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    Log.w(TAG, "MediaProjection stopped");
                    isCapturing = false;
                    mediaProjection = null;
                }
            }, handler);
            
            Log.i(TAG, "MediaProjection created successfully");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to create MediaProjection", e);
            return false;
        }
    }
    
    /**
     * 开始截屏
     */
    public boolean startCapture() {
        if (mediaProjection == null) {
            Log.e(TAG, "MediaProjection is null, cannot start capture");
            return false;
        }
        
        if (isCapturing) {
            Log.d(TAG, "Already capturing");
            return true;
        }
        
        try {
            // 创建ImageReader
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, 
                android.graphics.PixelFormat.RGBA_8888, 2);
            
            // 创建VirtualDisplay
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(),
                null,
                handler
            );
            
            // 设置监听器获取图像
            imageReader.setOnImageAvailableListener(reader -> {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    try {
                        latestBitmap = imageToBitmap(image);
                    } finally {
                        image.close();
                    }
                }
            }, handler);
            
            isCapturing = true;
            Log.i(TAG, "Capture started: " + screenWidth + "x" + screenHeight);
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "Failed to start capture", e);
            return false;
        }
    }
    
    /**
     * 停止截屏
     */
    public void stopCapture() {
        isCapturing = false;
        
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        
        if (imageReader != null) {
            imageReader.setOnImageAvailableListener(null, null);
            imageReader.close();
            imageReader = null;
        }
        
        Log.i(TAG, "Capture stopped");
    }
    
    /**
     * 获取最新截图
     */
    public Bitmap getLatestBitmap() {
        return latestBitmap;
    }
    
    /**
     * 是否正在截屏
     */
    public boolean isCapturing() {
        return isCapturing && mediaProjection != null;
    }
    
    /**
     * 是否有权限
     */
    public boolean hasPermission() {
        return mediaProjection != null;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stopCapture();
        
        if (mediaProjection != null) {
            mediaProjection.stop();
            mediaProjection = null;
        }
        
        latestBitmap = null;
        Log.i(TAG, "ScreenCapture released");
    }
    
    /**
     * Image转Bitmap
     */
    private Bitmap imageToBitmap(Image image) {
        Image.Plane[] planes = image.getPlanes();
        android.graphics.Buffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * screenWidth;
        
        // 创建Bitmap
        Bitmap bitmap = Bitmap.createBitmap(
            screenWidth + rowPadding / pixelStride,
            screenHeight,
            Bitmap.Config.ARGB_8888
        );
        buffer.rewind();
        bitmap.copyPixelsFromBuffer(buffer);
        
        // 裁剪到正确尺寸
        if (rowPadding > 0) {
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight);
        }
        
        return bitmap;
    }
    
    public int getScreenWidth() { return screenWidth; }
    public int getScreenHeight() { return screenHeight; }
}
