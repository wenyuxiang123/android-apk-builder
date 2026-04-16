package com.deltaaim.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.util.Log;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * YOLOv8目标检测器 - TFLite实现
 * 模型：YOLOv8n量化版（INT8）
 */
public class YOLODetector {
    private static final String TAG = "YOLODetector";
    
    // 模型参数
    private static final int INPUT_SIZE = 320;  // 输入尺寸（性能优化）
    private static final float CONFIDENCE_THRESHOLD = 0.45f;
    private static final float NMS_THRESHOLD = 0.5f;
    
    // 类别标签（三角洲行动角色）
    private static final String[] CLASSES = {
        "enemy_head",      // 敌人头部
        "enemy_body",      // 敌人身体
        "enemy_full",      // 敌人全身
        "teammate",        // 队友
        "vehicle"          // 载具
    };
    
    private Interpreter interpreter;
    private GpuDelegate gpuDelegate;
    private ByteBuffer inputBuffer;
    private float[][][] outputBuffer;
    
    private int outputWidth;
    private int outputHeight;
    
    public YOLODetector(Context context) {
        try {
            Interpreter.Options options = new Interpreter.Options();
            
            // GPU加速
            CompatibilityList compatList = new CompatibilityList();
            if (compatList.isDelegateSupportedOnThisDevice()) {
                gpuDelegate = new GpuDelegate(compatList.getBestOptionsForThisDevice());
                options.addDelegate(gpuDelegate);
                Log.d(TAG, "GPU加速已启用");
            } else {
                // CPU多线程
                options.setNumThreads(4);
                Log.d(TAG, "使用CPU模式");
            }
            
            // 加载模型
            MappedByteBuffer modelBuffer = loadModelFile(context, "delta_aim_model.tflite");
            interpreter = new Interpreter(modelBuffer, options);
            
            // 初始化缓冲区
            inputBuffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4);
            inputBuffer.order(ByteOrder.nativeOrder());
            
            // 获取输出形状
            int[] outputShape = interpreter.getOutputTensor(0).shape();
            outputWidth = outputShape[2];
            outputHeight = outputShape[1];
            outputBuffer = new float[1][outputHeight][outputWidth];
            
            Log.d(TAG, "模型加载成功，输出尺寸: " + outputWidth + "x" + outputHeight);
            
        } catch (Exception e) {
            Log.e(TAG, "模型加载失败: " + e.getMessage());
        }
    }
    
    /**
     * 执行检测
     */
    public List<Detection> detect(Bitmap bitmap) {
        if (interpreter == null) {
            return new ArrayList<>();
        }
        
        long startTime = System.currentTimeMillis();
        
        // 预处理
        Bitmap resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);
        preprocessBitmap(resized);
        
        // 推理
        interpreter.run(inputBuffer, outputBuffer);
        
        // 后处理
        List<Detection> detections = postprocess(bitmap.getWidth(), bitmap.getHeight());
        
        long elapsed = System.currentTimeMillis() - startTime;
        Log.d(TAG, "推理耗时: " + elapsed + "ms");
        
        return detections;
    }
    
    /**
     * 预处理Bitmap
     */
    private void preprocessBitmap(Bitmap bitmap) {
        inputBuffer.rewind();
        
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        bitmap.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        
        for (int pixel : pixels) {
            // 归一化到 [0, 1]
            float r = ((pixel >> 16) & 0xFF) / 255.0f;
            float g = ((pixel >> 8) & 0xFF) / 255.0f;
            float b = (pixel & 0xFF) / 255.0f;
            
            inputBuffer.putFloat(r);
            inputBuffer.putFloat(g);
            inputBuffer.putFloat(b);
        }
    }
    
    /**
     * 后处理 - NMS和坐标映射
     */
    private List<Detection> postprocess(int originalWidth, int originalHeight) {
        List<Detection> allDetections = new ArrayList<>();
        
        float scaleX = (float) originalWidth / INPUT_SIZE;
        float scaleY = (float) originalHeight / INPUT_SIZE;
        
        // 解析输出
        for (int i = 0; i < outputHeight; i++) {
            for (int j = 0; j < outputWidth; j++) {
                float[] row = outputBuffer[0][i];
                
                // YOLOv8输出格式：[x, y, w, h, conf, class_scores...]
                float x = row[j * 6];
                float y = row[j * 6 + 1];
                float w = row[j * 6 + 2];
                float h = row[j * 6 + 3];
                float conf = row[j * 6 + 4];
                
                if (conf > CONFIDENCE_THRESHOLD) {
                    int classId = 0;
                    float maxClassScore = 0;
                    for (int c = 0; c < CLASSES.length; c++) {
                        float classScore = row[j * 6 + 5 + c];
                        if (classScore > maxClassScore) {
                            maxClassScore = classScore;
                            classId = c;
                        }
                    }
                    
                    // 坐标转换
                    float left = (x - w / 2) * scaleX;
                    float top = (y - h / 2) * scaleY;
                    float right = (x + w / 2) * scaleX;
                    float bottom = (y + h / 2) * scaleY;
                    
                    Detection det = new Detection();
                    det.rect = new RectF(left, top, right, bottom);
                    det.confidence = conf * maxClassScore;
                    det.classId = classId;
                    det.className = CLASSES[classId];
                    det.isEnemy = (classId < 3);  // 前3类是敌人
                    
                    allDetections.add(det);
                }
            }
        }
        
        // NMS
        return applyNMS(allDetections);
    }
    
    /**
     * 非极大值抑制
     */
    private List<Detection> applyNMS(List<Detection> detections) {
        List<Detection> result = new ArrayList<>();
        
        // 按置信度排序
        detections.sort((a, b) -> Float.compare(b.confidence, a.confidence));
        
        boolean[] suppressed = new boolean[detections.size()];
        
        for (int i = 0; i < detections.size(); i++) {
            if (suppressed[i]) continue;
            
            Detection det = detections.get(i);
            result.add(det);
            
            for (int j = i + 1; j < detections.size(); j++) {
                if (suppressed[j]) continue;
                
                float iou = calculateIoU(det.rect, detections.get(j).rect);
                if (iou > NMS_THRESHOLD) {
                    suppressed[j] = true;
                }
            }
        }
        
        return result;
    }
    
    private float calculateIoU(RectF a, RectF b) {
        float intersectionLeft = Math.max(a.left, b.left);
        float intersectionTop = Math.max(a.top, b.top);
        float intersectionRight = Math.min(a.right, b.right);
        float intersectionBottom = Math.min(a.bottom, b.bottom);
        
        if (intersectionRight < intersectionLeft || intersectionBottom < intersectionTop) {
            return 0f;
        }
        
        float intersectionArea = (intersectionRight - intersectionLeft) * 
                                  (intersectionBottom - intersectionTop);
        float areaA = a.width() * a.height();
        float areaB = b.width() * b.height();
        
        return intersectionArea / (areaA + areaB - intersectionArea);
    }
    
    private MappedByteBuffer loadModelFile(Context context, String modelPath) throws IOException {
        java.io.File modelFile = new java.io.File(context.getFilesDir(), modelPath);
        FileInputStream inputStream = new FileInputStream(modelFile);
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = 0;
        long declaredLength = fileChannel.size();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
    
    public void close() {
        if (interpreter != null) {
            interpreter.close();
        }
        if (gpuDelegate != null) {
            gpuDelegate.close();
        }
    }
    
    /**
     * 检测结果类
     */
    public static class Detection {
        public RectF rect;
        public float confidence;
        public int classId;
        public String className;
        public boolean isEnemy;
        
        /**
         * 获取目标中心点
         */
        public float getCenterX() {
            return rect.centerX();
        }
        
        public float getCenterY() {
            return rect.centerY();
        }
        
        /**
         * 获取瞄准点（头部优先）
         */
        public float getAimX() {
            return rect.centerX();
        }
        
        public float getAimY() {
            // 头部位置：框的上1/4处
            return rect.top + rect.height() * 0.25f;
        }
    }
}
