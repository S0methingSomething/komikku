package eu.kanade.domain.manga.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.track.interactor.GetTracks

// S0M -->
sealed class ReadingPromptState {
    data object None : ReadingPromptState()
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
        if (basePreferences.incognitoMode().get()) {
            logcat(LogPriority.DEBUG) { "ReadingPrompt: None - incognito mode" }
            return ReadingPromptState.None
        }

        val autoAddEnabled = libraryPreferences.autoAddToLibraryEnabled().get()
        val trackingEnabled = libraryPreferences.forcedTrackingEnabled().get()

        logcat(LogPriority.DEBUG) { "ReadingPrompt: autoAddEnabled=$autoAddEnabled, trackingEnabled=$trackingEnabled" }

        // 2. Return None if both features disabled
        if (!autoAddEnabled && !trackingEnabled) {
            logcat(LogPriority.DEBUG) { "ReadingPrompt: None - both features disabled" }
            return ReadingPromptState.None
        }

        // 3. Return None if mangaId in sessionIgnoredIds
        if (mangaId in sessionIgnoredIds) {
            logcat(LogPriority.DEBUG) { "ReadingPrompt: None - manga in session ignored" }
            return ReadingPromptState.None
        }

        // 4. Get manga and check skipTracking
        val manga = getManga.await(mangaId) ?: run {
            logcat(LogPriority.DEBUG) { "ReadingPrompt: None - manga not found" }
            return ReadingPromptState.None
        }
        if (manga.skipTracking) {
            logcat(LogPriority.DEBUG) { "ReadingPrompt: None - skipTracking flag set" }
            return ReadingPromptState.None
        }

        // 5. Get read chapter count
        val chapters = getChaptersByMangaId.await(mangaId)
        val readCount = chapters.count { it.read }

        // 6. Check library status
        val inLibrary = manga.favorite

        // 7. Check tracking status
        val existingTracks = getTracks.await(mangaId)
        val linkedTrackerIds = existingTracks.map { it.trackerId }.toSet()

        val requiredTrackerIds = libraryPreferences.requiredTrackerIds().get()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        val loggedInTrackers = trackerManager.trackers.filter { it.isLoggedIn }

        // If no required trackers selected, use all logged-in trackers
        val trackersToCheck = if (requiredTrackerIds.isEmpty()) {
            loggedInTrackers
        } else {
            loggedInTrackers.filter { it.id in requiredTrackerIds }
        }

        val alreadyLinkedTrackers = trackersToCheck.filter { it.id in linkedTrackerIds }
        val missingTrackers = trackersToCheck.filter { it.id !in linkedTrackerIds }

        logcat(LogPriority.DEBUG) {
            "ReadingPrompt: readCount=$readCount, inLibrary=$inLibrary, " +
                "loggedInTrackers=${loggedInTrackers.size}, missingTrackers=${missingTrackers.size}"
        }

        // 8. Determine which prompts apply
        var showLibraryPrompt = false
        var showTrackingPrompt = false

        if (autoAddEnabled && !inLibrary) {
            val threshold = libraryPreferences.autoAddToLibraryThreshold().get()
            logcat(LogPriority.DEBUG) { "ReadingPrompt: library check - readCount=$readCount >= threshold=$threshold" }
            if (readCount >= threshold) {
                showLibraryPrompt = true
            }
        }

        if (trackingEnabled && loggedInTrackers.isNotEmpty() && missingTrackers.isNotEmpty()) {
            val threshold = libraryPreferences.forcedTrackingThreshold().get()
            logcat(LogPriority.DEBUG) { "ReadingPrompt: tracking check - readCount=$readCount >= threshold=$threshold" }
            if (readCount >= threshold) {
                showTrackingPrompt = true
            }
        }

        logcat(LogPriority.DEBUG) { "ReadingPrompt: showLibraryPrompt=$showLibraryPrompt, showTrackingPrompt=$showTrackingPrompt" }

        return if (showLibraryPrompt || showTrackingPrompt) {
            ReadingPromptState.ShowPrompt(
                showLibraryPrompt = showLibraryPrompt,
                showTrackingPrompt = showTrackingPrompt,
                missingTrackers = missingTrackers,
                alreadyLinkedTrackers = alreadyLinkedTrackers,
            )
        } else {
            ReadingPromptState.None
        }
    }
}
// S0M <--
