package dev.chungjungsoo.gptmobile.presentation.ui.mask

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
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
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.chungjungsoo.gptmobile.R
import dev.chungjungsoo.gptmobile.data.model.ApiType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiMaskListScreen(
    onBackAction: () -> Unit,
    onMaskUse: (enabledPlatforms: List<ApiType>, maskId: Int) -> Unit,
    viewModel: AiMaskListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val masks = viewModel.filteredMasks()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.ai_mask_list)) },
                navigationIcon = {
                    TextButton(onClick = onBackAction) {
                        Text(text = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::openCreate) {
                        Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.add_ai_mask))
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
                label = { Text(text = stringResource(R.string.search_ai_mask)) }
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (masks.isEmpty()) {
                    item {
                        Text(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            text = stringResource(R.string.no_ai_masks),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(masks, key = { it.id }) { mask ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val enabledApiTypes = uiState.platformState
                                    .filter { it.enabled }
                                    .map { it.name }
                                if (enabledApiTypes.isEmpty()) {
                                    Toast
                                        .makeText(
                                            context,
                                            context.getString(R.string.enable_at_leat_one_platform),
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()
                                    return@clickable
                                }
                                viewModel.touchMask(mask.id)
                                onMaskUse(enabledApiTypes, mask.id)
                            }
                            .padding(horizontal = 8.dp),
                        headlineContent = {
                            Text(text = mask.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        supportingContent = {
                            Text(
                                text = mask.systemPrompt,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { viewModel.openEdit(mask) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Edit,
                                        contentDescription = stringResource(R.string.edit_ai_mask)
                                    )
                                }
                                IconButton(onClick = { viewModel.requestDelete(mask) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = stringResource(R.string.delete_ai_mask)
                                    )
                                }
                            }
                        }
                    )

                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
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
                        stringResource(R.string.create_ai_mask)
                    } else {
                        stringResource(R.string.edit_ai_mask)
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
                        label = { Text(text = stringResource(R.string.ai_mask_name)) }
                    )
                    OutlinedTextField(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        value = uiState.editorSystemPrompt,
                        onValueChange = viewModel::updateEditorSystemPrompt,
                        label = { Text(text = stringResource(R.string.ai_mask_system_prompt)) }
                    )
                }
            }
        )
    }

    uiState.pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = viewModel::cancelDelete,
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(text = stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDelete) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
            title = { Text(text = stringResource(R.string.delete_ai_mask)) },
            text = { Text(text = stringResource(R.string.delete_ai_mask_confirm, target.name)) }
        )
    }
}
