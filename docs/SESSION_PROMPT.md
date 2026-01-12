# Session Continuation Prompt

Copy and paste this to start a new session:

---

I'm implementing a new feature for Komikku (Mihon fork): **Auto-Add to Library + Forced Tracking Prompts**.

Please:
1. Load TODO list ID: `1768198721279`
2. Read the implementation plan: `docs/READING_PROMPTS_PLAN.md`
3. Read these files needed for Task 1 (database field):
   - `data/src/main/sqldelight/tachiyomi/data/mangas.sq`
   - `data/src/main/sqldelight/tachiyomi/migrations/41.sqm` (latest migration example)
   - `domain/src/main/java/tachiyomi/domain/manga/model/Manga.kt`
   - `domain/src/main/java/tachiyomi/domain/manga/model/MangaUpdate.kt`
   - `data/src/main/java/tachiyomi/data/manga/MangaMapper.kt`
   - `data/src/main/java/tachiyomi/data/manga/MangaRepositoryImpl.kt`

Then start implementing Task 1: Add `skip_tracking` database field.

Key points:
- Use migration `1000.sqm` (high number to avoid upstream conflicts)
- Add `skip_tracking INTEGER AS Boolean NOT NULL DEFAULT 0` to mangas table
- Update Manga model, MangaUpdate, MangaMapper, and repository
- Use thinking tool extensively
- Write minimal code

---
