package com.pichillilorenzo.flutter_inappbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.pichillilorenzo.flutter_inappbrowser.InAppWebView.InAppWebView;
import com.pichillilorenzo.flutter_inappbrowser.InAppWebView.InAppWebViewOptions;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;
import io.flutter.plugin.platform.PlatformView;

import static android.content.Context.DOWNLOAD_SERVICE;
import static io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import static io.flutter.plugin.common.MethodChannel.Result;

public class FlutterWebView implements PlatformView, MethodCallHandler  {

  static final String LOG_TAG = "FlutterWebView";
  static final int MY_PERMISSIONS_REQUEST_WRITE_STORAGE = 34453;

  public final Activity activity;
  public InAppWebView webView;
  public MethodChannel channel;
  public final Registrar registrar;

  private String dlURL = "";
  private String dlUserAgent = "";
  private String dlContentDisposition ="";
  private String dlMimeType = "";

  public FlutterWebView(final Registrar registrar, int id, HashMap<String, Object> params) {

    this.registrar = registrar;
    this.activity = registrar.activity();

    String initialUrl = (String) params.get("initialUrl");
    String initialFile = (String) params.get("initialFile");
    Map<String, String> initialData = (Map<String, String>) params.get("initialData");
    Map<String, String> initialHeaders = (Map<String, String>) params.get("initialHeaders");
    HashMap<String, Object> initialOptions = (HashMap<String, Object>) params.get("initialOptions");

    InAppWebViewOptions options = new InAppWebViewOptions();
    options.parse(initialOptions);

    webView = new InAppWebView(registrar, this, id, options);
    webView.prepare();

    channel = new MethodChannel(registrar.messenger(), "com.pichillilorenzo/flutter_inappwebview_" + id);
    channel.setMethodCallHandler(this);

    if (initialFile != null) {
      try {
        initialUrl = Util.getUrlAsset(registrar, initialFile);
      } catch (IOException e) {
        e.printStackTrace();
        Log.e(LOG_TAG, initialFile + " asset file cannot be found!", e);
        return;
      }
    }

    if (initialData != null) {
      String data = initialData.get("data");
      String mimeType = initialData.get("mimeType");
      String encoding = initialData.get("encoding");
      String baseUrl = initialData.get("baseUrl");
      webView.loadDataWithBaseURL(baseUrl, data, mimeType, encoding, null);
    }
    else
      webView.loadUrl(initialUrl, initialHeaders);

    registrar.addRequestPermissionsResultListener(new PluginRegistry.RequestPermissionsResultListener() {
      @Override
      public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
        case MY_PERMISSIONS_REQUEST_WRITE_STORAGE: {
          if (grantResults.length > 0
                  && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startDownload(dlURL, dlUserAgent, dlContentDisposition, dlMimeType);
          }
        }
      }
        return true;
      }
    });

    webView.setDownloadListener(new DownloadListener() {
      public void onDownloadStart(String url, String userAgent,
                                  String contentDisposition, String mimeType,
                                  long contentLength) {
        dlURL = url; dlUserAgent = userAgent; dlContentDisposition = contentDisposition; dlMimeType = mimeType;
        if (ContextCompat.checkSelfPermission(getView().getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
          if (ActivityCompat.shouldShowRequestPermissionRationale(registrar.activity(),
                  Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
          } else {
            ActivityCompat.requestPermissions(registrar.activity(),
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    MY_PERMISSIONS_REQUEST_WRITE_STORAGE);
          }
        } else {
          startDownload(url, userAgent, contentDisposition, mimeType);
        }
      }
    });
  }

  public void startDownload(String url, String userAgent,
                            String contentDisposition, String mimeType) {
    try {
      DownloadManager.Request request = new DownloadManager.Request(
              Uri.parse(url));
      request.setMimeType(mimeType);

      String cookies = CookieManager.getInstance().getCookie(url);
      request.addRequestHeader("cookie", cookies);
      request.addRequestHeader("User-Agent", userAgent);
      request.setDescription("Downloading file...");
      request.setTitle(URLUtil.guessFileName(url, contentDisposition,
              mimeType));
      request.allowScanningByMediaScanner();
      request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
      request.setDestinationInExternalPublicDir(
              Environment.DIRECTORY_DOWNLOADS, URLUtil.guessFileName(
                      url, contentDisposition, mimeType));
      DownloadManager dm = (DownloadManager) getView().getContext().getSystemService(DOWNLOAD_SERVICE);
      dm.enqueue(request);
      Toast.makeText(getView().getContext(), "Downloading File",
              Toast.LENGTH_LONG).show();
    } catch(Exception e) {
      Log.e("DOWNLOADING", "Error when downloading "+ url, e);
    }
  }

  @Override
  public View getView() {
    return webView;
  }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    String source;
    String urlFile;
    switch (call.method) {
      case "getUrl":
        result.success((webView != null) ? webView.getUrl() : null);
        break;
      case "getTitle":
        result.success((webView != null) ? webView.getTitle() : null);
        break;
      case "getProgress":
        result.success((webView != null) ? webView.getProgress() : null);
        break;
      case "loadUrl":
        if (webView != null)
          webView.loadUrl(call.argument("url").toString(), (Map<String,String>)call.argument("headers"), result);
        else
          result.success(false);
        break;
      case "postUrl":
        if (webView != null)
          webView.postUrl(call.argument("url").toString(), (byte[])call.argument("postData"), result);
        else
          result.success(false);
        break;
      case "loadData":
        {
          String data = call.argument("data").toString();
          String mimeType = call.argument("mimeType").toString();
          String encoding = call.argument("encoding").toString();
          String baseUrl = call.argument("baseUrl").toString();

          if (webView != null)
            webView.loadData(data, mimeType, encoding, baseUrl, result);
          else
            result.success(false);
        }
        break;
      case "loadFile":
        if (webView != null)
          webView.loadFile(call.argument("url").toString(), (Map<String,String>)call.argument("headers"), result);
        else
          result.success(false);
        break;
      case "fileChosen":
        if (webView != null) {
          String uri = call.argument("uri").toString();
          webView.fileChosen(uri);
        }
        result.success("");
        break;
      case "filesChosen":
        if (webView != null) {
          ArrayList<String> uri = call.argument("uri");
          webView.filesChosen(uri);
        }
        result.success("");
        break;
      case "injectScriptCode":
        if (webView != null) {
          source = call.argument("source").toString();
          webView.injectScriptCode(source, result);
        }
        else {
          result.success("");
        }
        break;
      case "injectScriptFile":
        if (webView != null) {
          urlFile = call.argument("urlFile").toString();
          webView.injectScriptFile(urlFile);
        }
        result.success(true);
        break;
      case "injectStyleCode":
        if (webView != null) {
          source = call.argument("source").toString();
          webView.injectStyleCode(source);
        }
        result.success(true);
        break;
      case "injectStyleFile":
        if (webView != null) {
          urlFile = call.argument("urlFile").toString();
          webView.injectStyleFile(urlFile);
        }
        result.success(true);
        break;
      case "reload":
        if (webView != null)
          webView.reload();
        result.success(true);
        break;
      case "goBack":
        if (webView != null)
          webView.goBack();
        result.success(true);
        break;
      case "canGoBack":
        result.success((webView != null) && webView.canGoBack());
        break;
      case "goForward":
        if (webView != null)
          webView.goForward();
        result.success(true);
        break;
      case "canGoForward":
        result.success((webView != null) && webView.canGoForward());
        break;
      case "goBackOrForward":
        if (webView != null)
          webView.goBackOrForward((int)call.argument("steps"));
        result.success(true);
        break;
      case "canGoBackOrForward":
        result.success((webView != null) && webView.canGoBackOrForward((int)call.argument("steps")));
        break;
      case "stopLoading":
        if (webView != null)
          webView.stopLoading();
        result.success(true);
        break;
      case "isLoading":
        result.success((webView != null) && webView.isLoading());
        break;
      case "takeScreenshot":
        result.success((webView != null) ? webView.takeScreenshot() : null);
        break;
      case "setOptions":
        if (webView != null) {
          InAppWebViewOptions inAppWebViewOptions = new InAppWebViewOptions();
          HashMap<String, Object> inAppWebViewOptionsMap = call.argument("options");
          inAppWebViewOptions.parse(inAppWebViewOptionsMap);
          webView.setOptions(inAppWebViewOptions, inAppWebViewOptionsMap);
        }
        result.success(true);
        break;
      case "getOptions":
        result.success((webView != null) ? webView.getOptions() : null);
        break;
      case "getCopyBackForwardList":
        result.success((webView != null) ? webView.getCopyBackForwardList() : null);
        break;
      case "dispose":
        dispose();
        result.success(true);
        break;
      default:
        result.notImplemented();
    }
  }

  @Override
  public void dispose() {
    if (webView != null) {
      webView.setWebChromeClient(new WebChromeClient());
      webView.setWebViewClient(new WebViewClient() {
        public void onPageFinished(WebView view, String url) {
          webView.destroy();
          webView = null;
        }
      });
      webView.loadUrl("about:blank");
    }
  }
}