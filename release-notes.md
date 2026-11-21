# NeuroTube v1.6.0

**Release date:** 2026-11-17

A library import that stopped at the first list it found. An account whose likes, saved albums or
saved playlists spanned more than one list got a partial library, and nothing said so.

## Fixes

- **Every list on a page is walked, not just the first.** A library page can open with several
  shelves, and each shelf hands out its own next-page token. The parser kept only the first token,
  so the walk followed one shelf to its end and never read the others — the rest of a liked-songs
  list simply never arrived. All tokens on a page are now collected and queued, and the walk drains
  the queue, so each list is read to its end however many there are.
- **A next-page token is read from the modern shape too.** Continuations now arrive as
  `continuationCommand.token` rather than the older `nextContinuationData.continuation`. Only the old
  key was read, so a page served the new way handed back no token and the walk stopped after one
  page. Both shapes are read, on grids, shelves and rows.
- **A page served as `onResponseReceivedEndpoints` is read.** A continuation can be handed back as
  actions, commands or endpoints. The first two were handled; the third was not, so a page arriving
  that way ended the walk early. All three containers are now served.
- **Duplicates across branches are dropped.** Walking several lists can revisit an item that sits in
  more than one, so the imported library is de-duplicated by id.
- **The import runs again for accounts that already finished one.** The behaviour version is bumped,
  because an account an earlier import marked done would otherwise never see this fix.

## Notes

- The full unit suite passes: 4190 tests, 0 failures. Four new tests cover the multi-list token
  collection, the `continuationCommand` token shape and the endpoints container.
