package eu.kanade.presentation.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.category.visualName
import eu.kanade.presentation.track.MultiTrackerSearch
import eu.kanade.presentation.track.TrackerSearchState
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import tachiyomi.domain.category.model.Category
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource

// S0M -->
@Composable
fun ReadingPromptDialog(
    showLibraryPrompt: Boolean,
    showTrackingPrompt: Boolean,
    // Category selection
    categories: List<Category>,
    selectedCategoryIds: Set<Long>,
    onToggleCategory: (Long) -> Unit,
    // Tracker search
    searchState: TextFieldState,
    onSearch: () -> Unit,
    trackers: List<Tracker>,
    enabledTrackerIds: Set<Long>,
    onToggleTracker: (Long) -> Unit,
    alreadyLinkedTrackerIds: Set<Long>,
    searchResults: Map<Long, TrackerSearchState>,
    selectedResults: Map<Long, TrackSearch>,
    onSelectResult: (trackerId: Long, result: TrackSearch) -> Unit,
    onRetry: (trackerId: Long) -> Unit,
    skipTrackingChecked: Boolean,
    onSkipTrackingChange: (Boolean) -> Unit,
    // Actions
    onNotNow: () -> Unit,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    val title = when {
        showLibraryPrompt && showTrackingPrompt -> stringResource(KMR.strings.reading_prompt_add_and_track)
        showLibraryPrompt -> stringResource(KMR.strings.reading_prompt_add_to_library)
        showTrackingPrompt -> stringResource(KMR.strings.reading_prompt_set_up_tracking)
        else -> ""
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp),
            ) {
                if (showLibraryPrompt) {
                    Text(
                        text = stringResource(KMR.strings.reading_prompt_select_categories),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Column(
                        modifier = Modifier
                            .weight(if (showTrackingPrompt) 0.3f else 1f)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        categories.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleCategory(category.id) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = category.id in selectedCategoryIds,
                                    onCheckedChange = { onToggleCategory(category.id) },
                                )
                                Text(
                                    text = category.visualName,
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                )
                            }
                        }
                    }
                }

                if (showLibraryPrompt && showTrackingPrompt) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.small))
                }

                if (showTrackingPrompt) {
                    MultiTrackerSearch(
                        searchState = searchState,
                        onSearch = onSearch,
                        trackers = trackers,
                        enabledTrackerIds = enabledTrackerIds,
                        onToggleTracker = onToggleTracker,
                        alreadyLinkedTrackerIds = alreadyLinkedTrackerIds,
                        searchResults = searchResults,
                        selectedResults = selectedResults,
                        onSelectResult = onSelectResult,
                        onRetry = onRetry,
                        skipTrackingChecked = skipTrackingChecked,
                        onSkipTrackingChange = onSkipTrackingChange,
                        modifier = Modifier.weight(if (showLibraryPrompt) 0.7f else 1f),
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onNotNow) {
                    Text(text = stringResource(KMR.strings.reading_prompt_not_now))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onConfirm) {
                    Text(text = stringResource(KMR.strings.reading_prompt_confirm))
                }
            }
        },
    )
}
// S0M <--
