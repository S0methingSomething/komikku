# Komikku Plus - Future Ideas

## Performance Improvements (Priority)

### 1. RateLimitInterceptor Optimization
- **File:** `core/common/src/main/kotlin/eu/kanade/tachiyomi/network/interceptor/RateLimitInterceptor.kt`
- **Change:** `notifyAll()` → `notify()` (line ~107)
- **Benefit:** Reduces unnecessary thread wakeups
- **Risk:** 🟢 Low
- **Effort:** 5 min

### 2. Parallel Update Slots (Configurable)
- **File:** `LibraryUpdateJob.kt` line 394
- **Current:** Hardcoded `Semaphore(5)`
- **Change:** Add preference (1-5 range, default 5)
- **Benefit:** Faster library updates
- **Risk:** 🟡 Medium (rate limiting)
- **Effort:** 30 min

### 3. Parallel Chapter Downloads (Per Source)
- **File:** `Downloader.kt` line 217
- **Current:** Only 1 chapter per source at a time (`.first()`)
- **Change:** Add `parallelChapterLimit` preference, use `.take(n)`
- **Benefit:** Much faster downloads
- **Risk:** 🟡 Medium
- **Effort:** 30 min

### 4. Library Update Cache Skip
- **Change:** Option to force network requests during updates
- **Benefit:** Always fresh data
- **Risk:** 🟢 Low
- **Effort:** 20 min

---

## Debug Mode Power User Options

### 5. Ignore Rate Limits (Debug Only)
- **Location:** Advanced > Network (only if debug mode ON)
- **Warning:** "May cause IP bans"
- **Risk:** 🔴 High (user's choice)
- **Effort:** 20 min

### 6. Max Concurrent Requests (Debug Only)
- **Range:** 64-256
- **Location:** Advanced > Network (debug only)
- **Risk:** 🔴 High
- **Effort:** 20 min

### 7. Extended Parallel Slots (Debug Only)
- **Range:** 6-10 (beyond safe 1-5)
- **Location:** Advanced > Library (debug only)
- **Effort:** 10 min

---

## Feature Ideas

### 8. Auto-Reread Tracker Reset
- **Source:** xkana-shii/komikku
- **Description:** When re-reading manga, auto-reset tracker progress
- **Options:** Reset to 0 / Reset to current chapter
- **Risk:** 🟢 Low
- **Effort:** 1-2 hours

### 9. Profiles (Multi-User)
- **Spec:** `docs/profiles_spec.md`
- **Description:** Separate libraries, trackers, settings per profile
- **Risk:** 🟡 Medium (complex)
- **Effort:** Large project

---

## Reference Forks

| Fork | Useful For |
|------|------------|
| aSimpleGuy/komikku | Performance ideas (aggressive) |
| xkana-shii/komikku | Auto-reread feature |

---

## Completed Features (This Session)
- ✅ Reading Prompts (auto-add to library + forced tracking)
- ✅ Unit tests for GetReadingPromptState

## Previously Completed
- ✅ Private Keyboard Mode
- ✅ Internal Storage Option
- ✅ Samsung Secure Folder Fix
- ✅ Storage Selection UI
