# NeuroTube v1.6.1

**Release date:** 2026-11-22

Fix for missing YouTube and YouTube Music liked songs and videos in library import.

## Fixes

- **Full Liked Songs and Music Videos Import (`VLLM` & `VLLL`)**:
  - Replaced the truncated browse ID `FEmusic_liked_videos` (which previously returned only a single page without continuation) with the playlist endpoint `VLLM` with complete pagination tokens.
  - Added support for YouTube's main Liked Videos playlist (`VLLL`), allowing tracks and music videos liked on the YouTube website or app to be properly imported.
  - Automatically classifies videos from YouTube likes into Liked Music or Liked Videos based on music category, tags, and music video markers.
- **Deep Pagination Support**:
  - Increased playlist continuation limit from 50 to 150 pages, ensuring large libraries with thousands of liked songs are imported whole.
- **Automatic Library Refresh for Existing Accounts**:
  - Bumped the account import version to 7 so that accounts already signed in will automatically re-import their complete library.
