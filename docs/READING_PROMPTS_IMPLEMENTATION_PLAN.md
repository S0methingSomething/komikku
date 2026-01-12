# Auto-Add to Library + Forced Tracking Prompts

## Problem Statement

Users want automatic prompts to add manga to library and set up tracking after reading a configurable number of chapters, with the ability to exclude specific manga from tracking prompts.

---

## Requirements

1. **Auto-add to library** after threshold (default 3 chapters) with category selection dialog
2. **Forced tracking prompt** with two trigger modes: "every time" or "after threshold"
3. **Combined dialog** when both thresholds match
4. **Multi-tracker search menu** with toggleable tracker chips
5. **Three dismissal states**: "Not now" (session ignore), "Don't track this series" (permanent), Global disable
6. **Required trackers** configurable in settings + per-tracker toggle + in prompt menu
7. **Respect incognito mode** (no prompts)
8. **Show prompt at chapter transition**, not mid-scroll
9. **Handle partial tracking** (some trackers already linked)
10. **Per-tracker loading states** and error handling

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         ReaderViewModel                          │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ sessionIgnoredMangaIds: MutableSet<Long>                    ││
│  │ readingPromptState: StateFlow<ReadingPromptState?>          ││
│  └─────────────────────────────────────────────────────────────┘│
│                              │                                   │
│              updateChapterProgressOnComplete()                   │
│                              │                                   │
│                              ▼                                   │
│              GetReadingPromptState.await(mangaId)               │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                   GetReadingPromptState                          │
│  Checks: incognito, enabled, skipTracking, sessionIgnored,      │
│          readCount, library status, existing tracks             │
│  Returns: ReadingPromptState                                     │
└─────────────────────────────────────────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────────┐
│                    ReadingPromptDialog                           │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ ReadingPromptScreenModel                                  │  │
│  │  - searchJobs: Map<Long, Job>                             │  │
│  │  - results: Map<Long, Result<List<TrackSearch>>>          │  │
│  │  - selectedResults: Map<Long, TrackSearch>                │  │
│  │  - enabledTrackers: Set<Long>                             │  │
│  │  - alreadyLinkedTrackers: Set<Long> (locked/checked)      │  │
│  └───────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### UI Flow

```
┌─────────────────────────────────────────────────────────────────┐
│              Chapter completed → ChapterTransition shown         │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  Check: !incognitoMode && feature enabled?                      │
└─────────────────────────────────────────────────────────────────┘
                                │ Yes
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  GetReadingPromptState interactor checks:                       │
│  - readCount >= threshold?                                      │
│  - !inLibrary OR !trackedOnRequiredTrackers?                    │
│  - !skipTracking for this manga?                                │
│  Returns: NONE | ShowPrompt(...)                                │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│              ReadingPromptDialog                                 │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ "Add to library and set up tracking?"                       ││
│  ├─────────────────────────────────────────────────────────────┤│
│  │ Search: [manga title________________] 🔍  (auto-searched)   ││
│  ├─────────────────────────────────────────────────────────────┤│
│  │ [AniList ✓] [MAL ✓] [Kitsu ○]  ← click to toggle           ││
│  ├─────────────────────────────────────────────────────────────┤│
│  │ AniList results: (loading...) / [Result 1 ✓] [Result 2]    ││
│  │ MAL results: [Result 1 ✓] [Result 2]                        ││
│  ├─────────────────────────────────────────────────────────────┤│
│  │ ☐ Don't track this series (permanent)                       ││
│  ├─────────────────────────────────────────────────────────────┤│
│  │ [Not Now]  [Skip Tracking]  [Confirm]                       ││
│  └─────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────┘
```

---

## Task Breakdown

### Task 1: Add database field for skip_tracking flag

**Files to modify:**
- `data/src/main/sqldelight/tachiyomi/migrations/1000.sqm` (CREATE)
- `data/src/main/sqldelight/tachiyomi/data/mangas.sq`
- `domain/src/main/java/tachiyomi/domain/manga/model/Manga.kt`
- `domain/src/main/java/tachiyomi/domain/manga/model/MangaUpdate.kt`
- `data/src/main/java/tachiyomi/data/manga/MangaMapper.kt`
- `data/src/main/java/tachiyomi/data/manga/MangaRepositoryImpl.kt`

**Implementation:**
```sql
-- 1000.sqm
ALTER TABLE mangas ADD COLUMN skip_tracking INTEGER AS Boolean NOT NULL DEFAULT 0;
```

**Demo:** Can set/get skipTracking for a manga via repository

---

### Task 2: Add preferences for auto-add and tracking features

**Files to modify:**
- `domain/src/main/java/tachiyomi/domain/library/service/LibraryPreferences.kt`

**Implementation:**
```kotlin
// Add ForcedTrackingMode enum
enum class ForcedTrackingMode {
    EVERY_TIME,
    AFTER_THRESHOLD,
}

// Add preferences
fun autoAddToLibraryEnabled() = preferenceStore.getBoolean("auto_add_to_library_enabled", false)
fun autoAddToLibraryThreshold() = preferenceStore.getInt("auto_add_to_library_threshold", 3)
fun forcedTrackingEnabled() = preferenceStore.getBoolean("forced_tracking_enabled", false)
fun forcedTrackingMode() = preferenceStore.getEnum("forced_tracking_mode", ForcedTrackingMode.AFTER_THRESHOLD)
fun forcedTrackingThreshold() = preferenceStore.getInt("forced_tracking_threshold", 3)
fun requiredTrackerIds() = preferenceStore.getStringSet("required_tracker_ids", emptySet())
```

**Demo:** Preferences read/write correctly

---

### Task 3: Add required tracker settings UI

**Files to modify:**
- `app/src/main/java/eu/kanade/presentation/more/settings/screen/SettingsTrackingScreen.kt`

**Implementation:**
- Add "Reading Prompts" section with:
  - "Auto-add to library" toggle + threshold slider (1-10)
  - "Prompt for tracking" toggle + mode dropdown + threshold slider
  - "Required trackers" multi-select showing logged-in trackers
- Per logged-in tracker row: Add "Required" chip/toggle

**Demo:** All settings visible and functional

---

### Task 4: Create GetReadingPromptState interactor

**Files to create:**
- `app/src/main/java/eu/kanade/domain/manga/interactor/GetReadingPromptState.kt`

**Implementation:**
```kotlin
sealed class ReadingPromptState {
    object None : ReadingPromptState()
    data class ShowPrompt(
        val showLibraryPrompt: Boolean,
        val showTrackingPrompt: Boolean,
        val missingTrackers: List<Tracker>,
        val alreadyLinkedTrackers: List<Tracker>,
    ) : ReadingPromptState()
}

class GetReadingPromptState(
    private val libraryPreferences: LibraryPreferences,
    private val basePreferences: BasePreferences,
    private val getTracks: GetTracks,
    private val getChaptersByMangaId: GetChaptersByMangaId,
    private val getManga: GetManga,
    private val trackerManager: TrackerManager,
) {
    suspend fun await(
        mangaId: Long,
        sessionIgnoredIds: Set<Long>,
    ): ReadingPromptState {
        // 1. Return None if incognito mode
        // 2. Return None if both features disabled
        // 3. Return None if manga.skipTracking == true
        // 4. Return None if mangaId in sessionIgnoredIds
        // 5. Get read chapter count for manga
        // 6. Check library status and tracking status on required trackers
        // 7. Determine which prompts apply based on thresholds
    }
}
```

**Demo:** Unit tests for all scenarios

---

### Task 5: Create Multi-Tracker Search UI component

**Files to create:**
- `app/src/main/java/eu/kanade/presentation/track/MultiTrackerSearch.kt`

**Implementation:**
- Search bar (pre-filled with manga title, auto-searches on init)
- Tracker chips row: Each chip shows tracker icon + name, toggleable
- Already-linked trackers shown as checked/locked chips
- Results section: LazyColumn with headers per tracker
- Per-tracker: Loading indicator OR results OR error with retry
- Auto-select logic: If result.title exactly matches manga.title (case-insensitive), pre-select it
- Bottom: "Don't track this series" checkbox

**Demo:** Renders with mock data, toggles work, loading states show

---

### Task 6: Create ReadingPromptDialog

**Files to create:**
- `app/src/main/java/eu/kanade/presentation/reader/ReadingPromptDialog.kt`

**Implementation:**
- Adaptive content based on `ReadingPromptState.ShowPrompt`:
  - Title: "Add to library?" / "Set up tracking?" / "Add to library and track?"
  - If showLibraryPrompt: Category selection (reuse `ChangeCategoryDialog` internals)
  - If showTrackingPrompt: Embed `MultiTrackerSearch`
- Buttons:
  - "Not Now" → dismiss, add to session ignore
  - "Don't Track" (if tracking prompt) → set skipTracking = true
  - "Confirm" → execute actions

**Demo:** Dialog adapts to different states correctly

---

### Task 7: Create ReadingPromptScreenModel

**Files to create:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReadingPromptScreenModel.kt`

**Implementation:**
```kotlin
data class State(
    val mangaTitle: String,
    val searchQuery: String,
    val enabledTrackerIds: Set<Long>,
    val alreadyLinkedTrackerIds: Set<Long>,
    val searchResults: Map<Long, SearchState>, // Loading/Success/Error per tracker
    val selectedResults: Map<Long, TrackSearch>,
    val skipTrackingChecked: Boolean,
    val selectedCategories: List<Long>,
)

sealed class SearchState {
    object Loading : SearchState()
    data class Success(val results: List<TrackSearch>) : SearchState()
    data class Error(val message: String) : SearchState()
}
```

**Actions:**
- `init`: Auto-search all enabled trackers with manga title
- `search(query)`: Search all enabled trackers, use `async` per tracker with individual try-catch
- `toggleTracker(id)`: Enable/disable tracker (can't toggle already-linked)
- `selectResult(trackerId, result)`: Update selection
- `confirm()`: Register tracks + add to library via SetMangaCategories

**Demo:** Full flow works with real trackers

---

### Task 8: Integrate into ReaderViewModel

**Files to modify:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`

**Implementation:**
```kotlin
private val sessionIgnoredMangaIds = mutableSetOf<Long>()
private val _readingPromptState = MutableStateFlow<ReadingPromptState?>(null)
val readingPromptState = _readingPromptState.asStateFlow()

// In updateChapterProgressOnComplete():
val promptState = getReadingPromptState.await(manga.id, sessionIgnoredMangaIds)
if (promptState !is ReadingPromptState.None) {
    _readingPromptState.value = promptState
}

// Handlers:
fun onPromptNotNow() { sessionIgnoredMangaIds.add(mangaId); _readingPromptState.value = null }
fun onPromptSkipTracking() { updateManga(skipTracking = true); _readingPromptState.value = null }
fun onPromptConfirm(categories, tracks) { /* add to library + register tracks */ }
```

**Demo:** Completing 3rd chapter of non-library manga shows prompt

---

### Task 9: Integrate dialog into ReaderActivity

**Files to modify:**
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderActivity.kt`

**Implementation:**
```kotlin
val promptState by viewModel.readingPromptState.collectAsState()
promptState?.let { state ->
    if (state is ReadingPromptState.ShowPrompt) {
        ReadingPromptDialog(
            state = state,
            mangaTitle = manga.title,
            onNotNow = viewModel::onPromptNotNow,
            onSkipTracking = viewModel::onPromptSkipTracking,
            onConfirm = viewModel::onPromptConfirm,
        )
    }
}
```

**Demo:** Full reader flow works end-to-end

---

### Task 10: Add strings and polish

**Files to modify:**
- `i18n/src/commonMain/moko-resources/base/strings.xml`

**Strings to add:**
```xml
<string name="pref_auto_add_to_library">Auto-add to library</string>
<string name="pref_auto_add_to_library_summary">Prompt to add manga to library after reading chapters</string>
<string name="pref_forced_tracking">Prompt for tracking</string>
<string name="pref_forced_tracking_summary">Prompt to set up tracking on required trackers</string>
<string name="pref_required_trackers">Required trackers</string>
<string name="pref_tracking_threshold">Chapter threshold</string>
<string name="pref_tracking_mode_every_time">Every time</string>
<string name="pref_tracking_mode_after_threshold">After threshold</string>
<string name="reading_prompt_add_to_library">Add to library?</string>
<string name="reading_prompt_set_up_tracking">Set up tracking?</string>
<string name="reading_prompt_add_and_track">Add to library and track?</string>
<string name="reading_prompt_finish_tracking">Finish setting up tracking</string>
<string name="reading_prompt_not_now">Not now</string>
<string name="reading_prompt_dont_track">Don\'t track</string>
<string name="reading_prompt_dont_track_series">Don\'t track this series</string>
<string name="tracker_search_no_results">No results found</string>
<string name="tracker_search_error_retry">Error loading results. Tap to retry.</string>
```

**Edge cases:**
- No logged-in trackers → hide tracking prompt entirely
- All required trackers already linked → hide tracking prompt
- Network error on one tracker → show error with retry, don't block others

**Demo:** Feature complete, all strings localized, errors handled gracefully

---

## Implementation Notes

### From Gemini Review

1. **Session Ignore**: `sessionIgnoredMangaIds` in ViewModel prevents re-prompting until reader is closed

2. **Title Matching**: Case-insensitive exact match for auto-select; don't auto-select if multiple close matches or no exact match

3. **Concurrent Search**: 
   ```kotlin
   trackerIds.map { id -> 
       async { runCatching { tracker.search(query) } }
   }.awaitAll()
   ```
   Individual try-catch per tracker so one failure doesn't block others

4. **Search Job Cancellation**: Cancel existing job before starting new search:
   ```kotlin
   searchJobs[trackerId]?.cancel()
   searchJobs[trackerId] = scope.launch { ... }
   ```

5. **Trigger Point**: Hook into chapter completion flow, show dialog when `ChapterTransition.Next` becomes active

6. **Migration Safety**: Using `1000.sqm` avoids upstream Mihon conflicts

7. **Partial Tracking**: Already-linked trackers shown as locked/checked chips, dialog says "Finish setting up tracking"

8. **Category Default**: If no category selected, use default category (ID 0) via `SetMangaCategories` interactor

9. **SQLDelight**: Must update both `1000.sqm` AND `mangas.sq` CREATE TABLE for compiler to see new field

---

## Testing Checklist

- [ ] Skip tracking flag persists in database
- [ ] Preferences save and load correctly
- [ ] Settings UI shows all options
- [ ] Incognito mode prevents prompts
- [ ] Session ignore works (Not Now doesn't re-prompt immediately)
- [ ] Skip tracking flag works (Don't Track prevents future prompts)
- [ ] Multi-tracker search shows results from all trackers
- [ ] Per-tracker loading states work
- [ ] Auto-select works for exact matches
- [ ] Partial tracking shows already-linked as locked
- [ ] Category selection works
- [ ] Confirm action adds to library and registers tracks
- [ ] Network errors handled gracefully per-tracker
