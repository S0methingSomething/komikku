package eu.kanade.presentation.track

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import eu.kanade.presentation.components.withPrivateMode
import eu.kanade.tachiyomi.data.track.Tracker
import eu.kanade.tachiyomi.data.track.model.TrackSearch
import tachiyomi.i18n.MR
import tachiyomi.i18n.kmk.KMR
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.util.runOnEnterKeyPressed

// S0M -->
sealed class TrackerSearchState {
    data object Idle : TrackerSearchState()
    data object Loading : TrackerSearchState()
    data class Success(val results: List<TrackSearch>) : TrackerSearchState()
    data class Error(val message: String) : TrackerSearchState()
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MultiTrackerSearch(
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
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current

    Column(modifier = modifier) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                state = searchState,
                modifier = Modifier
                    .weight(1f)
                    .runOnEnterKeyPressed {
                        onSearch()
                        focusManager.clearFocus()
                    },
                textStyle = MaterialTheme.typography.bodyLarge
                    .copy(color = MaterialTheme.colorScheme.onSurface),
                lineLimits = TextFieldLineLimits.SingleLine,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search).withPrivateMode(),
                onKeyboardAction = {
                    onSearch()
                    focusManager.clearFocus()
                },
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorator = { innerTextField ->
                    if (searchState.text.isEmpty()) {
                        Text(
                            text = stringResource(MR.strings.action_search_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    innerTextField()
                },
            )
            IconButton(onClick = {
                onSearch()
                focusManager.clearFocus()
            }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(MR.strings.action_search),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.small))

        // Tracker chips
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MaterialTheme.padding.medium),
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            trackers.forEach { tracker ->
                val isLinked = tracker.id in alreadyLinkedTrackerIds
                val isEnabled = tracker.id in enabledTrackerIds || isLinked

                FilterChip(
                    selected = isEnabled,
                    onClick = { if (!isLinked) onToggleTracker(tracker.id) },
                    label = { Text(tracker.name) },
                    leadingIcon = if (isLinked) {
                        { Icon(Icons.Default.Lock, contentDescription = null) }
                    } else if (isEnabled) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    enabled = !isLinked,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = MaterialTheme.padding.small))

        // Results per tracker
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(vertical = MaterialTheme.padding.small),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            trackers.filter { it.id in enabledTrackerIds || it.id in alreadyLinkedTrackerIds }.forEach { tracker ->
                val state = searchResults[tracker.id] ?: TrackerSearchState.Idle
                val isLinked = tracker.id in alreadyLinkedTrackerIds

                item(key = "header-${tracker.id}") {
                    Text(
                        text = tracker.name + if (isLinked) " ✓" else "",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                    )
                }

                when (state) {
                    is TrackerSearchState.Idle -> {
                        // Show nothing or hint
                    }
                    is TrackerSearchState.Loading -> {
                        item(key = "loading-${tracker.id}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(MaterialTheme.padding.medium),
                                horizontalArrangement = Arrangement.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                    is TrackerSearchState.Success -> {
                        if (state.results.isEmpty()) {
                            item(key = "empty-${tracker.id}") {
                                Text(
                                    text = stringResource(MR.strings.no_results_found),
                                    modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            items(
                                items = state.results,
                                key = { "result-${tracker.id}-${it.hashCode()}" },
                            ) { result ->
                                SearchResultItem(
                                    trackSearch = result,
                                    selected = selectedResults[tracker.id] == result,
                                    onClick = { onSelectResult(tracker.id, result) },
                                )
                            }
                        }
                    }
                    is TrackerSearchState.Error -> {
                        item(key = "error-${tracker.id}") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = MaterialTheme.padding.medium),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { onRetry(tracker.id) }) {
                                    Text(stringResource(MR.strings.action_retry))
                                }
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider()

        // Don't track checkbox
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MaterialTheme.padding.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                checked = skipTrackingChecked,
                onCheckedChange = onSkipTrackingChange,
            )
            Text(
                text = stringResource(KMR.strings.reading_prompt_dont_track_series),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
// S0M <--
