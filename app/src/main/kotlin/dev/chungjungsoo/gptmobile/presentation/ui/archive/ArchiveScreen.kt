package dev.chungjungsoo.gptmobile.presentation.ui.archive

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(
    onBackAction: () -> Unit,
    viewModel: ArchiveViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.archived_roles)) },
                navigationIcon = {
                    TextButton(onClick = onBackAction) {
                        Text(text = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(uiState.archivedRoles, key = { it.id }) { role ->
                ListItem(
                    headlineContent = { Text(text = role.name) },
                    supportingContent = { Text(text = stringResource(R.string.role_group_value, role.groupName)) },
                    trailingContent = {
                        androidx.compose.foundation.layout.Row {
                            IconButton(onClick = { viewModel.restore(role.id) }) {
                                Icon(imageVector = Icons.Outlined.Restore, contentDescription = stringResource(R.string.restore_role))
                            }
                            IconButton(onClick = { viewModel.requestDelete(role) }) {
                                Icon(imageVector = Icons.Outlined.Delete, contentDescription = stringResource(R.string.delete_permanently))
                            }
                        }
                    }
                )
            }
        }
    }

    uiState.pendingDelete?.let { role ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(text = stringResource(R.string.delete_permanently))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
            title = { Text(text = stringResource(R.string.delete_permanently)) },
            text = { Text(text = stringResource(R.string.delete_permanently_confirm, role.name)) }
        )
    }
}
