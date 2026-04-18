package com.deltaaim.detector;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

/**
 * Color Detection utility for finding targets based on color
 * Uses edge detection and color matching algorithms
 */
public class ColorDetector {
    private static final String TAG = "ColorDetector";
    
    // Color matching thresholds
    private int targetRed = 255;
    private int targetGreen = 0;
    private int targetBlue = 0;
    private int colorThreshold = 50;
    
    // Edge detection settings
    private int edgeThreshold = 30;
    private int minTargetSize = 20;
    
    public ColorDetector() {
        // Default constructor
    }
    
    /**
     * Set target color for detection
     */
    public void setTargetColor(int r, int g, int b) {
        this.targetRed = r;
        this.targetGreen = g;
        this.targetBlue = b;
        Log.d(TAG, "Target color set to RGB(" + r + ", " + g + ", " + b + ")");
    }
    
    /**
     * Set color matching threshold
     */
    public void setColorThreshold(int threshold) {
        this.colorThreshold = threshold;
    }
    
    /**
     * Find the center point of the target color in the bitmap
     * @param bitmap Screenshot bitmap
     * @return TargetPoint with coordinates, or null if not found
     */
    public TargetPoint findTarget(Bitmap bitmap) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null");
            return null;
        }
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        int sumX = 0, sumY = 0, count = 0;
        int minX = width, minY = height, maxX = 0, maxY = 0;
        
        // Scan bitmap for matching colors
        for (int y = 0; y < height; y += 2) { // Sample every 2 pixels for speed
            for (int x = 0; x < width; x += 2) {
                int pixel = bitmap.getPixel(x, y);
                
                if (isColorMatch(pixel)) {
                    sumX += x;
                    sumY += y;
                    count++;
                    
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        
        if (count > minTargetSize) {
            int centerX = sumX / count;
            int centerY = sumY / count;
            int targetWidth = maxX - minX;
            int targetHeight = maxY - minY;
            
            Log.d(TAG, "Target found at (" + centerX + ", " + centerY + 
                  ") with size " + targetWidth + "x" + targetHeight);
            
            return new TargetPoint(centerX, centerY, targetWidth, targetHeight);
        }
        
        Log.d(TAG, "No target found");
        return null;
    }
    
    /**
     * Check if a pixel color matches the target
     */
    private boolean isColorMatch(int pixel) {
        int r = Color.red(pixel);
        int g = Color.green(pixel);
        int b = Color.blue(pixel);
        
        return Math.abs(r - targetRed) <= colorThreshold &&
               Math.abs(g - targetGreen) <= colorThreshold &&
               Math.abs(b - targetBlue) <= colorThreshold;
    }
    
    /**
     * Detect edges in the bitmap and find target contours
     */
    public TargetPoint findTargetWithEdges(Bitmap bitmap) {
        if (bitmap == null) return null;
        
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        
        // First pass: find color matches
        boolean[][] matchMap = new boolean[width / 4 + 1][height / 4 + 1];
        int mapWidth = matchMap.length;
        int mapHeight = matchMap[0].length;
        
        for (int y = 0; y < height; y += 4) {
            for (int x = 0; x < width; x += 4) {
                matchMap[x / 4][y / 4] = isColorMatch(bitmap.getPixel(x, y));
            }
        }
        
        // Second pass: find largest connected region
        int maxSize = 0;
        int centerX = 0, centerY = 0;
        boolean[][] visited = new boolean[mapWidth][mapHeight];
        
        for (int y = 0; y < mapHeight; y++) {
            for (int x = 0; x < mapWidth; x++) {
                if (matchMap[x][y] && !visited[x][y]) {
                    // BFS to find connected region
                    int regionSize = 0;
                    int sumX = 0, sumY = 0;
                    
                    int[] queueX = new int[mapWidth * mapHeight];
                    int[] queueY = new int[mapWidth * mapHeight];
                    int head = 0, tail = 0;
                    
                    queueX[tail] = x;
                    queueY[tail] = y;
                    tail++;
                    visited[x][y] = true;
                    
                    while (head < tail) {
                        int cx = queueX[head];
                        int cy = queueY[head];
                        head++;
                        
                        regionSize++;
                        sumX += cx;
                        sumY += cy;
                        
                        // Check neighbors
                        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
                        for (int[] dir : dirs) {
                            int nx = cx + dir[0];
                            int ny = cy + dir[1];
                            if (nx >= 0 && nx < mapWidth && ny >= 0 && ny < mapHeight &&
                                matchMap[nx][ny] && !visited[nx][ny]) {
                                visited[nx][ny] = true;
                                queueX[tail] = nx;
                                queueY[tail] = ny;
                                tail++;
                            }
                        }
                    }
                    
                    if (regionSize > maxSize) {
                        maxSize = regionSize;
                        centerX = sumX * 4 / regionSize;
                        centerY = sumY * 4 / regionSize;
                    }
                }
            }
        }
        
        if (maxSize > minTargetSize / 4) {
            return new TargetPoint(centerX, centerY, 0, 0);
        }
        
        return null;
    }
    
    /**
     * Target point data class
     */
    public static class TargetPoint {
        public final int x;
        public final int y;
        public final int width;
        public final int height;
        
        public TargetPoint(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
        
        public int getCenterX() {
            return x;
        }
        
        public int getCenterY() {
            return y;
        }
    }
}
