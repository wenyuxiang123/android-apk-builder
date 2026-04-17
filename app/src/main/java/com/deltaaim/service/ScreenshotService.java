package com.deltaaim.service;

import android.annotation.SuppressLint;
import android.app.Notification;
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
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.deltaaim.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ScreenshotService extends Service {
    
    private static final String TAG = "ScreenshotService";
    private static final int NOTIFICATION_ID = 1003;
    private static final String SCREENSHOT_DIR = "DeltaAim";
    private static final int MAX_SCREENSHOTS = 1000;
    
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;
    
    private ExecutorService executorService;
    private Handler handler;
    private AtomicInteger screenshotCount;
    
    private File screenshotDirectory;
    private boolean isCapturing = false;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        screenshotCount = new AtomicInteger(0);
        
        createScreenshotDirectory();
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        
        Log.d(TAG, "ScreenshotService created: " + screenWidth + "x" + screenHeight);
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent != null ? intent.getIntExtra("resultCode", -1) : -1;
        
        if (resultCode == -1) {
            Log.e(TAG, "No result code provided");
            stopSelf();
            return START_NOT_STICKY;
        }
        
        MediaProjectionManager projectionManager = 
            (MediaProjectionManager) getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        
        if (projectionManager != null) {
            mediaProjection = projectionManager.getMediaProjection(resultCode, intent);
            
            if (mediaProjection != null) {
                setupImageReader();
                startForeground(NOTIFICATION_ID, createNotification());
                Log.d(TAG, "Screenshot capture started");
            } else {
                Log.e(TAG, "Failed to create media projection");
                stopSelf();
            }
        }
        
        return START_STICKY;
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopCapture();
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
        Log.d(TAG, "ScreenshotService destroyed");
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    
    @SuppressLint("WrongConstant")
    private void setupImageReader() {
        int captureWidth = screenWidth / 2;
        int captureHeight = screenHeight / 2;
        
        imageReader = ImageReader.newInstance(
            captureWidth, captureHeight, PixelFormat.RGBA_8888, 2
        );
        
        imageReader.setOnImageAvailableListener(reader -> {
            if (isCapturing) {
                Image image = reader.acquireLatestImage();
                if (image != null) {
                    processImage(image);
                    image.close();
                }
            }
        }, handler);
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "DeltaAimCapture",
            captureWidth, captureHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(), null, handler
        );
        
        isCapturing = true;
    }
    
    private void processImage(Image image) {
        executorService.execute(() -> {
            try {
                if (screenshotCount.get() >= MAX_SCREENSHOTS) {
                    cleanupOldScreenshots();
                }
                
                Bitmap bitmap = imageToBitmap(image);
                if (bitmap != null) {
                    saveBitmap(bitmap);
                    screenshotCount.incrementAndGet();
                    bitmap.recycle();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing image", e);
            }
        });
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
            Log.e(TAG, "Error converting image to bitmap", e);
            return null;
        }
    }
    
    private void saveBitmap(Bitmap bitmap) {
        if (screenshotDirectory == null || !screenshotDirectory.exists()) {
            createScreenshotDirectory();
        }
        
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
        String filename = "capture_" + timestamp + ".png";
        File file = new File(screenshotDirectory, filename);
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            Log.d(TAG, "Screenshot saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Error saving screenshot", e);
        }
    }
    
    private void cleanupOldScreenshots() {
        if (screenshotDirectory != null && screenshotDirectory.exists()) {
            File[] files = screenshotDirectory.listFiles();
            if (files != null && files.length > MAX_SCREENSHOTS / 2) {
                java.util.Arrays.sort(files, (f1, f2) -> 
                    Long.compare(f1.lastModified(), f2.lastModified())
                );
                
                for (int i = 0; i < files.length / 2; i++) {
                    files[i].delete();
                    screenshotCount.decrementAndGet();
                }
            }
        }
    }
    
    private void createScreenshotDirectory() {
        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        screenshotDirectory = new File(picturesDir, SCREENSHOT_DIR);
        
        if (!screenshotDirectory.exists()) {
            screenshotDirectory.mkdirs();
        }
    }
    
    private void stopCapture() {
        isCapturing = false;
        
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
    }
    
    private Notification createNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        return new NotificationCompat.Builder(this, MainActivity.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_capture_title))
            .setContentText(getString(R.string.notification_capture_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }
    
    public int getScreenshotCount() {
        return screenshotCount.get();
    }
}
