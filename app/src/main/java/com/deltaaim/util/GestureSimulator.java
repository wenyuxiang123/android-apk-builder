package com.deltaaim.util;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.util.Log;
import android.view.Display;

public class GestureSimulator {
    
    private static final String TAG = "GestureSimulator";
    private static final long SWIPE_DURATION = 100;
    private static final long DRAG_DURATION = 150;
    
    private final AccessibilityService service;
    
    public GestureSimulator(AccessibilityService service) {
        this.service = service;
    }
    
    public boolean performClick(float x, float y) {
        if (service == null) {
            Log.e(TAG, "Service is null");
            return false;
        }
        try {
            GestureDescription gesture = createClickGesture(x, y);
            return dispatchGesture(gesture);
        } catch (Exception e) {
            Log.e(TAG, "Error performing click", e);
            return false;
        }
    }
    
    public boolean performSwipe(float startX, float startY, float endX, float endY, long duration) {
        if (service == null) {
            Log.e(TAG, "Service is null");
            return false;
        }
        try {
            GestureDescription gesture = createSwipeGesture(startX, startY, endX, endY, duration);
            return dispatchGesture(gesture);
        } catch (Exception e) {
            Log.e(TAG, "Error performing swipe", e);
            return false;
        }
    }
    
    public boolean performDrag(float startX, float startY, float endX, float endY, long duration) {
        if (service == null) {
            Log.e(TAG, "Service is null");
            return false;
        }
        try {
            GestureDescription gesture = createSwipeGesture(startX, startY, endX, endY, duration);
            return dispatchGesture(gesture);
        } catch (Exception e) {
            Log.e(TAG, "Error performing drag", e);
            return false;
        }
    }
    
    public boolean performAim(float targetX, float targetY) {
        if (service == null) {
            Log.e(TAG, "Service is null");
            return false;
        }
        try {
            Display display = service.getDisplay();
            if (display == null) {
                Log.e(TAG, "Display is null");
                return false;
            }
            Point size = new Point();
            display.getSize(size);
            float centerX = size.x / 2f;
            float centerY = size.y / 2f;
            float deltaX = targetX - centerX;
            float deltaY = targetY - centerY;
            float maxDelta = Math.min(size.x, size.y) * 0.3f;
            float distance = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
            if (distance > maxDelta) {
                float scale = maxDelta / distance;
                deltaX *= scale;
                deltaY *= scale;
            }
            float newX = centerX + deltaX;
            float newY = centerY + deltaY;
            return performDrag(centerX, centerY, newX, newY, DRAG_DURATION);
        } catch (Exception e) {
            Log.e(TAG, "Error performing aim", e);
            return false;
        }
    }
    
    private GestureDescription createClickGesture(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        return builder.build();
    }
    
    private GestureDescription createSwipeGesture(float startX, float startY, float endX, float endY, long duration) {
        Path path = new Path();
        path.moveTo(startX, startY);
        float midX = (startX + endX) / 2;
        float midY = (startY + endY) / 2;
        path.quadTo(midX, midY, endX, endY);
        GestureDescription.Builder builder = new GestureDescription.Builder();
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, duration));
        return builder.build();
    }
    
    private boolean dispatchGesture(GestureDescription gesture) {
        if (service == null) return false;
        return service.dispatchGesture(gesture, new AccessibilityService.GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d(TAG, "Gesture completed");
            }
            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.d(TAG, "Gesture cancelled");
            }
        }, null);
    }
    
    public boolean performRapidFire(float x, float y, int count, long interval) {
        if (service == null) return false;
        new Thread(() -> {
            for (int i = 0; i < count; i++) {
                performClick(x, y);
                try {
                    Thread.sleep(interval);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
        return true;
    }
}
