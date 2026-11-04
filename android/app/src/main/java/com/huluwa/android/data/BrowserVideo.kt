package com.huluwa.android.data

import kotlinx.serialization.json.JsonPrimitive
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
    private val mfcnVideoPath = Regex("""/video/\d+\.html?""", RegexOption.IGNORE_CASE)
    fun isMfcnPage(url: String): Boolean {
        val uri = url.toHttpUrlOrNull() ?: return false
        return (uri.host == "mfcn801.com" || uri.host.endsWith(".mfcn801.com")) &&
            mfcnVideoPath.matches(uri.encodedPath)
    }
    fun isSupportedPage(url: String): Boolean = isMoviePage(url) || isMfcnPage(url)
    fun isPlaybackPage(url: String): Boolean = isMfcnPage(url) ||
        (isMoviePage(url) && url.toHttpUrlOrNull()?.encodedPath == "/e/DownSys/play/")
    fun isM3u8(url: String): Boolean = url.toHttpUrlOrNull()?.encodedPath?.endsWith(".m3u8", ignoreCase = true) == true
    fun canResolve(url: String): Boolean {
        val uri = url.toHttpUrlOrNull() ?: return false
        return isMoviePage(url) && uri.encodedPath == "/e/DownSys/play/" &&
            ((uri.queryParameter("pathid2") != null && uri.queryParameter("bf") == "1") ||
                (uri.queryParameter("pathid4") != null && uri.queryParameter("bf") == "3"))
    }

    // 该站点通过 Vue 异步挂载播放器；只观察 DOM 新增，不改动网页自身的播放流程。
    private val mfcnAdaptScript = """
        if (mfcnPage) {
          if (!document.getElementById('huluwa-mfcn-video-style')) {
            var mfcnStyle = document.createElement('style');
            mfcnStyle.id = 'huluwa-mfcn-video-style';
            // 网站固定了原生控制面板的透明度；revert 恢复含 transparent 状态的浏览器默认样式。
            mfcnStyle.textContent = 'video#video.video-player::-webkit-media-controls-panel{opacity:revert!important;transform:revert!important}' +
              '.play-content.huluwa-video-started>.play_but{display:none!important}';
            (document.head || document.documentElement).appendChild(mfcnStyle);
          }
          if (!window.__huluwaMfcnVideoAdapter) {
            var boundVideos = new WeakSet();
            function refreshMfcnVideo() {
              var video = document.querySelector('.play-content video#video.video-player');
              if (!video || boundVideos.has(video)) return;
              boundVideos.add(video);
              video.controls = true;
              video.setAttribute('playsinline', '');
              video.setAttribute('webkit-playsinline', '');
              function started() {
                var container = video.closest('.play-content');
                if (container) container.classList.add('huluwa-video-started');
              }
              video.addEventListener('playing', started);
              video.addEventListener('emptied', function() {
                var container = video.closest('.play-content');
                if (container) container.classList.remove('huluwa-video-started');
              });
              if (!video.paused && video.readyState >= 2) started();
            }
            var observer = new MutationObserver(refreshMfcnVideo);
            observer.observe(document.documentElement, {childList: true, subtree: true});
            window.__huluwaMfcnVideoAdapter = {refresh: refreshMfcnVideo, observer: observer};
          }
          window.__huluwaMfcnVideoAdapter.refresh();
        }
    """.trimIndent()

    // 只读取网页播放配置，不向第三方页面暴露 JavascriptInterface。
    val captureScript = """
        (function() {
          var mfcnVideo = /^\/video\/(\d+)\.html?$/i.exec(location.pathname);
          var mfcnPage = (location.hostname === 'mfcn801.com' || location.hostname.endsWith('.mfcn801.com')) && mfcnVideo;
          $mfcnAdaptScript
          if (!mfcnPage && !document.getElementById('huluwa-video-style')) {
            var style = document.createElement('style');
            style.id = 'huluwa-video-style';
            style.textContent = '.player .main.playering{width:100%!important;max-width:100%!important}' +
              '.playering .video,.playering #a1{width:100%!important;height:auto!important;aspect-ratio:16/9;min-height:200px;background:#000}' +
              '.playering video,.playering iframe{max-width:100%!important;object-fit:contain}';
            (document.head || document.documentElement).appendChild(style);
          }
          document.querySelectorAll('iframe').forEach(function(frame) {
            frame.setAttribute('allowfullscreen', '');
            frame.setAttribute('webkitallowfullscreen', '');
            var allow = frame.getAttribute('allow') || '';
            if (allow.indexOf('fullscreen') < 0) frame.setAttribute('allow', allow + '; fullscreen');
          });
          var videos = Array.from(document.querySelectorAll('video'));
          if (!mfcnPage) videos.forEach(function(video) { video.controls = true; });
          var active = mfcnPage ? document.querySelector('.play-content video#video.video-player') :
            videos.find(function(video) { return !video.paused; }) || videos[0];
          var source = active && (active.currentSrc || active.src) || '';
          if (mfcnPage) {
            // HLS.js 使用 blob 地址；从当前视频组件读取真实播放配置，并核对路由，避开广告和旧视频。
            var siteVideo = active;
            var node = active;
            source = '';
            active = null;
            while (node) {
              var player = node.__vue__;
              if (player && player.paramet && Number(player.paramet.video_id) === Number(mfcnVideo[1]) &&
                  typeof player.m3u8Url === 'string') {
                source = player.m3u8Url;
                active = siteVideo;
                break;
              }
              node = node.parentElement;
            }
          } else {
            if (source.indexOf('http') !== 0 && window.flashvars) source = window.flashvars.a || '';
            if (source.indexOf('http') !== 0 && Array.isArray(window.video)) source = (window.video[0] || '').split('->')[0];
          }
          var target = active;
          if (!target || target.getBoundingClientRect().height < 40) {
            target = mfcnPage ? document.querySelector('.play-content') :
              document.querySelector('.playering iframe, .playering #a1, .playering .video, #a1');
          }
          var playback = window.__huluwaVideoPlayback;
          if (!playback) {
            playback = window.__huluwaVideoPlayback = {page: location.href, requested: false, owned: false};
            document.addEventListener('play', function(event) {
              var state = window.__huluwaVideoPlayback;
              if (state.owned && event.target === state.ownedVideo) {
                event.target.pause();
              } else if (event.target === state.video || (state.target && state.target.contains(event.target))) {
                state.requested = true;
              }
            }, true);
            document.addEventListener('pause', function(event) {
              var state = window.__huluwaVideoPlayback;
              if (!state.owned && event.target === state.video) state.requested = false;
            }, true);
            document.addEventListener('click', function(event) {
              var state = window.__huluwaVideoPlayback;
              if (!state.owned && event.target.closest && event.target.closest('.play_but')) state.requested = true;
            }, true);
          }
          if (playback.page !== location.href) {
            playback.page = location.href;
            playback.requested = false;
            playback.owned = false;
            playback.ownedVideo = null;
            playback.ownedHls = null;
          }
          playback.video = active;
          playback.target = target;
          playback.hls = mfcnPage && active && player ? player.hls : null;
          if (playback.owned && active === playback.ownedVideo && active && !active.paused) active.pause();
          var rect = target && target.getBoundingClientRect();
          var viewport = window.visualViewport;
          var bounds = rect && rect.width > 0 && rect.height > 0 ? {
            left: rect.left - (viewport ? viewport.offsetLeft : 0),
            top: rect.top - (viewport ? viewport.offsetTop : 0),
            width: rect.width, height: rect.height,
            viewportWidth: viewport ? viewport.width : window.innerWidth
          } : null;
          return JSON.stringify({url: source, title: document.title, pageUrl: location.href,
            requested: !playback.owned && (playback.requested || !!(active && !active.paused && !active.ended)),
            positionMs: active ? Math.round(active.currentTime * 1000) : null, bounds: bounds});
        })();
    """.trimIndent()

    val pauseScript = pausePageScript(release = false)
    val stopScript = pausePageScript(release = true)

    // 先标记归属再暂停网页，后续网页自动 play 不会与原生播放器同时发声。
    fun takeoverScript(page: String) = """
        (function() {
          if (location.href !== ${JsonPrimitive(page)}) return false;
          var state = window.__huluwaVideoPlayback;
          if (!state) return false;
          state.owned = true;
          state.ownedVideo = state.video;
          state.ownedHls = state.hls;
          state.requested = false;
          try { if (state.ownedHls) state.ownedHls.stopLoad(); } catch (ignored) {}
          $pauseScript
          return true;
        })();
    """.trimIndent()

    fun releaseTakeoverScript(positionMs: Long) = """
        (function() {
          var state = window.__huluwaVideoPlayback;
          if (!state) return;
          state.owned = false;
          state.requested = false;
          var video = state.ownedVideo;
          state.ownedVideo = null;
          try { if (state.ownedHls) state.ownedHls.startLoad(); } catch (ignored) {}
          state.ownedHls = null;
          if (video && video.isConnected) {
            try { video.currentTime = ${positionMs.coerceAtLeast(0)} / 1000; } catch (ignored) {}
            try {
              var attempt = video.play();
              if (attempt && attempt.catch) attempt.catch(function() {});
            } catch (ignored) {}
          }
        })();
    """.trimIndent()

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
