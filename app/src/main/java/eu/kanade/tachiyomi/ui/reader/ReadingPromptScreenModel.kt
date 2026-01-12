package eu.kanade.tachiyomi.ui.reader

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.manga.interactor.UpdateManga
import eu.kanade.domain.track.interactor.AddTracks
import eu.kanade.presentation.track.TrackerSearchState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import tachiyomi.core.common.util.lang.withIOContext
import tachiyomi.domain.category.interactor.GetCategories
import tachiyomi.domain.category.interactor.SetMangaCategories
import tachiyomi.domain.category.model.Category
import tachiyomi.domain.manga.model.MangaUpdate
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

// S0M -->
class ReadingPromptScreenModel(
    private val mangaId: Long,
    private val mangaTitle: String,
    val trackers: List<Tracker>,
    val alreadyLinkedTrackerIds: Set<Long>,
    private val getCategories: GetCategories = Injekt.get(),
    private val setMangaCategories: SetMangaCategories = Injekt.get(),
    private val addTracks: AddTracks = Injekt.get(),
    private val updateManga: UpdateManga = Injekt.get(),
) : StateScreenModel<ReadingPromptScreenModel.State>(State()) {

    val searchState = TextFieldState(mangaTitle)
    private val searchJobs = mutableMapOf<Long, Job>()

    init {
        screenModelScope.launch {
            val categories = getCategories.await().filterNot { it.isSystemCategory }
            mutableState.update { it.copy(categories = categories) }
        }
        // Auto-search on init
        search()
    }

    fun search() {
        val query = searchState.text.toString()
        if (query.isBlank()) return

        val enabledIds = mutableState.value.enabledTrackerIds + alreadyLinkedTrackerIds
        trackers.filter { it.id in enabledIds }.forEach { tracker ->
            searchTracker(tracker, query)
        }
    }

    private fun searchTracker(tracker: Tracker, query: String) {
        searchJobs[tracker.id]?.cancel()
        searchJobs[tracker.id] = screenModelScope.launch {
            mutableState.update {
                it.copy(searchResults = it.searchResults + (tracker.id to TrackerSearchState.Loading))
            }
            val result = withIOContext {
                try {
                    val results = tracker.search(query)
                    TrackerSearchState.Success(results)
                } catch (e: Exception) {
                    TrackerSearchState.Error(e.message ?: "Unknown error")
                }
            }
            mutableState.update {
                it.copy(searchResults = it.searchResults + (tracker.id to result))
            }
            // Auto-select exact match
            if (result is TrackerSearchState.Success) {
                val exactMatch = result.results.find { it.title.equals(mangaTitle, ignoreCase = true) }
                if (exactMatch != null && mutableState.value.selectedResults[tracker.id] == null) {
                    mutableState.update {
                        it.copy(selectedResults = it.selectedResults + (tracker.id to exactMatch))
                    }
                }
            }
        }
    }

    fun toggleTracker(trackerId: Long) {
        if (trackerId in alreadyLinkedTrackerIds) return
        mutableState.update {
            val newEnabled = if (trackerId in it.enabledTrackerIds) {
                it.enabledTrackerIds - trackerId
            } else {
                it.enabledTrackerIds + trackerId
            }
            it.copy(enabledTrackerIds = newEnabled)
        }
    }

    fun selectResult(trackerId: Long, result: TrackSearch) {
        mutableState.update {
            it.copy(selectedResults = it.selectedResults + (trackerId to result))
        }
    }

    fun toggleCategory(categoryId: Long) {
        mutableState.update {
            val newSelected = if (categoryId in it.selectedCategoryIds) {
                it.selectedCategoryIds - categoryId
            } else {
                it.selectedCategoryIds + categoryId
            }
            it.copy(selectedCategoryIds = newSelected)
        }
    }

    fun setSkipTracking(skip: Boolean) {
        mutableState.update { it.copy(skipTrackingChecked = skip) }
    }

    fun retrySearch(trackerId: Long) {
        val tracker = trackers.find { it.id == trackerId } ?: return
        searchTracker(tracker, searchState.text.toString())
    }

    suspend fun confirm(addToLibrary: Boolean): Boolean {
        return withIOContext {
            try {
                val state = mutableState.value

                // Add to library with categories
                if (addToLibrary) {
                    updateManga.await(MangaUpdate(id = mangaId, favorite = true))
                    val categoryIds = state.selectedCategoryIds.toList().ifEmpty { listOf(0L) }
                    setMangaCategories.await(mangaId, categoryIds)
                }

                // Register tracks
                if (!state.skipTrackingChecked) {
                    state.selectedResults.forEach { (trackerId, trackSearch) ->
                        if (trackerId !in alreadyLinkedTrackerIds) {
                            val tracker = trackers.find { it.id == trackerId }
                            tracker?.register(trackSearch, mangaId)
                        }
                    }
                }

                // Set skip tracking if checked
                if (state.skipTrackingChecked) {
                    updateManga.await(MangaUpdate(id = mangaId, skipTracking = true))
                }

                true
            } catch (e: Exception) {
                false
            }
        }
    }

    @Immutable
    data class State(
        val categories: List<Category> = emptyList(),
        val selectedCategoryIds: Set<Long> = emptySet(),
        val enabledTrackerIds: Set<Long> = emptySet(),
        val searchResults: Map<Long, TrackerSearchState> = emptyMap(),
        val selectedResults: Map<Long, TrackSearch> = emptyMap(),
        val skipTrackingChecked: Boolean = false,
    )
}
// S0M <--
