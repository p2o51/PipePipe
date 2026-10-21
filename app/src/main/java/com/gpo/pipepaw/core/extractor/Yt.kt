package com.gpo.pipepaw.core.extractor

import android.content.Context
import com.gpo.pipepaw.core.settings.AppSettings
import com.gpo.pipepaw.ui.components.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.ListExtractor.InfoItemsPage
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import org.schabi.newpipe.extractor.Page as ExtractorPage

/**
 * The app's only door to PipePipeExtractor. Every call is a suspend function on [Dispatchers.IO];
 * the UI never touches an extractor type except [StreamInfo], which the player resolves itself.
 * Paging cursors are opaque tokens for the extractor's own [ExtractorPage]s ([Cursors]).
 */
object Yt {
    lateinit var downloader: PawDownloader
        private set

    private val service: StreamingService get() = ServiceList.YouTube

    fun init(context: Context, http: OkHttpClient) {
        downloader = PawDownloader(http)
        val locale = context.resources.configuration.locales[0] ?: Locale.getDefault()
        val country = AppSettings.contentCountry.value.ifEmpty { locale.country.ifEmpty { "US" } }
        NewPipe.init(downloader, Localization.fromLocale(locale), ContentCountry(country))
        // Signed out: the client PipePipe defaults to (plain stream URLs, no SABR, no PO token).
        // The signature / n-parameter decoder falls back to PipePipe's API until the local
        // WebView decoder is ported (docs/PLAN.md, 播放内核).
        NewPipe.setYoutubePlayerClient("visionos")
    }

    // ── Streams ─────────────────────────────────────────────────────────────

    suspend fun stream(url: String): StreamInfo = io { StreamInfo.getInfo(service, url) }

    // ── Search ──────────────────────────────────────────────────────────────

    suspend fun search(query: String, cursor: String?): Page<ListItem> = io {
        // PipePipe's YouTube filters throw without a content filter; item 0 is "all", as its client defaults to.
        val factory = service.searchQHFactory
        val handler = factory.fromQuery(query, listOf(factory.getFilterItem(0)), null)
        if (cursor == null) {
            val info = SearchInfo.getInfo(service, handler)
            page(info.relatedItems.mapNotNull { it.toListItem() }.filterShorts(), info.nextPage)
        } else {
            val more = SearchInfo.getMoreItems(service, handler, Cursors.take(cursor))
            page(more.items.mapNotNull { it.toListItem() }.filterShorts(), more.nextPage)
        }
    }

    suspend fun suggestions(query: String): List<String> = io {
        if (query.isBlank()) emptyList() else service.suggestionExtractor.suggestionList(query)
    }

    // ── Channels ────────────────────────────────────────────────────────────

    suspend fun channel(url: String): ChannelInfo = io { ChannelInfo.getInfo(service, url) }

    /** The uploads already on a loaded [ChannelInfo] (its first page). */
    fun channelFirstPage(info: ChannelInfo): Page<VideoItem> =
        page(info.relatedItems.map { it.toVideo() }.filterShortVideos(), info.nextPage)

    suspend fun channelVideos(url: String, cursor: String): Page<VideoItem> = io {
        val more: InfoItemsPage<StreamInfoItem> = ChannelInfo.getMoreItems(service, url, Cursors.take(cursor))
        page(more.items.map { it.toVideo() }.filterShortVideos(), more.nextPage)
    }

    /** A channel's latest uploads from its RSS feed: one cheap request, no paging. */
    suspend fun latest(channelUrl: String): List<VideoItem> = io {
        FeedInfo.getInfo(service, channelUrl).relatedItems.map { it.toVideo() }
    }

    // ── Comments ────────────────────────────────────────────────────────────

    suspend fun comments(videoUrl: String, cursor: String?): Page<CommentsInfoItem> = io {
        if (cursor == null) {
            val info = CommentsInfo.getInfo(service, videoUrl)
            page(info.relatedItems, info.nextPage)
        } else {
            val more = CommentsInfo.getMoreItems(service, videoUrl, Cursors.take(cursor))
            page(more.items, more.nextPage)
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun <T> page(items: List<T>, next: ExtractorPage?): Page<T> {
        val more = ExtractorPage.isValid(next)
        return Page(items, hasMore = more, nextCursor = if (more) Cursors.put(next!!) else null)
    }

    private fun List<ListItem>.filterShorts(): List<ListItem> =
        if (!AppSettings.hideShorts.value) this else filterNot { it is ListItem.Video && it.video.isShort }

    private fun List<VideoItem>.filterShortVideos(): List<VideoItem> =
        if (!AppSettings.hideShorts.value) this else filterNot { it.isShort }
}

/** Opaque string tokens for extractor pages, so [com.gpo.pipepaw.ui.components.Pager] stays string-keyed. */
private object Cursors {
    private val next = AtomicLong()
    private val pages = ConcurrentHashMap<String, ExtractorPage>()

    fun put(page: ExtractorPage): String = next.incrementAndGet().toString().also { pages[it] = page }

    /** Kept (not removed): a failed load-more retries with the same cursor. */
    fun take(token: String): ExtractorPage = pages[token] ?: error("Unknown page token $token")
}
