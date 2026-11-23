# NeuroTube v1.6.2

**Release date:** 2026-11-23

Combines liked songs and videos under one Likes entry and fixes incomplete signed-in likes imports.

## Fixes

- **One Likes category:** The Library now shows videos and songs together in the same Likes shelf and list.
- **Complete likes paging:** The signed-in YouTube likes list follows continuation tokens until the end, including response-endpoint continuation pages. A repeated token or failed page is recorded with the partial count, and an incomplete import is eligible to retry on a later launch.
- **Accurate list parsing:** Playlist video renderers and continuation actions, commands, and endpoints are handled when reading YouTube liked videos.
- **Existing accounts:** The account-import version is bumped again so signed-in users receive this fix automatically.
