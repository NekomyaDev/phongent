package com.wangzan101.mika.remote

import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.record.RunStore
import com.wangzan101.mika.ui.Exchange
import com.wangzan101.mika.ui.ExchangeStatus
import com.wangzan101.mika.core.record.RunEvent
import java.io.File

/** Screens belong to this exchange only; never substitute the current screen. */
object ResultScreenshots {
    fun files(app:MikaApp,exchange:Exchange):List<File> {
        if(exchange.status !is ExchangeStatus.Done)return emptyList()
        val id=exchange.runId ?: return emptyList()
        return runCatching {
            val store=RunStore(app)
            val events=store.events(id)
            val cited=events.filterIsInstance<RunEvent.Sources>().lastOrNull()?.items?.flatMap{it.mediaIds}.orEmpty()
            val shots=events.filterIsInstance<RunEvent.Media>()
            val last=shots.maxOfOrNull{it.step}
            val ids=if(cited.isNotEmpty())cited else shots.filter{it.step==last}.map{it.mediaId}
            ids.distinct().takeLast(4).mapNotNull{store.screen(id,it)}.filter{it.length() in 1..8*1024*1024}
        }.getOrDefault(emptyList())
    }
}
