package com.deltaaim.util;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class TargetDetector {
    
    private static final String TAG = "TargetDetector";
    
    private int targetColorMin;
    private int targetColorMax;
    
    private int minTargetSize = 20;
    private int maxTargetSize = 200;
    private float confidenceThreshold = 0.5f;
    
    private List<Target> detectedTargets;
    private AtomicBoolean isProcessing;
    
    public TargetDetector() {
        detectedTargets = new ArrayList<>();
        isProcessing = new AtomicBoolean(false);
        setTargetColorRange(Color.argb(255, 200, 50, 50), Color.argb(255, 255, 100, 100));
    }
    
    public void setTargetColorRange(int colorMin, int colorMax) {
        this.targetColorMin = colorMin;
        this.targetColorMax = colorMax;
    }
    
    public List<Target> detect(Bitmap bitmap) {
        if (bitmap == null || isProcessing.get()) {
            return new ArrayList<>();
        }
        
        isProcessing.set(true);
        detectedTargets.clear();
        
        try {
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();
            
            for (int y = 0; y < height; y += 10) {
                for (int x = 0; x < width; x += 10) {
                    int pixel = bitmap.getPixel(x, y);
                    if (isTargetColor(pixel)) {
                        Target target = new Target(x, y, 30, 30);
                        detectedTargets.add(target);
                    }
                }
            }
            
            mergeOverlappingTargets();
            
        } catch (Exception e) {
            Log.e(TAG, "Error during detection", e);
        } finally {
            isProcessing.set(false);
        }
        
        return new ArrayList<>(detectedTargets);
    }
    
    public void detectAsync(Bitmap bitmap, DetectionCallback callback) {
        new Thread(() -> {
            List<Target> targets = detect(bitmap);
            if (callback != null) {
                callback.onDetectionComplete(targets);
            }
        }).start();
    }
    
    private boolean isTargetColor(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        return r > 180 && r < 255 && g < 120 && b < 120;
    }
    
    private void mergeOverlappingTargets() {
        if (detectedTargets.size() < 2) return;
        
        List<Target> merged = new ArrayList<>();
        boolean[] processed = new boolean[detectedTargets.size()];
        
        for (int i = 0; i < detectedTargets.size(); i++) {
            if (processed[i]) continue;
            Target current = detectedTargets.get(i);
            
            for (int j = i + 1; j < detectedTargets.size(); j++) {
                if (processed[j]) continue;
                Target other = detectedTargets.get(j);
                if (isOverlapping(current, other)) {
                    current.merge(other);
                    processed[j] = true;
                }
            }
            merged.add(current);
        }
        detectedTargets.clear();
        detectedTargets.addAll(merged);
    }
    
    private boolean isOverlapping(Target t1, Target t2) {
        return !(t1.x + t1.width < t2.x || t2.x + t2.width < t1.x ||
                 t1.y + t1.height < t2.y || t2.y + t2.height < t1.y);
    }
    
    public Target getClosestTarget(int centerX, int centerY) {
        Target closest = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Target target : detectedTargets) {
            double distance = Math.sqrt(
                Math.pow(target.centerX() - centerX, 2) + 
                Math.pow(target.centerY() - centerY, 2)
            );
            if (distance < minDistance) {
                minDistance = distance;
                closest = target;
            }
        }
        return closest;
    }
    
    public void setMinTargetSize(int size) { this.minTargetSize = size; }
    public void setMaxTargetSize(int size) { this.maxTargetSize = size; }
    public void setConfidenceThreshold(float threshold) { this.confidenceThreshold = threshold; }
    public int getTargetCount() { return detectedTargets.size(); }
    
    public static class Target {
        public float x, y, width, height, confidence;
        
        public Target(float x, float y, float width, float height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.confidence = 0.8f;
        }
        
        public float centerX() { return x + width / 2; }
        public float centerY() { return y + height / 2; }
        
        public void merge(Target other) {
            float newX = Math.min(x, other.x);
            float newY = Math.min(y, other.y);
            float newRight = Math.max(x + width, other.x + other.width);
            float newBottom = Math.max(y + height, other.y + other.height);
            x = newX;
            y = newY;
            width = newRight - newX;
            height = newBottom - newY;
            confidence = Math.max(confidence, other.confidence);
        }
    }
    
    public interface DetectionCallback {
        void onDetectionComplete(List<Target> targets);
    }
}
