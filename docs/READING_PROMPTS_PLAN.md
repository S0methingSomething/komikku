# Auto-Add to Library + Forced Tracking Prompts

## Summary
Prompt users to add manga to library and/or set up tracking after reading X chapters (default 3).

## Requirements
1. Auto-add to library after threshold with category selection
2. Forced tracking prompt (every time OR after threshold)
3. Combined dialog when thresholds match
4. Multi-tracker search (AniList/MAL/Kitsu simultaneously)
5. Three dismissals: "Not now" (session), "Don't track" (permanent), global disable
6. Required trackers configurable in settings + per-tracker + in menu
7. Respect incognito mode
8. Show at chapter transition, not mid-scroll
9. Handle partial tracking (some already linked)
10. Per-tracker loading/error states

## Tasks

### Task 1: Database field
- `1000.sqm`: `ALTER TABLE mangas ADD COLUMN skip_tracking INTEGER AS Boolean NOT NULL DEFAULT 0;`
- Update: `mangas.sq`, `Manga.kt`, `MangaUpdate.kt`, `MangaMapper.kt`, `MangaRepositoryImpl.kt`

### Task 2: Preferences
Add to `LibraryPreferences`:
- `autoAddToLibraryEnabled()` (default false)
- `autoAddToLibraryThreshold()` (default 3)
- `forcedTrackingEnabled()` (default false)
- `forcedTrackingMode()` (EVERY_TIME, AFTER_THRESHOLD)
- `forcedTrackingThreshold()` (default 3)
- `requiredTrackerIds()` (Set<String>)

### Task 3: Settings UI
In `SettingsTrackingScreen`: Reading Prompts section with toggles, sliders, required trackers multi-select

### Task 4: GetReadingPromptState interactor
Returns `None` or `ShowPrompt(showLibraryPrompt, showTrackingPrompt, missingTrackers, alreadyLinkedTrackers)`
Checks: incognito, enabled, skipTracking, sessionIgnored, readCount, library/tracking status

### Task 5: MultiTrackerSearch UI
Search bar, tracker chips (toggleable), per-tracker results/loading, auto-select exact matches, "don't track" checkbox

### Task 6: ReadingPromptDialog
Adapts to ShowPrompt state, category selection + multi-tracker search, buttons: Not Now/Don't Track/Confirm

### Task 7: ReadingPromptScreenModel
Manages search state per tracker, selections, categories, concurrent search with job cancellation

### Task 8: ReaderViewModel integration
Add `sessionIgnoredMangaIds`, `readingPromptState`. Call interactor in `updateChapterProgressOnComplete()`

### Task 9: ReaderActivity integration
Observe state, show dialog at chapter transition

### Task 10: Strings and polish

## Key Notes
- Use `1000.sqm` to avoid upstream migration conflicts
- Session ignore prevents re-prompt until reader closed
- Cancel search jobs before starting new search
- Auto-select only if single exact title match
- Already-linked trackers shown as locked chips


---

## Session Continuation Prompt

Copy-paste this to continue in a new session:

```
I'm implementing Auto-Add to Library + Forced Tracking Prompts for Komikku.

1. Load TODO: 1768198721279
2. Read: docs/READING_PROMPTS_PLAN.md
3. Read for Task 1: data/src/main/sqldelight/tachiyomi/data/mangas.sq, domain/src/main/java/tachiyomi/domain/manga/model/Manga.kt, domain/src/main/java/tachiyomi/domain/manga/model/MangaUpdate.kt, data/src/main/java/tachiyomi/data/manga/MangaMapper.kt, data/src/main/java/tachiyomi/data/manga/MangaRepositoryImpl.kt

Start Task 1: Add skip_tracking field. Use 1000.sqm migration. Use thinking tool extensively.
```
