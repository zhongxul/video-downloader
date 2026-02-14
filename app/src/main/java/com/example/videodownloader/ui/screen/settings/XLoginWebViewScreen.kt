package com.example.videodownloader.ui.screen.settings

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videodownloader.ui.component.AppGradientBackdrop
import com.example.videodownloader.ui.component.AppSectionCard

@Composable
fun XLoginWebViewScreen(
    onBack: () -> Unit,
    onCookieCaptured: (String) -> Unit,
) {
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    AppGradientBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppSectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "登录 X 并获取 Cookie",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(
                        text = "登录成功后点击保存，自动回填到设置页。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onBack) { Text("返回") }
                        FilledTonalButton(
                            onClick = {
                                val cookie = CookieManager.getInstance().getCookie("https://x.com").orEmpty()
                                onCookieCaptured(cookie)
                            },
                        ) {
                            Text("保存当前 Cookie")
                        }
                        Button(onClick = { webViewHolder.value?.reload() }) {
                            Text("刷新")
                        }
                    }
                }
            }

            AppSectionCard(modifier = Modifier.weight(1f)) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webViewClient = object : WebViewClient() {}
                            loadUrl("https://x.com/i/flow/login")
                            webViewHolder.value = this
                        }
                    },
                    update = { webViewHolder.value = it },
                )
            }
        }
    }
}
