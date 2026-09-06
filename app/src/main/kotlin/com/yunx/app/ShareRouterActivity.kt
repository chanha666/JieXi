package com.yunx.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.fuke.mobile.MediaActivity
import com.jiexi.core.link.LinkKind
import com.jiexi.core.link.UnifiedLinkClassifier

/**
 * Android share sheet entry. It has no UI: cloud links go to the cloud resolver,
 * public-video/direct links go to the media workspace, and unknown web links are
 * offered to the generic media engine. This keeps one share target without
 * making the user choose the correct feature first.
 */
class ShareRouterActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        route(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        route(intent)
    }

    private fun route(source: Intent?) {
        val sharedText = source
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type?.startsWith("text/") == true }
            ?.let { intent ->
                intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    ?: intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
            }
            .orEmpty()
        val kind = UnifiedLinkClassifier.classifyText(sharedText).firstOrNull()?.kind
        val target = Intent(this, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_SHARED_TEXT, sharedText)
        startActivity(target.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
