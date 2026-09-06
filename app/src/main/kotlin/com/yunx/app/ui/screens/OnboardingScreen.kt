package com.yunx.app.ui.screens

import com.yunx.app.ui.i18n.tr

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yunx.app.R

@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Button(onClick = onFinish,
                    modifier = Modifier.navigationBarsPadding().padding(horizontal = 28.dp, vertical = 16.dp).fillMaxWidth().heightIn(min = 52.dp),
                    shape = MaterialTheme.shapes.medium) { Text(tr("开始使用")) }
            }
        }
    ) { insets ->
        Column(Modifier.padding(insets).verticalScroll(rememberScrollState()).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Spacer(Modifier.height(28.dp))
            Image(painterResource(R.drawable.jiexi_mark), "解析图标", Modifier.size(64.dp))
            Text(tr("把想保存的，\n交给解析。"), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text(tr("视频、网盘分享，一个入口就够了。"), style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider()
            Text(tr("01   粘贴链接，自动识别内容"), style = MaterialTheme.typography.titleMedium)
            Text(tr("02   下载进度，随时查看"), style = MaterialTheme.typography.titleMedium)
            Text(tr("03   更多功能，收进「我的」"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
        }
    }
}
