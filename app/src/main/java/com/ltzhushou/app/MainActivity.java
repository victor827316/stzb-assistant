package com.ltzhushou.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.app.ActivityManager;
import java.util.List;
import android.app.AlertDialog;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {

    private static final String TAG = "LTZhuShou";
    private static final int REQUEST_MEDIA_PROJECTION = 1001;
    private static final String GAME_PACKAGE = "com.netease.stzb.netease";

    private WebView webView;
    private MediaProjectionManager mediaProjectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth, screenHeight, screenDpi;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setUserAgentString(settings.getUserAgentString() + " LtZhuShou/1.0");

        // 添加 JavaScript 桥
        webView.addJavascriptInterface(new NativeBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        // 获取屏幕尺寸
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        wm.getDefaultDisplay().getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDpi = metrics.densityDpi;

        mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        // 加载PWA
        webView.loadUrl("file:///android_asset/index.html");
        Log.d(TAG, "App started: " + screenWidth + "x" + screenHeight);

        // 初始化 AutomationService 的屏幕尺寸
        AutomationService autoService = AutomationService.getInstance();
        if (autoService != null) {
            autoService.setScreenSize(screenWidth, screenHeight);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == RESULT_OK && data != null) {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
            Log.d(TAG, "MediaProjection granted");
            setupVirtualDisplay();

            // 通知 WebView 截图权限已获取
            webView.post(() -> webView.evaluateJavascript(
                    "if(window.onScreenCaptureReady) onScreenCaptureReady()", null));
        }
    }

    private void setupVirtualDisplay() {
        imageReader = ImageReader.newInstance(screenWidth, screenHeight, android.graphics.ImageFormat.JPEG, 2);
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "screen_capture", screenWidth, screenHeight, screenDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.getSurface(), null, null);
    }

    private byte[] captureScreenshot() {
        if (imageReader == null) return null;
        try {
            Image image = imageReader.acquireLatestImage();
            if (image == null) return null;

            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            image.close();

            // 转为JPEG
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) return null;

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos);
            return baos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "Screenshot error: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            moveTaskToBack(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (virtualDisplay != null) virtualDisplay.release();
        if (mediaProjection != null) mediaProjection.stop();
    }

    // ==================== JavaScript Bridge ====================
    public class NativeBridge {

        @JavascriptInterface
        public String getDeviceInfo() {
            try {
                JSONObject info = new JSONObject();
                info.put("width", screenWidth);
                info.put("height", screenHeight);
                info.put("model", Build.MODEL);
                info.put("sdk", Build.VERSION.SDK_INT);
                return info.toString();
            } catch (Exception e) {
                return "{}";
            }
        }

        @JavascriptInterface
        public String getGamePackage() {
            return GAME_PACKAGE;
        }

        @JavascriptInterface
        public void requestScreenshotPermission() {
            mainHandler.post(() -> {
                Intent intent = mediaProjectionManager.createScreenCaptureIntent();
                startActivityForResult(intent, REQUEST_MEDIA_PROJECTION);
            });
        }

        @JavascriptInterface
        public String takeScreenshot() {
            byte[] data = captureScreenshot();
            if (data != null) {
                return Base64.encodeToString(data, Base64.NO_WRAP);
            }
            return "";
        }

        @JavascriptInterface
        public void launchGame() {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(GAME_PACKAGE);
            if (launchIntent != null) {
                startActivity(launchIntent);
            }
        }

        @JavascriptInterface
        public boolean isGameRunning() {
            try {
                ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
                List<ActivityManager.AppTask> tasks = am.getAppTasks();
                // 简化检查
                return false;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public void requestAccessibilityService() {
            mainHandler.post(() -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
                startActivity(intent);
            });
        }

        @JavascriptInterface
        public boolean isAccessibilityServiceEnabled() {
            return AutomationService.getInstance() != null;
        }

        @JavascriptInterface
        public void startAutomation(final String apiKey, final String taskType) {
            AutomationService service = AutomationService.getInstance();
            if (service != null) {
                service.setScreenSize(screenWidth, screenHeight);
                service.startAutomation(apiKey, taskType);
            }
        }

        @JavascriptInterface
        public void stopAutomation() {
            AutomationService service = AutomationService.getInstance();
            if (service != null) {
                service.stopAutomation();
            }
        }

        @JavascriptInterface
        public boolean isAutomationRunning() {
            AutomationService service = AutomationService.getInstance();
            return service != null && service.isRunning();
        }

        @JavascriptInterface
        public String callDeepSeek(final String apiKey, final String prompt) {
            try {
                URL url = new URL("https://api.deepseek.com/v1/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(30000);
                conn.setReadTimeout(60000);

                JSONObject body = new JSONObject();
                body.put("model", "deepseek-chat");
                JSONArray messages = new JSONArray();

                JSONObject systemMsg = new JSONObject();
                systemMsg.put("role", "system");
                systemMsg.put("content", "你是率土之滨战略专家。回答简洁、实用、可操作。");
                messages.put(systemMsg);

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", prompt);
                messages.put(userMsg);

                body.put("messages", messages);
                body.put("temperature", 0.7);
                body.put("max_tokens", 2000);

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                if (code == 200) {
                    InputStream is = conn.getInputStream();
                    java.util.Scanner s = new java.util.Scanner(is, "UTF-8").useDelimiter("\\A");
                    String response = s.hasNext() ? s.next() : "";
                    is.close();

                    JSONObject json = new JSONObject(response);
                    return json.getJSONArray("choices")
                            .getJSONObject(0).getJSONObject("message")
                            .getString("content");
                } else {
                    return "API错误: " + code;
                }
            } catch (Exception e) {
                return "错误: " + e.getMessage();
            }
        }
    }
}
