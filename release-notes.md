# NeuroTube v1.6.4

**Release date:** 2026-11-23

Fixes the liked-song importer build and restores YouTube Music liked-only songs to the unified Likes list.

## Fixes

- **YouTube Music liked songs:** Read the special `LM`/`VLLM` song playlist through the music playlist parser, with the library parser as a fallback. Both song and video likes remain in one Likes list.
- **Refresh existing song records:** If a song ID was already stored from the video likes list, account import updates its song classification and metadata without changing its local like date or order.
- **Complete song paging:** Follow every continuation until exhausted, record partial failures, and retry incomplete account imports on a later launch.
- **Existing accounts:** The account-import version is bumped so current users receive the corrected songs import automatically.
- **Build fix:** Corrects the importer function scope so account-library import compiles and can run.
