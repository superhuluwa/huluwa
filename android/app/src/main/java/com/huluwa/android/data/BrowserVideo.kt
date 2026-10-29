package com.huluwa.android.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 网页识别出的播放资源；请求头仅用于当前原生播放，不写入浏览记录。 */
data class BrowserVideo(
    val url: String, val title: String, val referer: String, val userAgent: String,
    val cookie: String = "", val positionMs: Long = 0,
) {
    val headers: Map<String, String> get() = buildMap {
        if (referer.isNotBlank()) put("Referer", referer)
        if (userAgent.isNotBlank()) put("User-Agent", userAgent)
        if (cookie.isNotBlank()) put("Cookie", cookie)
    }
}

object MovieWebVideo {
    fun isMoviePage(url: String): Boolean = url.toHttpUrlOrNull()?.host in setOf("66vod.com", "www.66vod.com")
    fun isM3u8(url: String): Boolean = url.toHttpUrlOrNull()?.encodedPath?.endsWith(".m3u8", ignoreCase = true) == true
    fun canResolve(url: String): Boolean {
        val uri = url.toHttpUrlOrNull() ?: return false
        return isMoviePage(url) && uri.encodedPath == "/e/DownSys/play/" &&
            ((uri.queryParameter("pathid2") != null && uri.queryParameter("bf") == "1") ||
                (uri.queryParameter("pathid4") != null && uri.queryParameter("bf") == "3"))
    }

    // 只读取公开播放配置，不向第三方页面暴露 JavascriptInterface。
    val captureScript = """
        (function() {
          if (!document.getElementById('huluwa-video-style')) {
            var style = document.createElement('style');
            style.id = 'huluwa-video-style';
            style.textContent = '.player .main.playering{width:100%!important;max-width:100%!important}' +
              '.playering .video,.playering #a1{width:100%!important;height:auto!important;aspect-ratio:16/9;min-height:200px;background:#000}' +
              '.playering video,.playering iframe{max-width:100%!important;object-fit:contain}';
            document.head.appendChild(style);
          }
          document.querySelectorAll('iframe').forEach(function(frame) {
            frame.setAttribute('allowfullscreen', '');
            frame.setAttribute('webkitallowfullscreen', '');
            var allow = frame.getAttribute('allow') || '';
            if (allow.indexOf('fullscreen') < 0) frame.setAttribute('allow', allow + '; fullscreen');
          });
          var videos = Array.from(document.querySelectorAll('video'));
          videos.forEach(function(video) { video.controls = true; });
          var active = videos.find(function(video) { return !video.paused; }) || videos[0];
          var source = active && (active.currentSrc || active.src) || '';
          if (source.indexOf('http') !== 0 && window.flashvars) source = window.flashvars.a || '';
          if (source.indexOf('http') !== 0 && Array.isArray(window.video)) source = (window.video[0] || '').split('->')[0];
          return JSON.stringify({url: source, title: document.title, positionMs: active ? Math.round(active.currentTime * 1000) : 0});
        })();
    """.trimIndent()

    val pauseScript = pausePageScript(release = false)
    val stopScript = pausePageScript(release = true)

    // 回退可能只切换网页路由，不会销毁文档；离开前主动释放媒体源。
    // 跨域 frame 无法访问媒体元素，用 replace 卸载，避免额外增加回退记录。
    private fun pausePageScript(release: Boolean) = """
        (function pausePage(win) {
          var doc;
          try { doc = win.document; } catch (ignored) { return false; }
          if (!doc) return false;
          doc.querySelectorAll('video,audio').forEach(function(media) {
            try {
              media.pause();
              if ($release) {
                media.removeAttribute('autoplay');
                if (media.srcObject) {
                  if (media.srcObject.getTracks) media.srcObject.getTracks().forEach(function(track) { track.stop(); });
                  media.srcObject = null;
                }
                media.removeAttribute('src');
                media.querySelectorAll('source').forEach(function(source) { source.removeAttribute('src'); });
                media.load();
              }
            } catch (ignored) {}
          });
          doc.querySelectorAll('iframe,frame').forEach(function(frame) {
            if ($release) frame.removeAttribute('data-huluwa-resume-src');
            if (pausePage(frame.contentWindow)) return;
            try {
              if (!$release && !frame.hasAttribute('data-huluwa-resume-src') && frame.src) {
                frame.setAttribute('data-huluwa-resume-src', frame.src);
              }
              frame.contentWindow.location.replace('about:blank');
            } catch (ignored) {}
          });
          return true;
        })(window);
    """.trimIndent()

    val resumeScript = """
        (function resumePage(win) {
          var doc;
          try { doc = win.document; } catch (ignored) { return; }
          if (!doc) return;
          doc.querySelectorAll('iframe,frame').forEach(function(frame) {
            var source = frame.getAttribute('data-huluwa-resume-src');
            if (source !== null) {
              try {
                frame.contentWindow.location.replace(source);
                frame.removeAttribute('data-huluwa-resume-src');
              } catch (ignored) {}
            } else resumePage(frame.contentWindow);
          });
        })(window);
    """.trimIndent()
}
