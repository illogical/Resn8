package com.app.resn8.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFilterSheet(
    currentSort: SortOrder,
    currentFilters: LibraryFilterSnapshot,
    onSortSelected: (SortOrder) -> Unit,
    onAvailabilitySelected: (AvailabilityFilter) -> Unit,
    onToggleExcludeDisliked: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Sort By", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Column {
                SortOrder.entries.forEach { sort ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        RadioButton(
                            selected = currentSort == sort,
                            onClick = { onSortSelected(sort) }
                        )
                        Text(sort.name.replace("_", " "))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Availability", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.padding(vertical = 8.dp)) {
                AvailabilityFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = currentFilters.availability == filter,
                        onClick = { onAvailabilitySelected(filter) },
                        label = { Text(filter.name.replace("_", " ")) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Exclude Disliked Tracks", modifier = Modifier.weight(1f))
                Switch(
                    checked = currentFilters.excludeDisliked,
                    onCheckedChange = { onToggleExcludeDisliked() }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Done")
            }
        }
    }
}
