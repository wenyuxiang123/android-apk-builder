package com.deltaaim.core;

import android.graphics.PointF;
import android.util.Log;

import java.util.List;
import java.util.Random;

/**
 * 仿生瞄准控制器 - 模拟人眼运动机制
 * 
 * 核心特性：
 * 1. Saccade（扫视）：目标切换时快速跳变
 * 2. Smooth Pursuit（平滑追踪）：跟踪时匀速移动
 * 3. Micro-saccades（微颤）：自然抖动赋予"生命感"
 */
public class BionicAimController {
    private static final String TAG = "BionicAim";
    
    // 运动模式
    public enum MotionMode {
        SACCADE,          // 扫视模式（快速切换目标）
        SMOOTH_PURSUIT,   // 平滑追踪模式
        IDLE              // 空闲
    }
    
    // 参数配置
    private static class Config {
        // 扫视参数
        static final float SACCADE_MAX_SPEED = 15000;     // 扫视最大速度 (像素/秒)
        static final float SACCADE_MIN_SPEED = 500;       // 扫视最小速度
        static final float SACCADE_DURATION = 0.08f;      // 扫视持续时间 (秒)
        static final float SACCADE_ACCELERATION = 50000;  // 扫视加速度
        
        // 平滑追踪参数
        static final float PURSUIT_GAIN = 0.15f;          // 追踪增益
        static final float PURSUIT_MAX_SPEED = 2000;      // 平滑追踪最大速度
        static final float PURSUIT_PREDICTION_TIME = 0.1f; // 预测时间 (秒)
        
        // 微颤参数
        static final float MICRO_FREQUENCY = 1.5f;        // 微颤频率 (Hz)
        static final float MICRO_AMPLITUDE = 2.0f;        // 微颤幅度 (像素)
        static final float MICRO_RANDOMNESS = 0.3f;       // 微颤随机性
        
        // 目标切换阈值
        static final float TARGET_SWITCH_THRESHOLD = 100; // 目标切换距离阈值
        
        // 安全参数
        static final float SAFE_ZONE_RADIUS = 5;          // 安全区半径
    }
    
    private MotionMode currentMode = MotionMode.IDLE;
    private PointF currentTarget = new PointF();
    private PointF previousTarget = new PointF();
    private PointF currentAim = new PointF();
    private PointF aimVelocity = new PointF();
    
    // 微颤状态
    private long microSaccadeStartTime = 0;
    private float microSaccadePhase = 0;
    private Random random = new Random();
    
    // 目标历史（用于运动预测）
    private PointF[] targetHistory = new PointF[5];
    private int historyIndex = 0;
    private long[] timeHistory = new long[5];
    
    // 扫视状态
    private long saccadeStartTime = 0;
    private PointF saccadeStart = new PointF();
    private PointF saccadeEnd = new PointF();
    private boolean saccadeInProgress = false;
    
    // 追踪状态
    private float smoothGain = Config.PURSUIT_GAIN;
    
    public BionicAimController() {
        for (int i = 0; i < targetHistory.length; i++) {
            targetHistory[i] = new PointF();
            timeHistory[i] = 0;
        }
    }
    
    /**
     * 更新瞄准点
     * @param detections 检测结果列表
     * @param screenWidth 屏幕宽度
     * @param screenHeight 屏幕高度
     * @param deltaTime 帧间隔（秒）
     */
    public PointF update(List<YOLODetector.Detection> detections, 
                         int screenWidth, int screenHeight, float deltaTime) {
        if (detections == null || detections.isEmpty()) {
            // 无目标，进入空闲模式
            currentMode = MotionMode.IDLE;
            applyMicroSaccade(deltaTime);
            return currentAim;
        }
        
        // 选择最优目标
        YOLODetector.Detection bestTarget = selectBestTarget(detections, screenWidth, screenHeight);
        if (bestTarget == null) {
            return currentAim;
        }
        
        // 更新目标位置
        PointF newTarget = new PointF(bestTarget.getAimX(), bestTarget.getAimY());
        
        // 判断是否需要目标切换（扫视）
        float targetDistance = distance(newTarget, currentTarget);
        
        if (targetDistance > Config.TARGET_SWITCH_THRESHOLD || saccadeInProgress) {
            // 目标切换 - 启动扫视
            startSaccade(newTarget);
        } else {
            // 同一目标追踪 - 平滑追踪
            updateSmoothPursuit(newTarget, deltaTime);
        }
        
        // 应用微颤
        applyMicroSaccade(deltaTime);
        
        // 记录目标历史
        recordTargetHistory(newTarget);
        
        return currentAim;
    }
    
    /**
     * 选择最优目标
     * 优先级：距离中心近 > 置信度高 > 敌人优先
     */
    private YOLODetector.Detection selectBestTarget(List<YOLODetector.Detection> detections,
                                                     int screenWidth, int screenHeight) {
        float centerX = screenWidth / 2f;
        float centerY = screenHeight / 2f;
        
        YOLODetector.Detection best = null;
        float bestScore = Float.MAX_VALUE;
        
        for (YOLODetector.Detection det : detections) {
            if (!det.isEnemy) continue;  // 只瞄准敌人
            
            float distanceToCenter = distance(new PointF(det.getAimX(), det.getAimY()),
                                              new PointF(centerX, centerY));
            
            // 综合评分：距离 + 置信度 + 头部优先
            float score = distanceToCenter / 1000f 
                        - det.confidence * 50
                        - (det.classId == 0 ? 100 : 0);  // 头部优先
            
            if (score < bestScore) {
                bestScore = score;
                best = det;
            }
        }
        
        return best;
    }
    
    /**
     * 启动扫视
     */
    private void startSaccade(PointF target) {
        if (!saccadeInProgress) {
            saccadeInProgress = true;
            saccadeStartTime = System.currentTimeMillis();
            saccadeStart.set(currentAim);
            saccadeEnd.set(target);
            currentMode = MotionMode.SACCADE;
        }
        
        // 执行扫视动画
        float elapsed = (System.currentTimeMillis() - saccadeStartTime) / 1000f;
        float progress = Math.min(1f, elapsed / Config.SACCADE_DURATION);
        
        // 使用ease-out曲线
        float eased = easeOutQuad(progress);
        
        // 计算当前位置
        currentAim.x = saccadeStart.x + (saccadeEnd.x - saccadeStart.x) * eased;
        currentAim.y = saccadeStart.y + (saccadeEnd.y - saccadeStart.y) * eased;
        
        if (progress >= 1f) {
            saccadeInProgress = false;
            currentTarget.set(target);
            currentMode = MotionMode.SMOOTH_PURSUIT;
        }
    }
    
    /**
     * 平滑追踪更新
     */
    private void updateSmoothPursuit(PointF target, float deltaTime) {
        currentMode = MotionMode.SMOOTH_PURSUIT;
        
        // 运动预测
        PointF predictedTarget = predictTargetPosition(target);
        
        // 计算误差
        float errorX = predictedTarget.x - currentAim.x;
        float errorY = predictedTarget.y - currentAim.y;
        
        // PID控制（简化版）
        float velocityX = errorX * smoothGain;
        float velocityY = errorY * smoothGain;
        
        // 限制最大速度
        float speed = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY);
        if (speed > Config.PURSUIT_MAX_SPEED) {
            velocityX = velocityX / speed * Config.PURSUIT_MAX_SPEED;
            velocityY = velocityY / speed * Config.PURSUIT_MAX_SPEED;
        }
        
        // 平滑速度变化
        aimVelocity.x = aimVelocity.x * 0.7f + velocityX * 0.3f;
        aimVelocity.y = aimVelocity.y * 0.7f + velocityY * 0.3f;
        
        // 更新位置
        currentAim.x += aimVelocity.x * deltaTime;
        currentAim.y += aimVelocity.y * deltaTime;
        
        previousTarget.set(currentTarget);
        currentTarget.set(target);
    }
    
    /**
     * 运动预测
     */
    private PointF predictTargetPosition(PointF currentTarget) {
        if (historyIndex < 2) {
            return currentTarget;
        }
        
        // 计算速度
        int prevIndex = (historyIndex - 2 + targetHistory.length) % targetHistory.length;
        PointF prevPos = targetHistory[prevIndex];
        long prevTime = timeHistory[prevIndex];
        long currTime = timeHistory[(historyIndex - 1 + targetHistory.length) % targetHistory.length];
        
        if (currTime == prevTime) return currentTarget;
        
        float dt = (currTime - prevTime) / 1000f;
        float vx = (currentTarget.x - prevPos.x) / dt;
        float vy = (currentTarget.y - prevPos.y) / dt;
        
        // 预测未来位置
        PointF predicted = new PointF();
        predicted.x = currentTarget.x + vx * Config.PURSUIT_PREDICTION_TIME;
        predicted.y = currentTarget.y + vy * Config.PURSUIT_PREDICTION_TIME;
        
        return predicted;
    }
    
    /**
     * 应用微颤
     */
    private void applyMicroSaccade(float deltaTime) {
        long currentTime = System.currentTimeMillis();
        microSaccadePhase += deltaTime * Config.MICRO_FREQUENCY * 2 * (float) Math.PI;
        
        // 正弦波基础 + 随机扰动
        float baseNoise = (float) Math.sin(microSaccadePhase);
        float randomNoise = (random.nextFloat() - 0.5f) * Config.MICRO_RANDOMNESS;
        
        float offsetX = (baseNoise + randomNoise) * Config.MICRO_AMPLITUDE;
        float offsetY = (baseNoise * 0.7f + randomNoise) * Config.MICRO_AMPLITUDE * 0.8f;
        
        currentAim.x += offsetX * deltaTime * 60;
        currentAim.y += offsetY * deltaTime * 60;
    }
    
    /**
     * 记录目标历史
     */
    private void recordTargetHistory(PointF target) {
        targetHistory[historyIndex].set(target);
        timeHistory[historyIndex] = System.currentTimeMillis();
        historyIndex = (historyIndex + 1) % targetHistory.length;
    }
    
    /**
     * 缓动函数 - easeOutQuad
     */
    private float easeOutQuad(float t) {
        return 1 - (1 - t) * (1 - t);
    }
    
    /**
     * 计算两点距离
     */
    private float distance(PointF a, PointF b) {
        float dx = a.x - b.x;
        float dy = a.y - b.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
    
    public MotionMode getCurrentMode() {
        return currentMode;
    }
    
    public PointF getCurrentAim() {
        return currentAim;
    }
}
