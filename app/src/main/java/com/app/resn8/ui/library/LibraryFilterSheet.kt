package com.app.resn8.ui.library

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.resn8.domain.model.LibrarySortField
import com.app.resn8.domain.model.LibrarySortSelection
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.SortDirection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySortSheet(
    currentSurface: LibrarySurface,
    currentSort: LibrarySortSelection,
    onFieldSelected: (LibrarySortField) -> Unit,
    onDirectionSelected: (SortDirection) -> Unit,
    onDismiss: () -> Unit
) {
    val fields = when (currentSurface) {
        LibrarySurface.ARTISTS,
        LibrarySurface.ALBUMS -> listOf(LibrarySortField.ALPHABETICAL)
        LibrarySurface.ALL_TRACKS -> listOf(
            LibrarySortField.ALPHABETICAL,
            LibrarySortField.ARTIST,
            LibrarySortField.ALBUM,
            LibrarySortField.DATE_ADDED,
            LibrarySortField.PLAY_COUNT,
            LibrarySortField.LAST_PLAYED,
            LibrarySortField.RATING
        )
        LibrarySurface.FOLDERS -> emptyList()
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp
            )
        ) {
            item {
                Text(
                    text = "Sort By",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(fields, key = { it.name }) { field ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = currentSort.field == field,
                        onClick = { onFieldSelected(field) }
                    )
                    Text(field.displayLabel())
                }
            }
            item {
                Text(
                    text = "Direction",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SortDirection.entries.forEachIndexed { index, direction ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = SortDirection.entries.size
                            ),
                            selected = currentSort.direction == direction,
                            onClick = { onDirectionSelected(direction) },
                            label = { Text(direction.displayLabel()) }
                        )
                    }
                }
            }
        }
    }
}

private fun LibrarySortField.displayLabel(): String = when (this) {
    LibrarySortField.ALPHABETICAL -> "Alphabetical"
    LibrarySortField.ARTIST -> "Artist"
    LibrarySortField.ALBUM -> "Album"
    LibrarySortField.DATE_ADDED -> "Date Added"
    LibrarySortField.PLAY_COUNT -> "Play Count"
    LibrarySortField.LAST_PLAYED -> "Last Played"
    LibrarySortField.RATING -> "Rating"
}

private fun SortDirection.displayLabel(): String = when (this) {
    SortDirection.ASCENDING -> "Ascending"
    SortDirection.DESCENDING -> "Descending"
}
