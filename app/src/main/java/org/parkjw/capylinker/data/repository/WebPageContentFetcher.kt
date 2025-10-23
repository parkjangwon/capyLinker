package org.parkjw.capylinker.data.repository

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class WebPageContentFetcher @Inject constructor(
    @ApplicationContext private val appContext: Context
) {

    /**
     * 렌더링이 끝난 후의 전체 페이지 HTML(document.documentElement.outerHTML)을 반환합니다.
     * - JS/DOMStorage 활성화
     * - 쿠키 허용
     * - onPageFinished 이후 약간 대기하여 동적 컨텐츠 반영
     */
    suspend fun getFullPageHtml(url: String, waitAfterLoadMs: Long = 1200L, timeoutMs: Long = 15000L): String? {
        return withContext(Dispatchers.Main) {
            withTimeout(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    val webView = WebView(appContext)

                    CookieManager.getInstance().setAcceptCookie(true)

                    val settings = webView.settings
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.loadsImagesAutomatically = false
                    settings.blockNetworkImage = true
                    // 데스크톱 브라우저 UA로 일부 사이트의 간소 뷰 회피
                    settings.userAgentString =
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                    webView.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, url: String) {
                            // 동적 콘텐츠 반영을 위해 소량 대기 후 outerHTML 추출
                            view.postDelayed({
                                try {
                                    view.evaluateJavascript(
                                        "(function(){return document.documentElement.outerHTML;})()"
                                    ) { value ->
                                        // value는 JS 문자열(JSON) 형태로 전달됨
                                        val html = unquoteJsString(value).trim()
                                        if (!cont.isCompleted) {
                                            cont.resume(html)
                                        }
                                        view.destroy()
                                    }
                                } catch (e: Exception) {
                                    if (!cont.isCompleted) {
                                        cont.resumeWithException(e)
                                    }
                                    view.destroy()
                                }
                            }, waitAfterLoadMs)
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: android.webkit.WebResourceError
                        ) {
                            if (!cont.isCompleted) cont.resume(null)
                            view.destroy()
                        }
                    }

                    cont.invokeOnCancellation {
                        try {
                            webView.destroy()
                        } catch (_: Exception) {}
                    }

                    webView.loadUrl(url)
                }
            }
        }
    }

    private fun unquoteJsString(raw: String?): String {
        if (raw == null) return ""
        // "...." 형태 제거
        var s = if (raw.length >= 2 && raw.first() == '"' && raw.last() == '"') {
            raw.substring(1, raw.length - 1)
        } else raw
        // JS 이스케이프 해제
        s = s.replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
        // 일부 브라우저가 < 를 \u003C 로 이스케이프하는 경우 복원
        s = s.replace("\\u003C", "<")
            .replace("\\u003E", ">")
            .replace("\\u0026", "&")
        return s
    }
}
