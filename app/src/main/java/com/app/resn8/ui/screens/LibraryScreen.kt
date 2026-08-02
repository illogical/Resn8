package com.app.resn8.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("artists", "albums", "tracks")
    val selectedIndex = tabs.indexOf(currentTab).coerceAtLeast(0)

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedIndex) {
            tabs.forEachIndexed { index, tabName ->
                Tab(
                    selected = selectedIndex == index,
                    onClick = { onTabSelected(tabName) },
                    text = { Text(tabName.replaceFirstChar { it.uppercase() }) }
                )
            }
        }
        Text(
            text = "Library View: ${tabs[selectedIndex].replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp)
        )
    }
}
