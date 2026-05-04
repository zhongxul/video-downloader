package com.example.videodownloader.ui.screen.settings

import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.videodownloader.R
import com.example.videodownloader.ui.component.AppGradientBackdrop
import com.example.videodownloader.ui.component.AppSectionCard
import com.example.videodownloader.ui.redesign.component.AppTopBar

@Composable
fun XLoginWebViewScreen(
    onBack: () -> Unit,
    onCookieCaptured: (String) -> Unit,
) {
    val webViewHolder = remember { mutableStateOf<WebView?>(null) }

    AppGradientBackdrop {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AppTopBar(
                title = stringResource(R.string.x_login_title),
                showBack = true,
                onBack = onBack,
                rightActionText = stringResource(R.string.x_login_save_cookie),
                onRightAction = {
                    val cookie = CookieManager.getInstance().getCookie("https://x.com").orEmpty()
                    onCookieCaptured(cookie)
                },
            )
            AppSectionCard(
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.x_login_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { webViewHolder.value?.reload() }) {
                        Text(stringResource(R.string.x_login_refresh))
                    }
                }
            }

            AppSectionCard(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            ) {
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
