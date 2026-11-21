package com.neurotube.app.innertube.pages

import com.neurotube.app.innertube.models.GridRenderer
import com.neurotube.app.innertube.models.MusicPlaylistShelfRenderer
import com.neurotube.app.innertube.models.MusicShelfRenderer
import com.neurotube.app.innertube.models.SectionListRenderer
import com.neurotube.app.innertube.models.YTItem
import com.neurotube.app.innertube.models.continuationItemViewModelToken
import com.neurotube.app.innertube.models.getContinuation
import com.neurotube.app.innertube.models.getContinuations
import com.neurotube.app.innertube.models.response.BrowseResponse

/**
 * Reads the shape of the music library, which is not uniform. A first page puts its items in a grid,
 * a shelf, or a playlist shelf directly under the section list; some accounts wrap that in an item
 * section; and a later page arrives as a continuation holding a bare grid, a shelf, a playlist
 * shelf, or a whole section list. Some libraries hand the next slice back as an append action rather
 * than as continuation contents. These helpers flatten all of that into items plus a next token.
 *
 * Every one of those shapes has to be read. A page whose shape is walked past yields no items and,
 * worse, no next token, so the walk stops and only the first page is imported — which is how an
 * account with more liked songs than one page holds ends up with a shorter list in the app.
 */
object LibraryResponseParser {
    /** The items and next token of the library tab at [tabIndex]. */
    fun pageOf(
        response: BrowseResponse,
        tabIndex: Int = 0,
    ): LibraryPage {
        val sections = sectionList(response, tabIndex)?.contents.orEmpty()
        val fallback = sectionToken(response, tabIndex)

        // Every list-bearing section is read, not only the first. A signed-in liked-songs page opens
        // with a header or sort-chip section and carries its grid after it, so reading the first
        // section alone imported nothing; a page split across several sections imported only the
        // first one's share.
        //
        // Each section keeps its own token. Taking only the first one followed that list to the end
        // and left the other lists' pages unread, which is how a long liked list imported short.
        val items = mutableListOf<YTItem>()
        val continuations = mutableListOf<String>()
        for (section in sections) {
            val read = readSection(section) ?: continue
            items += read.items
            continuations += read.continuations
        }
        if (continuations.isEmpty() && fallback != null) continuations += fallback
        return LibraryPage(
            items = items,
            continuation = continuations.firstOrNull(),
            continuations = continuations.distinct(),
        )
    }

    /** The items and next token of one library section, or null when it lists nothing. */
    private fun readSection(section: SectionListRenderer.Content): LibraryPage? {
        val grid = section.gridRenderer
        if (grid != null) {
            return LibraryPage(
                items = grid.items.toLibraryItems(),
                continuation =
                    grid.continuations?.getContinuation()
                        ?: grid.items.trailingContinuation(),
                continuations =
                    grid.continuations.getContinuations()
                        .ifEmpty { listOfNotNull(grid.items.trailingContinuation()) },
            )
        }
        section.musicPlaylistShelfRenderer?.let { return it.asSection() }
        section.musicShelfRenderer?.let { shelf ->
            return LibraryPage(
                items = shelf.toItems(),
                continuation =
                    shelf.continuations?.getContinuation()
                        ?: shelf.contents?.getContinuation(),
                continuations = shelf.allContinuations(),
            )
        }
        val itemSection = section.itemSectionRenderer ?: return null
        itemSection.gridRenderer?.let { grid ->
            return LibraryPage(
                items = grid.items.toLibraryItems(),
                continuation =
                    grid.continuations?.getContinuation()
                        ?: grid.items.trailingContinuation(),
                continuations =
                    grid.continuations.getContinuations()
                        .ifEmpty { listOfNotNull(grid.items.trailingContinuation()) },
            )
        }
        itemSection.musicShelfRenderer?.let { shelf ->
            return LibraryPage(
                items = shelf.toItems(),
                continuation =
                    shelf.continuations?.getContinuation()
                        ?: shelf.contents?.getContinuation(),
                continuations = shelf.allContinuations(),
            )
        }
        val nestedGrid = itemSection.contents.orEmpty().firstNotNullOfOrNull { it.gridRenderer }
        if (nestedGrid != null) {
            return LibraryPage(
                items = nestedGrid.items.toLibraryItems(),
                continuation =
                    nestedGrid.continuations?.getContinuation()
                        ?: nestedGrid.items.trailingContinuation(),
                continuations =
                    nestedGrid.continuations.getContinuations()
                        .ifEmpty { listOfNotNull(nestedGrid.items.trailingContinuation()) },
            )
        }
        // The plain row list an item section carries directly.
        return LibraryPage(
            items = itemSection.contents.toItems(),
            continuation = itemSection.contents?.getContinuation(),
            continuations = itemSection.contents.getContinuations(),
        )
    }

    /** Every token a shelf carries, from its own continuation list and from its trailing rows. */
    private fun MusicShelfRenderer.allContinuations(): List<String> =
        continuations.getContinuations() + contents.getContinuations()

    /**
     * The token on the section list itself, when the page carries exactly one list-bearing section.
     *
     * A single-section library page — every `FEmusic_liked_*` page is one — hangs the next-page
     * token off the section list rather than the grid or shelf inside it. That token is then
     * unambiguously this section's, so it is taken as a fallback; without it the library renders
     * only its first page. With more than one list-bearing section the token belongs to the list as
     * a whole and must not be attributed to the first one.
     */
    private fun sectionToken(
        response: BrowseResponse,
        tabIndex: Int,
    ): String? {
        val sectionList = sectionList(response, tabIndex) ?: return null
        val contents = sectionList.contents.orEmpty()
        // A page's leading header or sort-chip section holds no list and so must not count as a
        // second one; counting it rejected the token of the one real section and stopped the walk.
        if (contents.count { it.carriesList() } != 1) return null
        return sectionList.continuations?.getContinuation()
            ?: contents.firstNotNullOfOrNull { it.continuationItemViewModel?.continuationItemViewModelToken() }
    }

    /** Whether a section holds anything to read, as opposed to a header or sort-chip strip. */
    private fun SectionListRenderer.Content.carriesList(): Boolean {
        if (gridRenderer != null || musicShelfRenderer != null || musicPlaylistShelfRenderer != null) return true
        val itemSection = itemSectionRenderer ?: return false
        if (itemSection.gridRenderer != null || itemSection.musicShelfRenderer != null) return true
        return itemSection.contents.orEmpty().any { content ->
            content.musicResponsiveListItemRenderer != null ||
                content.lockupViewModel != null ||
                content.gridRenderer != null
        }
    }

    /**
     * A library tab's section list, in whichever container the page arrives in.
     *
     * A signed-in liked-songs page comes back two-column — the shelf lives under
     * `secondaryContents` — while other library pages and every continuation are single-column.
     * Reading only the single-column container walked past a whole liked-songs page and imported
     * nothing, which is how the account's liked songs never reached the app.
     *
     * The tab's own section list is preferred over the secondary column: a two-column page carries
     * both, and the secondary column can hold an empty or unrelated list, which would shadow the
     * tab that actually holds the items.
     */
    private fun sectionList(
        response: BrowseResponse,
        tabIndex: Int,
    ): SectionListRenderer? {
        val twoColumn = response.contents?.twoColumnBrowseResultsRenderer
        val singleColumnTabs = response.contents?.singleColumnBrowseResultsRenderer?.tabs
        if (singleColumnTabs != null && tabIndex in singleColumnTabs.indices) {
            singleColumnTabs[tabIndex].tabRenderer.content?.sectionListRenderer?.let { return it }
        }
        // A two-column page lists its tabs as nullable entries, so a missing one is skipped rather
        // than assumed present.
        val twoColumnTabs = twoColumn?.tabs
        if (twoColumnTabs != null && tabIndex in twoColumnTabs.indices) {
            twoColumnTabs[tabIndex]?.tabRenderer?.content?.sectionListRenderer?.let { return it }
        }
        return twoColumn?.secondaryContents?.sectionListRenderer
            ?: response.contents?.sectionListRenderer
    }

    /** The items and next token of a library continuation response. */
    fun continuationPageOf(response: BrowseResponse): LibraryContinuationPage {
        val contents = response.continuationContents

        contents?.gridContinuation?.let { grid ->
            val page =
                LibraryContinuationPage(
                    items = grid.items.toLibraryItems(),
                    continuation = grid.continuations?.getContinuation() ?: grid.items.trailingContinuation(),
                    continuations =
                        grid.continuations.getContinuations()
                            .ifEmpty { listOfNotNull(grid.items.trailingContinuation()) },
                )
            if (page.hasContent) return page
        }

        contents?.sectionListContinuation?.let { sectionList ->
            // A continuation of a grid is bare, but a continuation of a shelf repeats the section
            // list and puts the next token on the section list itself. Every section is read for the
            // same reason the first page reads them all: a continuation can carry more than one, and
            // each list keeps its own token so none of them is left half-read.
            val fallbackContinuation = sectionList.continuations?.getContinuation()
            val items = mutableListOf<YTItem>()
            val continuations = mutableListOf<String>()
            for (section in sectionList.contents) {
                val page = fromSlice(section = section, fallbackContinuation = null)
                items += page.items
                continuations += page.continuations
            }
            if (continuations.isEmpty() && fallbackContinuation != null) continuations += fallbackContinuation
            val page =
                LibraryContinuationPage(
                    items = items,
                    continuation = continuations.firstOrNull(),
                    continuations = continuations.distinct(),
                )
            if (page.hasContent) return page
        }

        contents?.musicPlaylistShelfContinuation?.let { shelf ->
            val page =
                LibraryContinuationPage(
                    items = shelf.contents.toItems(),
                    continuation = shelf.continuations?.getContinuation() ?: shelf.contents?.getContinuation(),
                    continuations = shelf.continuations.getContinuations() + shelf.contents.getContinuations(),
                )
            if (page.hasContent) return page
        }

        contents?.musicShelfContinuation?.let { shelf ->
            val page =
                LibraryContinuationPage(
                    items = shelf.toItems(),
                    continuation = shelf.continuations?.getContinuation() ?: shelf.contents?.getContinuation(),
                    continuations = shelf.continuations.getContinuations() + shelf.contents.getContinuations(),
                )
            if (page.hasContent) return page
        }

        // A library page can hand its next slice back as an appended or reloaded list instead of as
        // continuation contents; the token then rides on the last row. All three containers are
        // served — actions, commands and endpoints — and a walk that reads only some of them stops
        // at the page that uses another, which is how a long liked list imported short.
        val handed =
            response.continuationItemsOfActions()
                ?: response.continuationItemsOfCommands()
                ?: response.continuationItemsOfEndpoints()
        if (handed != null) {
            return LibraryContinuationPage(
                items = handed.toItems(),
                continuation = handed.getContinuation(),
                continuations = handed.getContinuations(),
            )
        }

        return LibraryContinuationPage(items = emptyList(), continuation = null)
    }

    /** The rows a page hands back as an appended or reloaded list in `onResponseReceivedActions`. */
    private fun BrowseResponse.continuationItemsOfActions(): List<MusicShelfRenderer.Content>? =
        onResponseReceivedActions
            ?.firstNotNullOfOrNull { action ->
                action.appendContinuationItemsAction?.continuationItems
                    ?: action.reloadContinuationItemsAction?.continuationItems
            }

    /** The same, in the `onResponseReceivedCommands` form. */
    private fun BrowseResponse.continuationItemsOfCommands(): List<MusicShelfRenderer.Content>? =
        onResponseReceivedCommands
            ?.firstNotNullOfOrNull { command ->
                command.appendContinuationItemsCommand?.continuationItems
                    ?: command.reloadContinuationItemsCommand?.continuationItems
            }

    /** The same, in the `onResponseReceivedEndpoints` form. */
    private fun BrowseResponse.continuationItemsOfEndpoints(): List<MusicShelfRenderer.Content>? =
        onResponseReceivedEndpoints
            ?.firstNotNullOfOrNull { endpoint ->
                endpoint.appendContinuationItemsAction?.continuationItems
                    ?: endpoint.reloadContinuationItemsAction?.continuationItems
            }

    /** Whether a page carried anything, so an empty container cannot shadow a fuller one. */
    private val LibraryContinuationPage.hasContent: Boolean
        get() = items.isNotEmpty() || continuations.isNotEmpty()

    /** A playlist shelf read as the shelf shape the rest of this file works with. */
    private fun MusicPlaylistShelfRenderer.asSection(): LibraryPage =
        LibraryPage(
            items = contents.toItems(),
            continuation =
                continuations?.getContinuation()
                    ?: contents?.getContinuation(),
            continuations = continuations.getContinuations() + contents.getContinuations(),
        )

    /**
     * The token a modern library grid appends as its last item, or null when it carries none.
     *
     * A grid can hand out its next page either through `continuations`, by appending a bare
     * `continuationItemRenderer`, or — as the library now does — by appending a
     * `continuationItemViewModel`. Reading only the first two lost the token, so a liked-songs grid
     * longer than one page imported just its first slice.
     */
    private fun List<GridRenderer.Item>.trailingContinuation(): String? =
        // The token item is normally last, but a grid can append it before a trailing banner; the
        // whole list is searched so a stray item after it cannot strand the rest of the list.
        firstNotNullOfOrNull { item ->
            item.continuationItemRenderer
                ?.continuationEndpoint
                ?.continuationCommand
                ?.token
                ?: item.continuationItemViewModel?.continuationItemViewModelToken()
        }

    /** One slice of a library: its items, and the token to continue from when it has no own token. */
    private fun fromSlice(
        section: SectionListRenderer.Content?,
        fallbackContinuation: String?,
    ): LibraryContinuationPage {
        val read = section?.let(::readSection)
        val continuations = read?.continuations.orEmpty().ifEmpty { listOfNotNull(fallbackContinuation) }
        return LibraryContinuationPage(
            items = read?.items.orEmpty(),
            continuation = continuations.firstOrNull(),
            continuations = continuations,
        )
    }

    private fun List<GridRenderer.Item>.toLibraryItems(): List<YTItem> =
        mapNotNull { item ->
            item.musicTwoRowItemRenderer?.let { LibraryPage.fromMusicTwoRowItemRenderer(it) }
                ?: item.lockupViewModel?.toLibraryItem()
        }

    private fun MusicShelfRenderer?.toItems(): List<YTItem> = this?.contents.toItems()

    private fun List<MusicShelfRenderer.Content>?.toItems(): List<YTItem> =
        this.orEmpty().flatMap { content ->
            val direct =
                listOfNotNull(
                    content.musicResponsiveListItemRenderer?.let { LibraryPage.fromMusicResponsiveListItemRenderer(it) }
                        ?: content.musicTwoRowItemRenderer?.let { LibraryPage.fromMusicTwoRowItemRenderer(it) }
                        ?: content.lockupViewModel?.toLibraryItem(),
                )
            // A section item can itself be a grid, which is how some libraries return their saved
            // playlists, albums and songs.
            direct +
                content.gridRenderer
                    ?.items
                    ?.toLibraryItems()
                    .orEmpty()
        }
}
