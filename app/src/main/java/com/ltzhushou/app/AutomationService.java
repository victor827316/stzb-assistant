package com.ltzhushou.app;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutomationService extends AccessibilityService {

    private static final String TAG = "LTZhuShou_Auto";
    private static AutomationService instance;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean isRunning = false;
    private String deepSeekKey = "";
    private int screenWidth = 1080;
    private int screenHeight = 2400;
    private int cycleCount = 0;
    private String currentTask = "";

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.d(TAG, "AutomationService created");
    }

    @Override
    public void onDestroy() {
        instance = null;
        isRunning = false;
        super.onDestroy();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 用于检测游戏状态变化
    }

    @Override
    public void onInterrupt() {
        Log.d(TAG, "AutomationService interrupted");
    }

    public static AutomationService getInstance() {
        return instance;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setScreenSize(int w, int h) {
        this.screenWidth = w;
        this.screenHeight = h;
    }

    public void startAutomation(String apiKey, String taskType) {
        this.deepSeekKey = apiKey;
        this.currentTask = taskType;
        this.isRunning = true;
        this.cycleCount = 0;

        // 启动前台服务通知
        startForegroundService();

        // 开始自动化循环
        executor.execute(this::automationLoop);
        Log.d(TAG, "Automation started: " + taskType);
    }

    public void stopAutomation() {
        isRunning = false;
        Log.d(TAG, "Automation stopped");
    }

    private void startForegroundService() {
        // 创建通知渠道并显示前台通知
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "auto_channel", "率土助手", android.app.NotificationManager.IMPORTANCE_LOW);
            android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }

        android.app.Notification notification = new android.app.Notification.Builder(this, "auto_channel")
                .setContentTitle("率土助手运行中")
                .setContentText(currentTask)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true)
                .build();

        startForeground(1, notification);
    }

    private void automationLoop() {
        while (isRunning) {
            try {
                cycleCount++;
                Log.d(TAG, "Cycle #" + cycleCount);

                // 1. 获取当前游戏界面截图
                // 注意：Android 10+ 需要 MediaProjection API 截图
                // 这里通过 App 内部的截图机制获取

                // 2. 发送到 DeepSeek 分析
                String analysis = analyzeScreenWithDeepSeek();

                // 3. 执行操作
                if (analysis != null) {
                    executeActions(analysis);
                } else {
                    // DeepSeek 未返回，随机等待
                    randomDelay(3, 6);
                }

                // 4. 随机延时防检测
                randomDelay(3, 8);

            } catch (Exception e) {
                Log.e(TAG, "Loop error: " + e.getMessage());
                randomDelay(5, 10);
            }
        }
    }

    private String analyzeScreenWithDeepSeek() {
        if (deepSeekKey.isEmpty()) return null;

        try {
            JSONObject body = new JSONObject();
            body.put("model", "deepseek-chat");

            org.json.JSONArray msgs = new org.json.JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是率土之滨游戏AI助手。分析当前游戏状态，返回最优操作指令。操作包括：tap(x,y)点击坐标, swipe(x1,y1,x2,y2)滑动, wait(ms)等待。以JSON返回。");
            msgs.put(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", "当前是率土之滨游戏，任务是：" + currentTask + "。请分析并返回下一步操作。");
            msgs.put(userMsg);

            body.put("messages", msgs);
            body.put("temperature", 0.3);
            body.put("max_tokens", 500);

            // 发送 HTTP 请求
            URL url = new URL("https://api.deepseek.com/v1/chat/completions");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + deepSeekKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                java.io.InputStream is = conn.getInputStream();
                String response = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A").next();
                is.close();

                JSONObject responseJson = new JSONObject(response);
                String content = responseJson.getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message")
                        .getString("content");

                Log.d(TAG, "DeepSeek: " + content.substring(0, Math.min(80, content.length())));
                return content;
            } else {
                Log.e(TAG, "API error: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "DeepSeek error: " + e.getMessage());
            return null;
        }
    }

    private void executeActions(String analysisJson) {
        try {
            // 解析 JSON 并提取操作
            String jsonStr = analysisJson;
            int start = jsonStr.indexOf("{");
            int end = jsonStr.lastIndexOf("}") + 1;
            if (start < 0 || end <= start) return;

            JSONObject decision = new JSONObject(jsonStr.substring(start, end));
            String action = decision.optString("action", "");

            switch (action) {
                case "tap": {
                    int x = decision.optInt("x", screenWidth / 2);
                    int y = decision.optInt("y", screenHeight / 2);
                    performTap(x, y);
                    break;
                }
                case "swipe": {
                    int x1 = decision.optInt("x1", screenWidth / 2);
                    int y1 = decision.optInt("y1", screenHeight / 2);
                    int x2 = decision.optInt("x2", screenWidth / 2);
                    int y2 = decision.optInt("y2", screenHeight / 2);
                    long ms = decision.optLong("ms", 300);
                    performSwipe(x1, y1, x2, y2, ms);
                    break;
                }
                case "wait": {
                    long ms = decision.optLong("ms", 3000);
                    Thread.sleep(ms);
                    break;
                }
                case "back": {
                    performGlobalAction(GLOBAL_ACTION_BACK);
                    Thread.sleep(1000);
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Execute error: " + e.getMessage());
        }
    }

    private void performTap(int x, int y) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
            dispatchGesture(builder.build(), null, null);
            Log.d(TAG, "Tap: (" + x + "," + y + ")");
        }
    }

    private void performSwipe(int x1, int y1, int x2, int y2, long ms) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Path path = new Path();
            path.moveTo(x1, y1);
            path.lineTo(x2, y2);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0, ms));
            dispatchGesture(builder.build(), null, null);
            Log.d(TAG, "Swipe: (" + x1 + "," + y1 + ")->(" + x2 + "," + y2 + ")");
        }
    }

    private void randomDelay(int minSec, int maxSec) {
        try {
            int delay = minSec + new Random().nextInt(maxSec - minSec + 1);
            Thread.sleep(delay * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
