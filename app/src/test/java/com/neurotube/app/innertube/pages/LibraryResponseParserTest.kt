package com.neurotube.app.innertube.pages

import com.neurotube.app.account.toSavedMusicCollections
import com.neurotube.app.account.toSubscribedArtists
import com.neurotube.app.innertube.models.AlbumItem
import com.neurotube.app.innertube.models.ArtistItem
import com.neurotube.app.innertube.models.PlaylistItem
import com.neurotube.app.innertube.models.SongItem
import com.neurotube.app.innertube.models.response.BrowseResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing is checked against responses captured from a real music library, because the library's
 * shape differs from search and artist pages: saved albums carry a plain watch endpoint, and pages
 * after the first arrive as a section list continuation.
 */
@OptIn(ExperimentalSerializationApi::class)
class LibraryResponseParserTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private fun response(name: String): BrowseResponse {
        val text =
            checkNotNull(javaClass.classLoader?.getResourceAsStream("music/$name")) {
                "missing fixture $name"
            }.bufferedReader().readText()
        // Some captures were trimmed to the fields under test and dropped the envelope.
        val patched = if (text.contains("\"responseContext\"")) text else text.replaceFirst("{", """{"responseContext":{},""")
        return json.decodeFromString(BrowseResponse.serializer(), patched)
    }

    private fun decode(json: String): BrowseResponse = this.json.decodeFromString(BrowseResponse.serializer(), json)

    @Test
    fun `a lockup playlist page imports every playlist it carries`() {
        val page = LibraryResponseParser.pageOf(response("library_playlists_lockup.json"))

        val playlists = page.items.filterIsInstance<PlaylistItem>()
        assertEquals("the lockup shape is what a signed-in library is served now", 3, playlists.size)
        assertTrue(playlists.all { it.id.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun `a lockup artist shelf imports every artist it carries`() {
        val page = LibraryResponseParser.pageOf(response("library_artists_lockup.json"))

        assertEquals(3, page.items.filterIsInstance<ArtistItem>().size)
    }

    @Test
    fun `a lockup album grid imports every album it carries`() {
        val page = LibraryResponseParser.pageOf(response("library_albums_lockup.json"))

        val albums = page.items.filterIsInstance<AlbumItem>()
        assertEquals(2, albums.size)
        assertEquals("MPREb_E7NpKw54NIT", albums.first().browseId)
    }

    @Test
    fun `a lockup podcast grid imports every podcast it carries`() {
        val page = LibraryResponseParser.pageOf(response("library_podcasts_lockup.json"))

        assertEquals(1, page.items.filterIsInstance<PlaylistItem>().size)
    }

    @Test
    fun `a grid wrapped in an item section is read`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {
                     "responseContext": {},
                     "contents": {
                      "singleColumnBrowseResultsRenderer": {
                       "tabs": [
                        {
                         "tabRenderer": {
                          "content": {
                           "sectionListRenderer": {
                            "contents": [
                             {
                              "itemSectionRenderer": {
                               "gridRenderer": {
                                "items": [
                                 {
                                  "musicTwoRowItemRenderer": {
                                   "title": {
                                    "runs": [
                                     {
                                      "text": "Saved Album"
                                     }
                                    ]
                                   },
                                   "navigationEndpoint": {
                                    "browseEndpoint": {
                                     "browseId": "MPREb_ALBUM",
                                     "browseEndpointContextSupportedConfigs": {
                                      "browseEndpointContextMusicConfig": {
                                       "pageType": "MUSIC_PAGE_TYPE_ALBUM"
                                      }
                                     }
                                    }
                                   },
                                   "thumbnailRenderer": {
                                    "musicThumbnailRenderer": {
                                     "thumbnail": {
                                      "thumbnails": [
                                       {
                                        "url": "https://lh3.googleusercontent.com/a",
                                        "width": 120,
                                        "height": 120
                                       }
                                      ]
                                     }
                                    }
                                   },
                                   "thumbnailOverlay": {
                                    "musicItemThumbnailOverlayRenderer": {
                                     "content": {
                                      "musicPlayButtonRenderer": {
                                       "playNavigationEndpoint": {
                                        "watchEndpoint": {
                                         "videoId": "v",
                                         "playlistId": "OLAK5uy_album"
                                        }
                                       }
                                      }
                                     }
                                    }
                                   }
                                  }
                                 }
                                ]
                               }
                              }
                             }
                            ]
                           }
                          }
                         }
                        }
                       ]
                      }
                     }
                    }
                    """.trimIndent(),
                ),
            )

        assertEquals("an item section wrapping a grid must not be walked past", 1, page.items.size)
        assertTrue(page.items.single() is AlbumItem)
    }

    @Test
    fun `a grid nested inside a shelf row is read`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {
                     "responseContext": {},
                     "contents": {
                      "singleColumnBrowseResultsRenderer": {
                       "tabs": [
                        {
                         "tabRenderer": {
                          "content": {
                           "sectionListRenderer": {
                            "contents": [
                             {
                              "musicShelfRenderer": {
                               "contents": [
                                {
                                 "gridRenderer": {
                                  "items": [
                                   {
                                    "musicTwoRowItemRenderer": {
                                     "title": {
                                      "runs": [
                                       {
                                        "text": "Nested Playlist"
                                       }
                                      ]
                                     },
                                     "navigationEndpoint": {
                                      "browseEndpoint": {
                                       "browseId": "VLPLnested",
                                       "browseEndpointContextSupportedConfigs": {
                                        "browseEndpointContextMusicConfig": {
                                         "pageType": "MUSIC_PAGE_TYPE_PLAYLIST"
                                        }
                                       }
                                      }
                                     },
                                     "thumbnailRenderer": {
                                      "musicThumbnailRenderer": {
                                       "thumbnail": {
                                        "thumbnails": [
                                         {
                                          "url": "https://lh3.googleusercontent.com/n",
                                          "width": 120,
                                          "height": 120
                                         }
                                        ]
                                       }
                                      }
                                     }
                                    }
                                   }
                                  ]
                                 }
                                }
                               ]
                              }
                             }
                            ]
                           }
                          }
                         }
                        }
                       ]
                      }
                     }
                    }
                    """.trimIndent(),
                ),
            )

        assertEquals("a grid nested in a shelf row must not be walked past", 1, page.items.size)
        assertTrue(page.items.single() is PlaylistItem)
    }

    @Test
    fun `a one-section page reads the next token that hangs off the section list`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{
                         "contents":[{"gridRenderer":{"items":[]}}],
                         "continuations":[{"reloadContinuationData":{"continuation":"SECTION_TOKEN"}}]
                       }}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        assertEquals("SECTION_TOKEN", page.continuation)
    }

    @Test
    fun `a page whose grid appends its token as the last item still continues`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
                         {"gridRenderer":{"items":[
                           {"continuationItemRenderer":{"continuationEndpoint":
                             {"continuationCommand":{"token":"TRAILING_TOKEN"}}}}
                         ]}}
                       ]}}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        assertEquals("TRAILING_TOKEN", page.continuation)
    }

    @Test
    fun `a multi-section page does not hand the list token to its first section`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{
                         "contents":[
                           {"gridRenderer":{"items":[]}},
                           {"gridRenderer":{"items":[]}}
                         ],
                         "continuations":[{"reloadContinuationData":{"continuation":"LIST_TOKEN"}}]
                       }}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        assertEquals(null, page.continuation)
    }

    @Test
    fun `saved playlists keep the playable ones and skip the create tile`() {
        val page = LibraryResponseParser.pageOf(response("library_playlists_large.json"))

        val playlists = page.items.filterIsInstance<PlaylistItem>()
        assertEquals(4, playlists.size)
        assertTrue(playlists.any { it.title == "Liked Music" })
        assertTrue("the create tile has nothing to open", page.items.none { it is PlaylistItem && it.title == "New playlist" })
    }

    @Test
    fun `saved albums survive the plain watch endpoint on their play tile`() {
        val page = LibraryResponseParser.pageOf(response("library_albums_large.json"))

        val albums = page.items.filterIsInstance<AlbumItem>()
        assertEquals(3, albums.size)
        assertEquals("MPREb_E7NpKw54NIT", albums.first().browseId)
        assertEquals("OLAK5uy_m8Yan_MSAia_ZYmZpl6xm1AINDg8LF1NI", albums.first().playlistId)
    }

    @Test
    fun `saved artists are kept even though they carry no shuffle or radio action`() {
        val page = LibraryResponseParser.pageOf(response("library_artists_library.json"))

        val artists = page.items.filterIsInstance<ArtistItem>()
        assertEquals(3, artists.size)
        assertEquals("Kendrick Lamar", artists.first().title)
    }

    @Test
    fun `a grid continuation is read as a page of albums`() {
        val page = LibraryResponseParser.continuationPageOf(response("cont_albums.json"))

        assertEquals(2, page.items.size)
        assertTrue(page.items.all { it is AlbumItem })
    }

    @Test
    fun `a section list continuation keeps the token that continues the library`() {
        val page = LibraryResponseParser.continuationPageOf(response("cont_playlists.json"))

        assertEquals(3, page.items.size)
        assertTrue(page.items.all { it is PlaylistItem })
        assertNotNull("the next token must survive, or the library stops after one page", page.continuation)
    }

    @Test
    fun `a shelf continuation prefers the token on the shelf`() {
        val page = LibraryResponseParser.continuationPageOf(response("cont_artists.json"))

        assertEquals(25, page.items.size)
        assertTrue(page.items.all { it is ArtistItem })
        assertNotNull(page.continuation)
    }

    /** One grid row for a liked song, in the shape the library serves it. */
    private fun songRow(
        id: String,
        title: String,
    ): String =
        """
        {"musicTwoRowItemRenderer":{
          "title":{"runs":[{"text":"$title"}]},
          "subtitle":{"runs":[{"text":"An Artist",
            "navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}}]},
          "thumbnailRenderer":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://lh3.googleusercontent.com/$id","width":120,"height":120}]}}},
          "navigationEndpoint":{"watchEndpoint":{"videoId":"$id"}}
        }}
        """.trimIndent()

    /** A library page whose liked songs arrive as a grid of two-row song rows. */
    private fun likedSongsGridPage(
        ids: List<Pair<String, String>>,
        next: String?,
    ): BrowseResponse {
        val rows = ids.joinToString(",") { songRow(it.first, it.second) }
        val continuation =
            if (next == null) "" else ""","continuations":[{"reloadContinuationData":{"continuation":"$next"}}]"""
        return decode(
            """
            {"responseContext":{},
             "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
               {"tabRenderer":{"content":{"sectionListRenderer":{
                 "contents":[{"gridRenderer":{"items":[$rows]}}]$continuation
               }}}}
             ]}}}
            """.trimIndent(),
        )
    }

    /** One shelf row for a liked song, in the shape a playlist shelf serves it. */
    private fun songShelfRow(
        id: String,
        title: String,
    ): String =
        """
        {"musicResponsiveListItemRenderer":{
          "playlistItemData":{"videoId":"$id"},
          "flexColumns":[
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"$title"}]}}},
            {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
              {"text":"An Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCartist"}}}]}}}
          ],
          "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
            {"url":"https://lh3.googleusercontent.com/$id","width":120,"height":120}]}}}
        }}
        """.trimIndent()

    /** A liked-songs continuation, as a playlist shelf carrying the next token. */
    private fun likedSongsContinuation(
        ids: List<Pair<String, String>>,
        next: String?,
    ): BrowseResponse {
        val rows = ids.joinToString(",") { songShelfRow(it.first, it.second) }
        val continuation =
            if (next == null) "" else ""","continuations":[{"nextContinuationData":{"continuation":"$next"}}]"""
        return decode(
            """
            {"responseContext":{},
             "continuationContents":{"musicPlaylistShelfContinuation":{
               "contents":[$rows]$continuation
             }}}
            """.trimIndent(),
        )
    }

    @Test
    fun `a grid of two-row song rows imports every song`() {
        val page = LibraryResponseParser.pageOf(likedSongsGridPage(listOf("v1" to "One", "v2" to "Two"), null))

        assertEquals("a two-row song row is a song, not a shape to walk past", 2, page.items.size)
        assertTrue(page.items.all { it is SongItem })
        assertEquals("v1", page.items.first().id)
    }

    @Test
    fun `a liked-songs page keeps the token that continues it`() {
        val page = LibraryResponseParser.pageOf(likedSongsGridPage(listOf("v1" to "One"), "NEXT"))

        assertEquals(1, page.items.size)
        assertEquals("NEXT", page.continuation)
    }

    @Test
    fun `a liked-songs continuation served as a playlist shelf is read`() {
        val page = LibraryResponseParser.continuationPageOf(likedSongsContinuation(listOf("v2" to "Two"), "NEXT2"))

        assertEquals(1, page.items.size)
        assertTrue(page.items.single() is SongItem)
        assertEquals("NEXT2", page.continuation)
    }

    @Test
    fun `every liked song is walked, not just the first page`() {
        val firstPage = (1..50).map { "v$it" to "Song $it" }
        val secondPage = (51..77).map { "v$it" to "Song $it" }

        val first = LibraryResponseParser.pageOf(likedSongsGridPage(firstPage, "PAGE2"))
        val second = LibraryResponseParser.continuationPageOf(likedSongsContinuation(secondPage, null))

        assertEquals("the first page is read whole", 50, first.items.size)
        assertEquals("the continuation is read whole", 27, second.items.size)
        assertEquals("50 + 27 is the account's 77 likes", 77, (first.items + second.items).distinctBy { it.id }.size)
    }

    @Test
    fun `a liked-songs page served as a playlist shelf is read`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{
                         "contents":[{"musicPlaylistShelfRenderer":{"contents":[
                           {"musicResponsiveListItemRenderer":{
                             "playlistItemData":{"videoId":"v1"},
                             "flexColumns":[
                               {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"One"}]}}},
                               {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                                 {"text":"An Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCa"}}}]}}}
                             ],
                             "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                               {"url":"https://lh3.googleusercontent.com/v1","width":120,"height":120}]}}}
                           }}
                         ]}}]
                       }}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        assertEquals(1, page.items.size)
        assertTrue(page.items.single() is SongItem)
    }

    @Test
    fun `a liked-songs continuation handed back as an append action is read`() {
        val page =
            LibraryResponseParser.continuationPageOf(
                decode(
                    """
                    {"responseContext":{},
                     "onResponseReceivedActions":[{"appendContinuationItemsAction":{"continuationItems":[
                       {"musicResponsiveListItemRenderer":{
                         "playlistItemData":{"videoId":"v9"},
                         "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Nine"}]}}}],
                         "thumbnail":{"musicThumbnailRenderer":{"thumbnail":{"thumbnails":[
                           {"url":"https://lh3.googleusercontent.com/v9","width":120,"height":120}]}}}
                       }},
                       {"continuationItemRenderer":{"continuationEndpoint":{"continuationCommand":{"token":"MORE"}}}}
                     ]}}]}
                    """.trimIndent(),
                ),
            )

        assertEquals(1, page.items.size)
        assertEquals("v9", page.items.single().id)
        assertEquals("MORE", page.continuation)
    }

    @Test
    fun `a library served as lockup rows still imports its saved playlists`() {
        val page = LibraryResponseParser.pageOf(response("library_playlists_lockup.json"))

        val playlists = page.items.filterIsInstance<PlaylistItem>()
        assertEquals("every lockup row must be read, not walked past", 3, playlists.size)
        assertTrue(playlists.any { it.title == "Sam Sulek Playlist" })
        assertTrue("a lockup playlist must carry a thumbnail", playlists.all { !it.thumbnail.isNullOrBlank() })
    }

    @Test
    fun `a lockup podcast row is imported as a playlist`() {
        val page = LibraryResponseParser.pageOf(response("library_podcasts_lockup.json"))

        assertEquals(1, page.items.filterIsInstance<PlaylistItem>().size)
    }

    @Test
    fun `a library served as lockup artist rows imports its artists`() {
        val page = LibraryResponseParser.pageOf(response("library_artists_lockup.json"))

        val artists = page.items.filterIsInstance<ArtistItem>()
        assertEquals(3, artists.size)
        assertEquals("Ariana Grande", artists.first().title)
        assertTrue("an artist row must keep a channel id", artists.all { it.channelId != null })
    }

    @Test
    fun `a library served as lockup album rows imports its albums`() {
        val page = LibraryResponseParser.pageOf(response("library_albums_lockup.json"))

        val albums = page.items.filterIsInstance<AlbumItem>()
        assertEquals(2, albums.size)
        assertEquals("MPREb_E7NpKw54NIT", albums.first().browseId)
        assertEquals(
            "the album's playlist id comes from its play action",
            "OLAK5uy_m8Yan_MSAia_ZYmZpl6xm1AINDg8LF1NI",
            albums.first().playlistId,
        )
    }

    @Test
    fun `a lockup grid still reads its continuation`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
                         {"gridRenderer":{"items":[
                           {"lockupViewModel":{"contentId":"PL1","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST",
                             "metadata":{"lockupMetadataViewModel":{"title":{"content":"One"}}}}},
                           {"continuationItemRenderer":{"continuationEndpoint":
                             {"continuationCommand":{"token":"LOCKUP_NEXT"}}}}
                         ]}}
                       ]}}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        assertEquals(1, page.items.size)
        assertEquals("LOCKUP_NEXT", page.continuation)
    }

    @Test
    fun `lockup rows wrapped in an item section are still read`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{
                         "contents":[{"itemSectionRenderer":{"contents":[
                           {"lockupViewModel":{"contentId":"PL1","contentType":"LOCKUP_CONTENT_TYPE_PLAYLIST",
                             "metadata":{"lockupMetadataViewModel":{"title":{"content":"One"}}}}}
                         ]}}],
                         "continuations":[{"reloadContinuationData":{"continuation":"WRAPPED_NEXT"}}]
                       }}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        assertEquals(1, page.items.size)
        assertTrue(page.items.single() is PlaylistItem)
        assertEquals("WRAPPED_NEXT", page.continuation)
    }

    @Test
    fun `a continuation of lockup rows is read with its next token`() {
        val page =
            LibraryResponseParser.continuationPageOf(
                decode(
                    """
                    {"responseContext":{},
                     "continuationContents":{"gridContinuation":{
                       "items":[
                         {"lockupViewModel":{"contentId":"MPREb_1","contentType":"LOCKUP_CONTENT_TYPE_ALBUM",
                           "itemPlayback":{"inlinePlayerData":{"onSelect":{"innertubeCommand":{"watchEndpoint":
                             {"videoId":"v1","playlistId":"OLAK5uy_1"}}}}},
                           "metadata":{"lockupMetadataViewModel":{"title":{"content":"An Album"}}}}},
                         {"continuationItemRenderer":{"continuationEndpoint":
                           {"continuationCommand":{"token":"NEXT_ALBUMS"}}}}
                       ]
                     }}}
                    """.trimIndent(),
                ),
            )

        val album = page.items.single() as AlbumItem
        assertEquals("MPREb_1", album.browseId)
        assertEquals("OLAK5uy_1", album.playlistId)
        assertEquals("NEXT_ALBUMS", page.continuation)
    }

    @Test
    fun `lockup artist rows reach the subscriptions store`() {
        val subscriptions = LibraryResponseParser.pageOf(response("library_artists_lockup.json")).items.toSubscribedArtists()

        assertEquals(3, subscriptions.size)
        assertTrue("an imported artist must be subscribed by channel id", subscriptions.all { it.channelId.startsWith("UC") })
    }

    @Test
    fun `lockup playlist rows reach the saved collections`() {
        val collections = LibraryResponseParser.pageOf(response("library_playlists_lockup.json")).items.toSavedMusicCollections()

        assertEquals("every saved playlist must survive as a collection", 3, collections.size)
        assertTrue(collections.all { it.id.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun `a liked-songs page served two-column imports every song`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
                       "contents":[{"musicPlaylistShelfRenderer":{"playlistId":"LM","contents":[
                         {"musicResponsiveListItemRenderer":{
                           "playlistItemData":{"videoId":"v1","playlistSetVideoId":"SET1"},
                           "flexColumns":[
                             {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"One"}]}}},
                             {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                               {"text":"An Artist","navigationEndpoint":{"browseEndpoint":{"browseId":"UCa"}}}]}}}
                           ],
                           "fixedColumns":[{"musicResponsiveListItemFixedColumnRenderer":{"text":{"runs":[{"text":"3:21"}]}}}]
                         }}
                       ]}}]
                     }}}}}
                    """.trimIndent(),
                ),
            )

        assertEquals("a two-column liked-songs page must not be walked past", 1, page.items.size)
        val song = page.items.single() as SongItem
        assertEquals("v1", song.id)
        assertEquals("An Artist", song.artists.single().name)
    }

    @Test
    fun `a liked song whose third column is a play count is still imported`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
                       "contents":[{"musicPlaylistShelfRenderer":{"playlistId":"LM","contents":[
                         {"musicResponsiveListItemRenderer":{
                           "playlistItemData":{"videoId":"IluRBvnYMoY"},
                           "flexColumns":[
                             {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                               {"text":"Give Life Back to Music","navigationEndpoint":{"watchEndpoint":{"videoId":"IluRBvnYMoY"}}}]}}},
                             {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                               {"text":"Daft Punk","navigationEndpoint":{"browseEndpoint":{"browseId":"UCRr1xG"}}}]}}},
                             {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"55M plays"}]}}}
                           ]
                         }}
                       ]}}]
                     }}}}}
                    """.trimIndent(),
                ),
            )

        val song = page.items.single() as SongItem
        assertEquals("a play-count third column must not drop the song", "IluRBvnYMoY", song.id)
        assertEquals("Daft Punk", song.artists.single().name)
        assertTrue("a row with no album column has no album", song.album == null)
        assertTrue("the video id builds a thumbnail when the row omits one", song.thumbnail.contains("IluRBvnYMoY"))
    }

    @Test
    fun `a liked song is read when only its title run names the video`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
                       "contents":[{"musicPlaylistShelfRenderer":{"playlistId":"LM","contents":[
                         {"musicResponsiveListItemRenderer":{
                           "flexColumns":[
                             {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                               {"text":"A song with no playlistItemData","navigationEndpoint":{"watchEndpoint":{"videoId":"bbbbbbbbbb2"}}}]}}},
                             {"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[
                               {"text":"Some Uploader","navigationEndpoint":{"browseEndpoint":{"browseId":"UCuploader"}}}]}}}
                           ]
                         }}
                       ]}}]
                     }}}}}
                    """.trimIndent(),
                ),
            )

        val song = page.items.single() as SongItem
        assertEquals("the title run's watch endpoint names the video", "bbbbbbbbbb2", song.id)
    }

    @Test
    fun `a liked-songs page served two-column keeps its next-page token`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
                       "contents":[{"musicPlaylistShelfRenderer":{"playlistId":"LM","contents":[
                         {"musicResponsiveListItemRenderer":{
                           "playlistItemData":{"videoId":"v1"},
                           "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"One"}]}}}]
                         }}
                       ],"continuations":[{"nextContinuationData":{"continuation":"TWO_COL_NEXT"}}]}}]
                     }}}}}
                    """.trimIndent(),
                ),
            )

        assertEquals("the shelf's own token must continue the walk", "TWO_COL_NEXT", page.continuation)
    }

    @Test
    fun `every liked song is walked across a two-column first page and its continuation`() {
        val first =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
                       "contents":[{"musicPlaylistShelfRenderer":{"playlistId":"LM","contents":[
                         {"musicResponsiveListItemRenderer":{
                           "playlistItemData":{"videoId":"v1"},
                           "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"One"}]}}}]
                         }}
                       ],"continuations":[{"nextContinuationData":{"continuation":"PAGE2"}}]}}]
                     }}}}}
                    """.trimIndent(),
                ),
            )
        val second =
            LibraryResponseParser.continuationPageOf(
                decode(
                    """
                    {"responseContext":{},
                     "continuationContents":{"musicPlaylistShelfContinuation":{"contents":[
                       {"musicResponsiveListItemRenderer":{
                         "playlistItemData":{"videoId":"v2"},
                         "flexColumns":[{"musicResponsiveListItemFlexColumnRenderer":{"text":{"runs":[{"text":"Two"}]}}}]
                       }}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        assertEquals("PAGE2", first.continuation)
        assertEquals("both pages of a liked-songs list are read", listOf("v1", "v2"), (first.items + second.items).map { it.id })
    }

    @Test
    fun `a liked-songs page served as video lockups imports every song`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
                       "contents":[{"gridRenderer":{"items":[
                         {"lockupViewModel":{
                           "contentId":"pvSdeU13hKc",
                           "contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
                           "contentImage":{"thumbnailViewModel":{"image":{"sources":[
                             {"url":"https://i.ytimg.com/vi/pvSdeU13hKc/hq720.jpg","width":720,"height":404}]}}},
                           "metadata":{"lockupMetadataViewModel":{
                             "title":{"content":"A liked song "},
                             "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[
                               {"text":{"content":"950K views"}},{"text":{"content":"21 hours ago"}}]}]}}}}
                         }},
                         {"lockupViewModel":{
                           "contentId":"VfQdPRyqQW4",
                           "contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
                           "contentImage":{"thumbnailViewModel":{"image":{"sources":[
                             {"url":"https://i.ytimg.com/vi/VfQdPRyqQW4/hq720.jpg","width":720,"height":404}]}}},
                           "metadata":{"lockupMetadataViewModel":{"title":{"content":"Another liked song "}}}
                         }}
                       ]}}]
                     }}}}}
                    """.trimIndent(),
                ),
            )

        val songs = page.items.filterIsInstance<SongItem>()
        assertEquals("a liked song arrives as a VIDEO lockup, which must not be walked past", 2, songs.size)
        assertEquals(listOf("pvSdeU13hKc", "VfQdPRyqQW4"), songs.map { it.id })
        assertEquals("A liked song ", songs.first().title)
    }

    @Test
    fun `a real signed-in video-lockup grid imports every row`() {
        val page = LibraryResponseParser.pageOf(response("account_history.json"))

        val songs = page.items.filterIsInstance<SongItem>()
        assertEquals("every row of a real VIDEO lockup grid must be read", 4, songs.size)
        assertEquals("pvSdeU13hKc", songs.first().id)
        assertTrue("a video lockup names the row's video id as its content id", songs.all { it.id.isNotBlank() && it.title.isNotBlank() })
    }

    @Test
    fun `a song lockup row is still imported`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{"contents":[
                         {"gridRenderer":{"items":[
                           {"lockupViewModel":{
                             "contentId":"MPREb_song",
                             "contentType":"LOCKUP_CONTENT_TYPE_SONG",
                             "metadata":{"lockupMetadataViewModel":{"title":{"content":"A song lockup"}}}}}
                         ]}}
                       ]}}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        val song = page.items.single() as SongItem
        assertEquals("MPREb_song", song.id)
        assertEquals("A song lockup", song.title)
    }

    @Test
    fun `a video lockup reads the channel it links to as the artist`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
                       "contents":[{"gridRenderer":{"items":[
                         {"lockupViewModel":{
                           "contentId":"aaaaaaaaaaa",
                           "contentType":"LOCKUP_CONTENT_TYPE_VIDEO",
                           "metadata":{"lockupMetadataViewModel":{
                             "title":{"content":"A song"},
                             "metadata":{"contentMetadataViewModel":{"metadataRows":[{"metadataParts":[
                               {"text":{"content":"Daft Punk","commandRuns":[{"onTap":{"innertubeCommand":
                                 {"browseEndpoint":{"browseId":"UCRr1xG"}}}}]}}]}]}}}}
                         }}
                       ]}}]
                     }}}}}
                    """.trimIndent(),
                ),
            )

        val song = page.items.single() as SongItem
        assertEquals("Daft Punk", song.artists.single().name)
        assertEquals("UCRr1xG", song.artists.single().id)
    }

    @Test
    fun `a liked-songs grid whose next token is a modern continuation item keeps it`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{
                         "contents":[{"gridRenderer":{"items":[
                           ${songRow("v1", "One")},
                           {"continuationItemViewModel":{"continuationCommand":{"token":"PAGE2"}}}
                         ]}}]
                       }}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        assertEquals("the first page is read whole", 1, page.items.size)
        assertEquals(
            "a grid that closes with a continuationItemViewModel must still hand out its next token",
            "PAGE2",
            page.continuation,
        )
    }

    @Test
    fun `a liked-songs grid whose token nests under innertubeCommand keeps it`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{
                         "contents":[{"gridRenderer":{"items":[
                           ${songRow("v1", "One")},
                           {"continuationItemViewModel":{"continuationCommand":{
                             "innertubeCommand":{"continuationCommand":{"token":"PAGE2"}}
                           }}}
                         ]}}]
                       }}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )

        assertEquals("PAGE2", page.continuation)
    }

    @Test
    fun `a liked-songs shelf continuation whose token is a modern item keeps it`() {
        val page =
            LibraryResponseParser.continuationPageOf(
                decode(
                    """
                    {"responseContext":{},
                     "continuationContents":{"musicPlaylistShelfContinuation":{
                       "contents":[
                         ${songShelfRow("v2", "Two")},
                         {"continuationItemViewModel":{"continuationCommand":{"token":"NEXT2"}}}
                       ]
                     }}}
                    """.trimIndent(),
                ),
            )

        assertEquals(1, page.items.size)
        assertEquals("NEXT2", page.continuation)
    }

    @Test
    fun `a two-column liked-songs page whose token is a modern section item keeps it`() {
        val page =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"twoColumnBrowseResultsRenderer":{"secondaryContents":{"sectionListRenderer":{
                       "contents":[
                         {"musicPlaylistShelfRenderer":{"playlistId":"LM","contents":[
                           ${songShelfRow("v1", "One")}
                         ]}},
                         {"continuationItemViewModel":{"continuationCommand":{"token":"PAGE2"}}}
                       ]
                     }}}}}
                    """.trimIndent(),
                ),
            )

        assertEquals(1, page.items.size)
        assertEquals("PAGE2", page.continuation)
    }

    @Test
    fun `every liked song is walked when the token arrives as a modern item`() {
        val first =
            LibraryResponseParser.pageOf(
                decode(
                    """
                    {"responseContext":{},
                     "contents":{"singleColumnBrowseResultsRenderer":{"tabs":[
                       {"tabRenderer":{"content":{"sectionListRenderer":{
                         "contents":[{"gridRenderer":{"items":[
                           ${songRow("v1", "One")},
                           {"continuationItemViewModel":{"continuationCommand":{"token":"PAGE2"}}}
                         ]}}]
                       }}}}
                     ]}}}
                    """.trimIndent(),
                ),
            )
        val second =
            LibraryResponseParser.continuationPageOf(
                decode(
                    """
                    {"responseContext":{},
                     "continuationContents":{"musicPlaylistShelfContinuation":{
                       "contents":[${songShelfRow("v2", "Two")}]
                     }}}
                    """.trimIndent(),
                ),
            )

        assertEquals("PAGE2", first.continuation)
        assertEquals(
            "both pages of a liked-songs list are read",
            listOf("v1", "v2"),
            (first.items + second.items).map { it.id },
        )
    }
}
