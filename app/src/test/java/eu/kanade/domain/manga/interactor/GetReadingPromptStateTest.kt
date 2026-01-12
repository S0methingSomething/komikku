package eu.kanade.domain.manga.interactor

import eu.kanade.domain.base.BasePreferences
import eu.kanade.tachiyomi.data.track.BaseTracker
import eu.kanade.tachiyomi.data.track.TrackerManager
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import tachiyomi.core.common.preference.Preference
import tachiyomi.domain.chapter.interactor.GetChaptersByMangaId
import tachiyomi.domain.chapter.model.Chapter
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.interactor.GetManga
import tachiyomi.domain.manga.model.Manga
import tachiyomi.domain.track.interactor.GetTracks
import tachiyomi.domain.track.model.Track

// S0M -->
class GetReadingPromptStateTest {

    private lateinit var libraryPreferences: LibraryPreferences
    private lateinit var basePreferences: BasePreferences
    private lateinit var getTracks: GetTracks
    private lateinit var getChaptersByMangaId: GetChaptersByMangaId
    private lateinit var getManga: GetManga
    private lateinit var trackerManager: TrackerManager
    private lateinit var getReadingPromptState: GetReadingPromptState

    private val testMangaId = 1L
    private val testManga = Manga.create().copy(id = testMangaId, favorite = false)

    @BeforeEach
    fun setup() {
        libraryPreferences = mockk()
        basePreferences = mockk()
        getTracks = mockk()
        getChaptersByMangaId = mockk()
        getManga = mockk()
        trackerManager = mockk()

        // Default preferences
        every { basePreferences.incognitoMode() } returns mockPreference(false)
        every { libraryPreferences.autoAddToLibraryEnabled() } returns mockPreference(true)
        every { libraryPreferences.autoAddToLibraryThreshold() } returns mockPreference(3)
        every { libraryPreferences.forcedTrackingEnabled() } returns mockPreference(false)
        every { libraryPreferences.forcedTrackingThreshold() } returns mockPreference(3)
        every { libraryPreferences.requiredTrackerIds() } returns mockPreference(emptySet())
        every { trackerManager.trackers } returns emptyList()

        coEvery { getManga.await(testMangaId) } returns testManga
        coEvery { getChaptersByMangaId.await(testMangaId) } returns createChapters(5, read = 3)
        coEvery { getTracks.await(testMangaId) } returns emptyList()

        getReadingPromptState = GetReadingPromptState(
            libraryPreferences,
            basePreferences,
            getTracks,
            getChaptersByMangaId,
            getManga,
            trackerManager,
        )
    }

    @Test
    fun `returns None when incognito mode is on`() = runTest {
        every { basePreferences.incognitoMode() } returns mockPreference(true)

        val result = getReadingPromptState.await(testMangaId, emptySet())

        result shouldBe ReadingPromptState.None
    }

    @Test
    fun `returns None when both features disabled`() = runTest {
        every { libraryPreferences.autoAddToLibraryEnabled() } returns mockPreference(false)
        every { libraryPreferences.forcedTrackingEnabled() } returns mockPreference(false)

        val result = getReadingPromptState.await(testMangaId, emptySet())

        result shouldBe ReadingPromptState.None
    }

    @Test
    fun `returns None when mangaId in sessionIgnoredIds`() = runTest {
        val result = getReadingPromptState.await(testMangaId, setOf(testMangaId))

        result shouldBe ReadingPromptState.None
    }

    @Test
    fun `returns None when manga skipTracking is true`() = runTest {
        coEvery { getManga.await(testMangaId) } returns testManga.copy(skipTracking = true)

        val result = getReadingPromptState.await(testMangaId, emptySet())

        result shouldBe ReadingPromptState.None
    }

    @Test
    fun `returns None when readCount below threshold`() = runTest {
        coEvery { getChaptersByMangaId.await(testMangaId) } returns createChapters(5, read = 2)

        val result = getReadingPromptState.await(testMangaId, emptySet())

        result shouldBe ReadingPromptState.None
    }

    @Test
    fun `returns ShowPrompt with showLibraryPrompt when not in library and threshold met`() = runTest {
        val result = getReadingPromptState.await(testMangaId, emptySet())

        result.shouldBeInstanceOf<ReadingPromptState.ShowPrompt>()
        (result as ReadingPromptState.ShowPrompt).showLibraryPrompt shouldBe true
    }

    @Test
    fun `returns None for library prompt when already in library`() = runTest {
        coEvery { getManga.await(testMangaId) } returns testManga.copy(favorite = true)
        every { libraryPreferences.forcedTrackingEnabled() } returns mockPreference(false)

        val result = getReadingPromptState.await(testMangaId, emptySet())

        result shouldBe ReadingPromptState.None
    }

    @Test
    fun `returns ShowPrompt with showTrackingPrompt when missing required trackers`() = runTest {
        every { libraryPreferences.autoAddToLibraryEnabled() } returns mockPreference(false)
        every { libraryPreferences.forcedTrackingEnabled() } returns mockPreference(true)
        every { libraryPreferences.requiredTrackerIds() } returns mockPreference(setOf("1"))

        val tracker = mockk<BaseTracker>()
        every { tracker.id } returns 1L
        every { tracker.isLoggedIn } returns true
        every { trackerManager.trackers } returns listOf(tracker)

        val result = getReadingPromptState.await(testMangaId, emptySet())

        result.shouldBeInstanceOf<ReadingPromptState.ShowPrompt>()
        (result as ReadingPromptState.ShowPrompt).showTrackingPrompt shouldBe true
        result.missingTrackers shouldBe listOf(tracker)
    }

    @Test
    fun `returns None for tracking when all required trackers linked`() = runTest {
        every { libraryPreferences.autoAddToLibraryEnabled() } returns mockPreference(false)
        every { libraryPreferences.forcedTrackingEnabled() } returns mockPreference(true)
        every { libraryPreferences.requiredTrackerIds() } returns mockPreference(setOf("1"))

        val tracker = mockk<BaseTracker>()
        every { tracker.id } returns 1L
        every { tracker.isLoggedIn } returns true
        every { trackerManager.trackers } returns listOf(tracker)

        coEvery { getTracks.await(testMangaId) } returns listOf(
            Track(
                id = 1,
                mangaId = testMangaId,
                trackerId = 1L,
                remoteId = 100,
                libraryId = null,
                title = "",
                lastChapterRead = 0.0,
                totalChapters = 0,
                status = 0,
                score = 0.0,
                remoteUrl = "",
                startDate = 0,
                finishDate = 0,
                private = false,
            ),
        )

        val result = getReadingPromptState.await(testMangaId, emptySet())

        result shouldBe ReadingPromptState.None
    }

    @Test
    fun `tracking prompt triggers when threshold is 0`() = runTest {
        every { libraryPreferences.autoAddToLibraryEnabled() } returns mockPreference(false)
        every { libraryPreferences.forcedTrackingEnabled() } returns mockPreference(true)
        every { libraryPreferences.forcedTrackingThreshold() } returns mockPreference(0)
        every { libraryPreferences.requiredTrackerIds() } returns mockPreference(setOf("1"))

        val tracker = mockk<BaseTracker>()
        every { tracker.id } returns 1L
        every { tracker.isLoggedIn } returns true
        every { trackerManager.trackers } returns listOf(tracker)

        // With 0 read chapters and threshold 0, should trigger
        coEvery { getChaptersByMangaId.await(testMangaId) } returns createChapters(5, read = 0)

        val result = getReadingPromptState.await(testMangaId, emptySet())

        result.shouldBeInstanceOf<ReadingPromptState.ShowPrompt>()
        (result as ReadingPromptState.ShowPrompt).showTrackingPrompt shouldBe true
    }

    private fun createChapters(total: Int, read: Int): List<Chapter> {
        return (1..total).map { i ->
            Chapter.create().copy(
                id = i.toLong(),
                mangaId = testMangaId,
                read = i <= read,
            )
        }
    }

    private fun <T> mockPreference(value: T): Preference<T> {
        val pref = mockk<Preference<T>>()
        every { pref.get() } returns value
        return pref
    }
}
// S0M <--
