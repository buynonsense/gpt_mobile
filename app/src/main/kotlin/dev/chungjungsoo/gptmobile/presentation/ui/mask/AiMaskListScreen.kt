package dev.chungjungsoo.gptmobile.presentation.ui.mask

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMaskListScreen(
    onBackAction: () -> Unit,
    onOpenArchive: () -> Unit,
    viewModel: AiMaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val roles = viewModel.filteredRoles()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.role_management)) },
                navigationIcon = {
                    TextButton(onClick = onBackAction) {
                        Text(text = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenArchive) {
                        Icon(imageVector = Icons.Outlined.Archive, contentDescription = stringResource(R.string.archived_roles))
                    }
                    IconButton(onClick = viewModel::openCreate) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.create_role))
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                singleLine = true,
                label = { Text(text = stringResource(R.string.search_roles)) }
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (roles.isEmpty()) {
                    item {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            text = stringResource(R.string.no_roles),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(roles, key = { it.id }) { role ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.openEdit(role) }
                            .padding(horizontal = 8.dp),
                        headlineContent = {
                            Text(text = role.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = stringResource(R.string.role_group_value, role.groupName),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = role.systemPrompt.ifBlank { stringResource(R.string.default_role_description) },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.openEdit(role) }) {
                                    Icon(imageVector = Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_role))
                                }
                                if (!role.isDefault) {
                                    IconButton(onClick = { viewModel.requestArchive(role) }) {
                                        Icon(imageVector = Icons.Outlined.Archive, contentDescription = stringResource(R.string.archive_role))
                                    }
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (uiState.isEditorOpen) {
        AlertDialog(
            onDismissRequest = viewModel::closeEditor,
            confirmButton = {
                TextButton(onClick = viewModel::saveEditor) {
                    Text(text = stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::closeEditor) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
            title = {
                Text(
                    text = if (uiState.editing == null) {
                        stringResource(R.string.create_role)
                    } else {
                        stringResource(R.string.edit_role)
                    }
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = uiState.editorName,
                        onValueChange = viewModel::updateEditorName,
                        singleLine = true,
                        label = { Text(text = stringResource(R.string.role_name)) }
                    )
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        value = uiState.editorGroupName,
                        onValueChange = viewModel::updateEditorGroupName,
                        singleLine = true,
                        enabled = uiState.editing?.isDefault != true,
                        label = { Text(text = stringResource(R.string.role_group)) }
                    )
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        value = uiState.editorSystemPrompt,
                        onValueChange = viewModel::updateEditorSystemPrompt,
                        label = { Text(text = stringResource(R.string.role_system_prompt)) }
                    )
                }
            }
        )
    }

    uiState.pendingArchive?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelArchive,
            confirmButton = {
                TextButton(onClick = viewModel::confirmArchive) {
                    Text(text = stringResource(R.string.archive_role))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelArchive) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
            title = { Text(text = stringResource(R.string.archive_role)) },
            text = { Text(text = stringResource(R.string.archive_role_confirm, target.name)) }
        )
    }
}
