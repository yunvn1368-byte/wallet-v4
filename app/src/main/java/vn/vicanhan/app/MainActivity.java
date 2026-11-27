package vn.vicanhan.app;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.biometrics.BiometricManager;
import android.hardware.biometrics.BiometricPrompt;
import android.os.Build;
import android.os.CancellationSignal;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private static final int PICK_IMAGE = 1;
    private WebView web;
    private ValueCallback<Uri[]> fileCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0A0A0C"));
        getWindow().setNavigationBarColor(Color.parseColor("#0A0A0C"));
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0A0A0C"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // lưu dữ liệu trong máy (localStorage)
        s.setAllowFileAccess(true);
        s.setTextZoom(100);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            // Cho phép chọn ảnh từ máy để làm hình nền
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                try {
                    startActivityForResult(Intent.createChooser(i, "Chọn ảnh nền"), PICK_IMAGE);
                } catch (Exception e) {
                    fileCallback = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PICK_IMAGE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.evaluateJavascript("window.onAppPause && window.onAppPause()", null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.evaluateJavascript("window.onAppResume && window.onAppResume()", null);
    }

    @Override
    public void onBackPressed() {
        web.evaluateJavascript("window.onAndroidBack && window.onAndroidBack()", value -> {
            if (!"true".equals(value)) MainActivity.super.onBackPressed();
        });
    }

    // ----- Vân tay -----
    private boolean bioAvailable() {
        if (Build.VERSION.SDK_INT >= 29) {
            BiometricManager bm = getSystemService(BiometricManager.class);
            return bm != null && bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS;
        }
        if (Build.VERSION.SDK_INT >= 28) {
            return getPackageManager().hasSystemFeature(PackageManager.FEATURE_FINGERPRINT);
        }
        return false;
    }

    @android.annotation.TargetApi(28)
    private void startBio() {
        try {
            BiometricPrompt prompt = new BiometricPrompt.Builder(this)
                    .setTitle("Mở Ví yun0213")
                    .setSubtitle("Đặt ngón tay lên cảm biến vân tay")
                    .setNegativeButton("Dùng mật khẩu", getMainExecutor(), (dialog, which) -> sendBio(false))
                    .build();
            prompt.authenticate(new CancellationSignal(), getMainExecutor(), new BiometricPrompt.AuthenticationCallback() {
                @Override
                public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    sendBio(true);
                }

                @Override
                public void onAuthenticationError(int errorCode, CharSequence errString) {
                    sendBio(false);
                }
            });
        } catch (Exception e) {
            sendBio(false);
        }
    }

    private void sendBio(boolean ok) {
        runOnUiThread(() -> web.evaluateJavascript("window.onBioResult && window.onBioResult(" + ok + ")", null));
    }

    class Bridge {
        @JavascriptInterface
        public boolean bioAvailable() {
            return MainActivity.this.bioAvailable();
        }

        @JavascriptInterface
        public void bioAuth() {
            if (Build.VERSION.SDK_INT < 28) {
                sendBio(false);
                return;
            }
            runOnUiThread(() -> startBio());
        }

        @JavascriptInterface
        public void share(String text, String title) {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, title);
            i.putExtra(Intent.EXTRA_TEXT, text);
            startActivity(Intent.createChooser(i, title));
        }

        @JavascriptInterface
        public String version() {
            try {
                return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void copy(String text) {
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("yun0213", text));
        }
    }
}
