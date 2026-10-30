package com.messenger.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String MESSENGER_URL = "http://89.125.121.18";
    private static final int REQUEST_MEDIA = 704;

    private WebView webView;
    private ValueCallback<Uri[]> fileCallback;
    private boolean chooserPending;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        // Keep the user's Android system theme unchanged.
        getWindow().setStatusBarColor(Color.rgb(18, 18, 22));
        getWindow().setNavigationBarColor(Color.rgb(18, 18, 22));
        getWindow().getDecorView().setSystemUiVisibility(0);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setAllowFileAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        if (Build.VERSION.SDK_INT >= 33) {
            settings.setAlgorithmicDarkeningAllowed(false);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            settings.setForceDark(WebSettings.FORCE_DARK_OFF);
        }

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient());
        webView.setDownloadListener((url, userAgent, disposition, mimeType, length) ->
                downloadFile(url, userAgent, disposition, mimeType));

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                cancelPendingChooser();
                fileCallback = callback;
                openSystemMediaPicker(params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                return true;
            }
        });

        webView.loadUrl(MESSENGER_URL);
    }

    /**
     * Use Android's Storage Access Framework instead of querying MediaStore ourselves.
     * It works across old and new Android versions, does not require broad media
     * permissions, and avoids loading every thumbnail on the UI thread.
     */
    private void openSystemMediaPicker(boolean allowMultiple) {
        if (chooserPending || fileCallback == null) return;
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES,
                    new String[]{"image/*", "video/*"});
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            chooserPending = true;
            startActivityForResult(intent, REQUEST_MEDIA);
        } catch (Exception error) {
            chooserPending = false;
            finishFileChooser(null);
            Toast.makeText(this, "Не удалось открыть выбор фото и видео",
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_MEDIA) return;

        chooserPending = false;
        if (resultCode != RESULT_OK || data == null) {
            finishFileChooser(null);
            return;
        }

        try {
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                Uri[] results = new Uri[count];
                for (int i = 0; i < count; i++) {
                    results[i] = data.getClipData().getItemAt(i).getUri();
                }
                finishFileChooser(results);
            } else if (data.getData() != null) {
                finishFileChooser(new Uri[]{data.getData()});
            } else {
                finishFileChooser(null);
            }
        } catch (Exception error) {
            finishFileChooser(null);
            Toast.makeText(this, "Не удалось получить выбранные файлы",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void finishFileChooser(Uri[] uris) {
        ValueCallback<Uri[]> callback = fileCallback;
        fileCallback = null;
        chooserPending = false;
        if (callback != null) callback.onReceiveValue(uris);
    }

    private void cancelPendingChooser() {
        if (fileCallback != null) finishFileChooser(null);
    }

    private void downloadFile(String url, String userAgent,
                              String contentDisposition, String mimeType) {
        try {
            DownloadManager.Request request =
                    new DownloadManager.Request(Uri.parse(url));
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            request.setTitle(name);
            request.setDescription("Загрузка из Мессенджера");
            request.setMimeType(mimeType);
            request.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS, name);

            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(true);

            DownloadManager manager =
                    (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(this, "Файл скачивается в папку «Загрузки»",
                        Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Не удалось запустить загрузку",
                        Toast.LENGTH_LONG).show();
            }
        } catch (Exception error) {
            Toast.makeText(this, "Ошибка загрузки файла", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        if (webView != null) webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        finishFileChooser(null);
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
}
