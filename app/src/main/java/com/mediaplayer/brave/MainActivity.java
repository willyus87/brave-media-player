package com.mediaplayer.brave;

import android.annotation.SuppressLint;
import android.app.PictureInPictureParams;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Rational;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.ByteArrayInputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private ProgressBar progressBar;
    private EditText urlBar;
    private FrameLayout fullscreenContainer;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private MediaPlaybackService mediaService;
    private boolean isBound = false;

    // ─── Dominios bloqueados (adblocker básico integrado) ───────────────────
    private static final Set<String> BLOCKED_DOMAINS = new HashSet<>(Arrays.asList(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "ads.youtube.com", "pagead2.googlesyndication.com",
        "static.doubleclick.net", "securepubads.g.doubleclick.net",
        "tpc.googlesyndication.com", "youtube-nocookie.com",
        "imasdk.googleapis.com",  // SDK de anuncios de YouTube
        "googletagmanager.com", "google-analytics.com", "googletagservices.com",
        "adsystem.amazon.com", "advertising.amazon.com",
        "scorecardresearch.com", "quantserve.com", "outbrain.com",
        "taboola.com", "criteo.com", "adsrvr.org", "rubiconproject.com",
        "openx.net", "pubmatic.com", "appnexus.com", "adnxs.com",
        "moatads.com", "amazon-adsystem.com", "aax-us-east.amazon-adsystem.com"
    ));

    // User-Agent de Chrome en Android para compatibilidad máxima
    private static final String USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36";

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MediaPlaybackService.LocalBinder binder = (MediaPlaybackService.LocalBinder) service;
            mediaService = binder.getService();
            isBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            isBound = false;
        }
    };

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Pantalla siempre activa mientras la app está en primer plano
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);

        urlBar = findViewById(R.id.url_bar);
        progressBar = findViewById(R.id.progress_bar);
        fullscreenContainer = findViewById(R.id.fullscreen_container);
        webView = findViewById(R.id.web_view);

        setupWebView();
        setupUrlBar();
        setupNavigationButtons();
        startAndBindService();
        requestNotificationPermission();

        // Manejar intent externo (abrir URL de YouTube desde otra app)
        handleIntent(getIntent());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // Configuración esencial
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false); // Reproducción automática
        settings.setUserAgentString(USER_AGENT);

        // Mejorar compatibilidad y rendimiento
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Habilitar cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        // Interfaz JavaScript para comunicación nativa ↔ web
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebViewClient(new AdBlockingWebViewClient());
        webView.setWebChromeClient(new FullscreenWebChromeClient());
        webView.setBackgroundColor(Color.BLACK);

        // Cargar YouTube directamente
        webView.loadUrl("https://m.youtube.com");
    }

    private void setupUrlBar() {
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            String input = urlBar.getText().toString().trim();
            if (!input.isEmpty()) {
                if (!input.startsWith("http://") && !input.startsWith("https://")) {
                    // Buscar en YouTube si no es URL completa
                    if (!input.contains(".")) {
                        input = "https://m.youtube.com/results?search_query=" +
                                Uri.encode(input);
                    } else {
                        input = "https://" + input;
                    }
                }
                webView.loadUrl(input);
            }
            return true;
        });
    }

    private void setupNavigationButtons() {
        ImageButton btnBack = findViewById(R.id.btn_back);
        ImageButton btnForward = findViewById(R.id.btn_forward);
        ImageButton btnHome = findViewById(R.id.btn_home);
        ImageButton btnPip = findViewById(R.id.btn_pip);

        btnBack.setOnClickListener(v -> {
            if (webView.canGoBack()) webView.goBack();
        });

        btnForward.setOnClickListener(v -> {
            if (webView.canGoForward()) webView.goForward();
        });

        btnHome.setOnClickListener(v -> webView.loadUrl("https://m.youtube.com"));

        btnPip.setOnClickListener(v -> enterPictureInPicture());
    }

    private void startAndBindService() {
        Intent serviceIntent = new Intent(this, MediaPlaybackService.class);
        startForegroundService(serviceIntent);
        bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE);
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    private void handleIntent(Intent intent) {
        if (intent != null && Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            if (data != null) {
                String url = data.toString();
                webView.loadUrl(url);
                urlBar.setText(url);
            }
        }
    }

    // ─── Picture-in-Picture (minimizado con video visible) ──────────────────
    private void enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams.Builder pipBuilder =
                    new PictureInPictureParams.Builder();
                pipBuilder.setAspectRatio(new Rational(16, 9));
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    pipBuilder.setAutoEnterEnabled(true);
                    pipBuilder.setSeamlessResizeEnabled(true);
                }
                enterPictureInPictureMode(pipBuilder.build());
            } catch (Exception e) {
                Toast.makeText(this, "PiP no disponible en este dispositivo",
                    Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
        // Auto-entrar en PiP al presionar el botón Home durante reproducción
        enterPictureInPicture();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPiPMode) {
        super.onPictureInPictureModeChanged(isInPiPMode);
        if (isInPiPMode) {
            // Ocultar controles en modo PiP
            findViewById(R.id.toolbar).setVisibility(View.GONE);
            findViewById(R.id.navigation_bar).setVisibility(View.GONE);
        } else {
            // Mostrar controles al salir de PiP
            findViewById(R.id.toolbar).setVisibility(View.VISIBLE);
            findViewById(R.id.navigation_bar).setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (customView != null) {
                // Salir de fullscreen
                if (customViewCallback != null) customViewCallback.onCustomViewHidden();
                return true;
            }
            if (webView.canGoBack()) {
                webView.goBack();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onDestroy() {
        if (isBound) {
            unbindService(serviceConnection);
            isBound = false;
        }
        webView.destroy();
        super.onDestroy();
    }

    // ════════════════════════════════════════════════════════════════════════
    // ADBLOCKER: WebViewClient con bloqueo de dominios publicitarios
    // ════════════════════════════════════════════════════════════════════════
    private class AdBlockingWebViewClient extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view,
                                                           WebResourceRequest request) {
            String url = request.getUrl().toString();
            String host = request.getUrl().getHost();

            if (host != null && shouldBlock(host, url)) {
                // Retornar respuesta vacía (bloquear el recurso)
                return new WebResourceResponse("text/plain", "utf-8",
                        new ByteArrayInputStream("".getBytes()));
            }
            return super.shouldInterceptRequest(view, request);
        }

        private boolean shouldBlock(String host, String url) {
            // Verificar contra lista de dominios bloqueados
            for (String blocked : BLOCKED_DOMAINS) {
                if (host.endsWith(blocked) || host.equals(blocked)) return true;
            }
            // Patrones adicionales de anuncios de YouTube
            if (url.contains("/api/stats/ads") ||
                url.contains("&adformat=") ||
                url.contains("ctier=") ||
                url.contains("/pagead/") ||
                url.contains("/ads/") ||
                url.contains("ad_type=") ||
                url.contains("adunit")) {
                return true;
            }
            return false;
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            progressBar.setVisibility(View.VISIBLE);
            urlBar.setText(url);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            progressBar.setVisibility(View.GONE);

            // Inyectar JS para omitir anuncios de YouTube automáticamente
            injectAdSkipper(view);

            // Inyectar JS para habilitar reproducción en background
            injectBackgroundPlayJS(view);
        }

        private void injectAdSkipper(WebView view) {
            String js =
                "(function() {" +
                "  var skipAd = function() {" +
                "    var btn = document.querySelector('.ytp-skip-ad-button, " +
                "      .ytp-ad-skip-button, .ytp-ad-skip-button-modern, " +
                "      [class*=\"skip-ad\"]');" +
                "    if(btn) { btn.click(); }" +
                "    var adOverlay = document.querySelector('.ytp-ad-overlay-container');" +
                "    if(adOverlay) { adOverlay.remove(); }" +
                "    var adBanner = document.querySelector('.ytp-ad-player-overlay');" +
                "    if(adBanner) { adBanner.style.display='none'; }" +
                "  };" +
                "  var observer = new MutationObserver(skipAd);" +
                "  observer.observe(document.body || document.documentElement, " +
                "    {childList: true, subtree: true});" +
                "  setInterval(skipAd, 500);" +
                "})();";
            view.evaluateJavascript(js, null);
        }

        private void injectBackgroundPlayJS(WebView view) {
            // Evitar que YouTube pause al perder foco/visibilidad
            String js =
                "(function() {" +
                "  Object.defineProperty(document, 'hidden', {value: false});" +
                "  Object.defineProperty(document, 'visibilityState', {value: 'visible'});" +
                "  document.addEventListener('visibilitychange', " +
                "    function(e) { e.stopImmediatePropagation(); }, true);" +
                "  document.addEventListener('webkitvisibilitychange', " +
                "    function(e) { e.stopImmediatePropagation(); }, true);" +
                "  var videos = document.querySelectorAll('video');" +
                "  videos.forEach(function(v) {" +
                "    v.addEventListener('pause', function() {" +
                "      if(document.hidden === false) {" +
                "        AndroidBridge.onVideoPaused();" +
                "      }" +
                "    });" +
                "    v.addEventListener('play', function() {" +
                "      AndroidBridge.onVideoPlaying(v.src || 'video');" +
                "    });" +
                "  });" +
                "})();";
            view.evaluateJavascript(js, null);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // FULLSCREEN: Soporte para modo pantalla completa de videos
    // ════════════════════════════════════════════════════════════════════════
    private class FullscreenWebChromeClient extends WebChromeClient {

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (customView != null) {
                callback.onCustomViewHidden();
                return;
            }
            customView = view;
            customViewCallback = callback;
            fullscreenContainer.addView(view);
            fullscreenContainer.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }

        @Override
        public void onHideCustomView() {
            if (customView != null) {
                fullscreenContainer.removeView(customView);
                fullscreenContainer.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                customView = null;
                customViewCallback = null;
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
            }
        }

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progressBar.setProgress(newProgress);
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTERFAZ JS → JAVA: Comunicación desde la página web al servicio nativo
    // ════════════════════════════════════════════════════════════════════════
    private class WebAppInterface {

        @JavascriptInterface
        public void onVideoPlaying(String title) {
            if (isBound && mediaService != null) {
                runOnUiThread(() -> mediaService.updateNotification(title, true));
            }
        }

        @JavascriptInterface
        public void onVideoPaused() {
            if (isBound && mediaService != null) {
                runOnUiThread(() -> mediaService.updateNotification("Pausado", false));
            }
        }
    }
}
