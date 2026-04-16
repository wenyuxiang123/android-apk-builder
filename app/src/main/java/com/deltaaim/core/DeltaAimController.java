package com.deltaaim.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.util.Log;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * DeltaAim主控制器 - 协调屏幕捕获、目标检测和触控执行
 */
public class DeltaAimController {
    private static final String TAG = "DeltaAimController";
    
    // 模块实例
    private ScreenCaptureService screenCapture;
    private YOLODetector detector;
    private BionicAimController aimController;
    private TouchExecutionService touchService;
    
    // 控制参数
    private AtomicBoolean isRunning = new AtomicBoolean(false);
    private AtomicBoolean aimEnabled = new AtomicBoolean(true);
    private AtomicBoolean autoFireEnabled = new AtomicBoolean(false);
    
    // 性能统计
    private PerformanceStats stats = new PerformanceStats();
    
    // 屏幕参数
    private int screenWidth;
    private int screenHeight;
    private int captureOffsetX;
    private int captureOffsetY;
    
    // 回调接口
    public interface StatsCallback {
        void onStatsUpdate(PerformanceStats stats);
    }
    
    private StatsCallback statsCallback;
    
    public static class PerformanceStats {
        public float captureFPS;
        public float detectFPS;
        public float overallFPS;
        public int targetCount;
        public String currentMode;
        public long detectTimeMs;
        public long aimTimeMs;
    }
    
    public DeltaAimController(Context context, int screenCaptureResultCode, 
                              android.content.Intent screenCaptureData) {
        // 初始化屏幕捕获
        screenCapture = new ScreenCaptureService(context, screenCaptureResultCode, screenCaptureData);
        
        // 初始化目标检测器
        detector = new YOLODetector(context);
        
        // 初始化仿生瞄准控制器
        aimController = new BionicAimController();
        
        // 获取屏幕参数
        screenWidth = screenCapture.getCaptureWidth();
        screenHeight = screenCapture.getCaptureHeight();
        captureOffsetX = screenCapture.getScreenOffsetX();
        captureOffsetY = screenCapture.getScreenOffsetY();
    }
    
    /**
     * 启动系统
     */
    public void start() {
        if (isRunning.get()) {
            Log.w(TAG, "系统已在运行中");
            return;
        }
        
        isRunning.set(true);
        
        // 启动屏幕捕获
        screenCapture.start((frame, fps) -> {
            stats.captureFPS = fps;
            processFrame(frame);
        });
        
        Log.d(TAG, "DeltaAim已启动");
    }
    
    /**
     * 停止系统
     */
    public void stop() {
        isRunning.set(false);
        screenCapture.stop();
        Log.d(TAG, "DeltaAim已停止");
    }
    
    /**
     * 处理帧数据
     */
    private void processFrame(Bitmap frame) {
        if (!isRunning.get() || frame == null) {
            return;
        }
        
        long frameStart = System.currentTimeMillis();
        
        // 1. 目标检测
        long detectStart = System.currentTimeMillis();
        List<YOLODetector.Detection> detections = detector.detect(frame);
        stats.detectTimeMs = System.currentTimeMillis() - detectStart;
        stats.targetCount = detections.size();
        
        // 2. 瞄准控制（如果启用）
        long aimStart = System.currentTimeMillis();
        if (aimEnabled.get() && !detections.isEmpty()) {
            PointF aimPoint = aimController.update(detections, screenWidth, screenHeight, 0.016f);
            stats.currentMode = aimController.getCurrentMode().name();
            
            // 3. 执行触控
            if (touchService != null) {
                // 转换坐标（捕获区域偏移）
                PointF screenPoint = new PointF(
                    aimPoint.x + captureOffsetX,
                    aimPoint.y + captureOffsetY
                );
                
                touchService.moveAimTo(screenPoint);
                
                // 自动射击（如果启用且目标对准）
                if (autoFireEnabled.get() && isAimed(detections, aimPoint)) {
                    // 由外部控制射击时机
                }
            }
        } else {
            stats.currentMode = "IDLE";
            aimController.update(null, screenWidth, screenHeight, 0.016f);
        }
        stats.aimTimeMs = System.currentTimeMillis() - aimStart;
        
        // 性能统计
        stats.overallFPS = 1000f / (System.currentTimeMillis() - frameStart);
        
        if (statsCallback != null) {
            statsCallback.onStatsUpdate(stats);
        }
    }
    
    /**
     * 判断是否已瞄准目标
     */
    private boolean isAimed(List<YOLODetector.Detection> detections, PointF aimPoint) {
        for (YOLODetector.Detection det : detections) {
            if (!det.isEnemy) continue;
            
            float distToCenter = distance(aimPoint, 
                new PointF(det.getAimX(), det.getAimY()));
            
            // 在目标中心10像素内视为已瞄准
            if (distToCenter < 10) {
                return true;
            }
        }
        return false;
    }
    
    private float distance(PointF a, PointF b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    // ========== 配置方法 ==========
    
    public void setAimEnabled(boolean enabled) {
        this.aimEnabled.set(enabled);
    }
    
    public void setAutoFireEnabled(boolean enabled) {
        this.autoFireEnabled.set(enabled);
    }
    
    public void setTouchService(TouchExecutionService service) {
        this.touchService = service;
        if (service != null) {
            service.initScreenParams(screenWidth, screenHeight);
        }
    }
    
    public void setStatsCallback(StatsCallback callback) {
        this.statsCallback = callback;
    }
    
    public boolean isRunning() {
        return isRunning.get();
    }
    
    public PerformanceStats getStats() {
        return stats;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        stop();
        if (detector != null) {
            detector.close();
        }
    }
}
