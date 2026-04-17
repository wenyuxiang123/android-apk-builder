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

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenshotService extends Service {
    
    private static final String TAG = "ScreenshotService";
    private static final String CHANNEL_ID = "deltaaim_screenshot";
    private static final int NOTIFICATION_ID = 1003;
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
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        executorService = Executors.newSingleThreadExecutor();
        handler = new Handler(Looper.getMainLooper());
        
        createNotificationChannel();
        createScreenshotDirectory();
        
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int resultCode = intent != null ? intent.getIntExtra("resultCode", -1) : -1;
        Intent data = intent != null ? intent.getParcelableExtra("data") : null;
        
        if (resultCode == -1 || data == null) {
            Log.e(TAG, "No result code or data provided");
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
                "DeltaAim Screenshot",
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
            .setContentTitle("DeltaAim Capturing")
            .setContentText("Collecting training data")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }
    
    @SuppressLint("WrongConstant")
    private void setupImageReader() {
        int captureWidth = screenWidth / 2;
        int captureHeight = screenHeight / 2;
        
        imageReader = ImageReader.newInstance(
            captureWidth, captureHeight,
            PixelFormat.RGBA_8888, 2
        );
        
        imageReader.setOnImageAvailableListener(reader -> {
            Image image = reader.acquireLatestImage();
            if (image != null) {
                processImage(image);
                image.close();
            }
        }, handler);
        
        virtualDisplay = mediaProjection.createVirtualDisplay(
            "DeltaAimCapture",
            captureWidth, captureHeight, screenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.getSurface(),
            null, handler
        );
    }
    
    private void processImage(Image image) {
        executorService.execute(() -> {
            try {
                Bitmap bitmap = imageToBitmap(image);
                if (bitmap != null) {
                    saveBitmap(bitmap);
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
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US)
            .format(new Date());
        String filename = "capture_" + timestamp + ".png";
        
        File file = new File(screenshotDirectory, filename);
        
        try (FileOutputStream fos = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, fos);
            Log.d(TAG, "Screenshot saved: " + file.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "Error saving screenshot", e);
        }
    }
    
    private void createScreenshotDirectory() {
        File picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        );
        screenshotDirectory = new File(picturesDir, SCREENSHOT_DIR);
        
        if (!screenshotDirectory.exists()) {
            screenshotDirectory.mkdirs();
        }
    }
    
    private void stopCapture() {
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
}
