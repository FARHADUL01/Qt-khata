package com.qtkhata.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private static final String HOME_URL = "https://qt.trymail226.workers.dev";
    private WebView webView;
    private TextView statusView;
    
    private ValueCallback<Uri[]> filePathCallback;
    private static final int FILE_CHOOSER_REQUEST_CODE = 10001;
    private static final int REQ_CAMERA = 10002;
    private static final int REQ_LOCATION = 10003;
    
    private PermissionRequest pendingCamera;
    private GeolocationPermissions.Callback pendingGeoCb;
    private String pendingGeoOrigin;
    private static final Pattern SAFE_JS_NAME = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$.]{0,80}$");

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        statusView = new TextView(this);
        statusView.setText("লোড হচ্ছে...");
        statusView.setTextSize(16);
        statusView.setGravity(android.view.Gravity.CENTER);
        statusView.setVisibility(View.GONE);

        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        root.addView(statusView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        
        webView.addJavascriptInterface(new WebAppInterface(), "Android");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView view, String url, String message, final JsResult result) {
                new AlertDialog.Builder(MainActivity.this).setMessage(message)
                        .setPositiveButton(android.R.string.ok, (d, w) -> result.confirm())
                        .setNegativeButton(android.R.string.cancel, (d, w) -> result.cancel())
                        .setOnCancelListener(d -> result.cancel()).show();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) MainActivity.this.filePathCallback.onReceiveValue(null);
                MainActivity.this.filePathCallback = filePathCallback;
                try { startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST_CODE); } 
                catch (Exception e) { MainActivity.this.filePathCallback = null; return false; }
                return true;
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                boolean wantsCamera = false;
                for (String r : request.getResources()) if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(r)) wantsCamera = true;
                if (!wantsCamera) { request.deny(); return; }
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                } else {
                    pendingCamera = request;
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CAMERA}, REQ_CAMERA);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) { statusView.setVisibility(View.GONE); }
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) { statusView.setVisibility(View.VISIBLE); }
        });

        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                if (url.startsWith("blob:")) {
                    String type = (mimetype == null) ? "application/octet-stream" : mimetype;
                    String js = "(function(){fetch(" + JSONObject.quote(url) + ").then(r=>r.blob()).then(b=>{var fr=new FileReader();fr.onload=function(){Android.saveBase64(String(fr.result).split(',')[1]," + JSONObject.quote("QTKhata-Backup.json") + "," + JSONObject.quote(type) + ");};fr.readAsDataURL(b);});})();";
                    webView.evaluateJavascript(js, null);
                    return;
                }
                try {
                    DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                    request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "QTKhata/" + System.currentTimeMillis() + ".json");
                    ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                    Toast.makeText(MainActivity.this, "ডাউনলোড শুরু হয়েছে", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {}
            }
        });

        if (savedInstanceState != null) webView.restoreState(savedInstanceState);
        if (webView.getUrl() == null) webView.loadUrl(HOME_URL);
    }

    public class WebAppInterface {
        @JavascriptInterface
        public void allowScreenshot() { runOnUiThread(() -> getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE)); }
        @JavascriptInterface
        public void blockScreenshot() { runOnUiThread(() -> getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)); }
        @JavascriptInterface
        public void saveImageToGallery(String base64, String fileName) { saveAsync(base64, safeName(fileName), "image/png", true); }
        @JavascriptInterface
        public void saveBase64(String base64, String fileName, String mime) { saveAsync(base64, safeName(fileName), mime, mime.startsWith("image/")); }
        @JavascriptInterface
        public boolean isBiometricAvailable() { return biometricAvailable(); }
        @JavascriptInterface
        public void authenticate(String callback) {
            if (callback != null && SAFE_JS_NAME.matcher(callback).matches()) runOnUiThread(() -> showBiometric(callback));
        }
    }

    private void saveAsync(String base64, String name, String mime, boolean image) {
        new Thread(() -> {
            boolean ok = false;
            try { ok = writeFile(Base64.decode(base64, Base64.DEFAULT), name, mime, image); } catch (Exception e) {}
            final boolean finalOk = ok;
            runOnUiThread(() -> {
                if (webView != null) webView.evaluateJavascript("if(window.__androidSaveResult)window.__androidSaveResult(" + finalOk + ")", null);
                Toast.makeText(MainActivity.this, finalOk ? "সেভ হয়েছে" : "সেভ করা যায়নি", Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private boolean writeFile(byte[] data, String name, String mime, boolean image) throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, image ? "Pictures/QTKhata" : "Download/QTKhata");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri item = cr.insert(image ? MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY) : MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), v);
            if (item == null) return false;
            try (OutputStream out = cr.openOutputStream(item)) { if (out == null) return false; out.write(data); }
            v.clear(); v.put(MediaStore.MediaColumns.IS_PENDING, 0); cr.update(item, v, null, null);
            return true;
        }
        File dir = new File(Environment.getExternalStoragePublicDirectory(image ? Environment.DIRECTORY_PICTURES : Environment.DIRECTORY_DOWNLOADS), "QTKhata");
        if (!dir.exists() && !dir.mkdirs()) return false;
        File f = new File(dir, name);
        try (FileOutputStream fo = new FileOutputStream(f)) { fo.write(data); }
        MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()}, new String[]{mime}, null);
        return true;
    }

    private String safeName(String n) { return n.replaceAll("[^A-Za-z0-9._-]", "_"); }
    private boolean biometricAvailable() { return BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS; }

    private void showBiometric(String callback) {
        if (!biometricAvailable()) { if(webView!=null) webView.evaluateJavascript("window."+callback+"(false)", null); return; }
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("QT Khata").setSubtitle("আঙুলের ছাপ দিয়ে খুলুন").setNegativeButtonText("বাতিল")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build();
        new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) { if(webView!=null) webView.evaluateJavascript("window."+callback+"(true)", null); }
            @Override public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) { if(webView!=null) webView.evaluateJavascript("window."+callback+"(false)", null); }
        }).authenticate(info);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CAMERA && pendingCamera != null) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) pendingCamera.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            else pendingCamera.deny();
            pendingCamera = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST_CODE) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null) { String d = data.getDataString(); if (d != null) results = new Uri[]{Uri.parse(d)}; }
            filePathCallback.onReceiveValue(results); filePathCallback = null;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView != null && webView.canGoBack()) { webView.goBack(); return true; }
        return super.onKeyDown(keyCode, event);
    }
                                       }
