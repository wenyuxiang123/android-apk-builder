package com.deltaaim.core;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.PointF;
import android.os.Build;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

/**
 * 触控执行服务 - 使用无障碍服务模拟触控
 * 免Root实现
 */
public class TouchExecutionService extends AccessibilityService {
    private static final String TAG = "TouchExecutor";
    
    private static TouchExecutionService instance;
    
    // 触控参数
    private static final long GESTURE_DURATION = 16;  // 约60fps
    
    // 当前准星位置
    private float currentCrosshairX = 0;
    private float currentCrosshairY = 0;
    
    // 屏幕中心
    private float screenCenterX = 0;
    private float screenCenterY = 0;
    
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "触控服务已启动");
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
    }
    
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 不需要处理事件
    }
    
    @Override
    public void onInterrupt() {
    }
    
    /**
     * 初始化屏幕参数
     */
    public void initScreenParams(int screenWidth, int screenHeight) {
        this.screenCenterX = screenWidth / 2f;
        this.screenCenterY = screenHeight / 2f;
        this.currentCrosshairX = screenCenterX;
        this.currentCrosshairY = screenCenterY;
    }
    
    /**
     * 移动准星到目标位置
     * 通过模拟拖动手势实现
     */
    public boolean moveAimTo(PointF target) {
        if (instance == null) {
            Log.e(TAG, "服务未初始化");
            return false;
        }
        
        // 计算需要移动的距离
        float deltaX = target.x - currentCrosshairX;
        float deltaY = target.y - currentCrosshairY;
        
        // 忽略小幅度移动
        if (Math.abs(deltaX) < 2 && Math.abs(deltaY) < 2) {
            return true;
        }
        
        // 创建拖动手势
        Path path = new Path();
        path.moveTo(screenCenterX, screenCenterY);
        
        // 计算终点（从中心点拖动）
        float endX = screenCenterX + deltaX;
        float endY = screenCenterY + deltaY;
        
        // 限制在屏幕范围内
        endX = Math.max(0, Math.min(endX, screenCenterX * 2));
        endY = Math.max(0, Math.min(endY, screenCenterY * 2));
        
        path.lineTo(endX, endY);
        
        // 构建手势描述
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(
            path, 0, GESTURE_DURATION
        ));
        
        GestureDescription gesture = builder.build();
        
        // 执行手势
        boolean result = dispatchGesture(gesture, null, null);
        
        if (result) {
            currentCrosshairX = target.x;
            currentCrosshairY = target.y;
        }
        
        return result;
    }
    
    /**
     * 平滑移动准星（多步执行）
     */
    public boolean smoothMoveTo(PointF target, int steps) {
        if (instance == null || steps <= 0) {
            return false;
        }
        
        float startX = currentCrosshairX;
        float startY = currentCrosshairY;
        
        // 分步执行，每步一个小幅度移动
        for (int i = 1; i <= steps; i++) {
            float progress = (float) i / steps;
            // 使用ease-out曲线
            float eased = 1 - (1 - progress) * (1 - progress);
            
            PointF stepTarget = new PointF();
            stepTarget.x = startX + (target.x - startX) * eased;
            stepTarget.y = startY + (target.y - startY) * eased;
            
            moveAimTo(stepTarget);
            
            try {
                Thread.sleep(16);  // 约60fps
            } catch (InterruptedException e) {
                break;
            }
        }
        
        return true;
    }
    
    /**
     * 执行点击（射击）
     */
    public boolean tap(float x, float y) {
        if (instance == null) {
            return false;
        }
        
        Path path = new Path();
        path.moveTo(x, y);
        
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(
            path, 0, 50  // 50ms点击
        ));
        
        return dispatchGesture(builder.build(), null, null);
    }
    
    /**
     * 执行长按
     */
    public boolean longPress(float x, float y, long durationMs) {
        if (instance == null) {
            return false;
        }
        
        Path path = new Path();
        path.moveTo(x, y);
        
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(
            path, 0, durationMs
        ));
        
        return dispatchGesture(builder.build(), null, null);
    }
    
    /**
     * 获取服务实例
     */
    public static TouchExecutionService getInstance() {
        return instance;
    }
    
    /**
     * 获取当前准星位置
     */
    public PointF getCurrentCrosshair() {
        return new PointF(currentCrosshairX, currentCrosshairY);
    }
    
    /**
     * 重置准星位置
     */
    public void resetCrosshair() {
        currentCrosshairX = screenCenterX;
        currentCrosshairY = screenCenterY;
    }
}
