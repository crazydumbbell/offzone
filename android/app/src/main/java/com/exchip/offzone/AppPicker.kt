package com.exchip.offzone

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

    @Composable
    fun AppPicker(selected: Set<String>, onClose: () -> Unit, onSave: (Set<String>) -> Unit) {
        val context = androidx.compose.ui.platform.LocalContext.current
        var apps by remember { mutableStateOf<List<SelectableApp>?>(null) }
        var failed by remember { mutableStateOf(false) }
        var draft by rememberSaveable(stateSaver = listSaver<Set<String>, String>(save = { it.toList() }, restore = { it.toSet() })) { mutableStateOf(selected) }
        var query by rememberSaveable { mutableStateOf("") }
        LaunchedEffect(Unit) {
            try {
                apps = withContext(Dispatchers.IO) { AppSelection.load(context) }
                draft = draft.intersect(apps!!.map { it.packageName }.toSet())
            } catch (_: Exception) { failed = true }
        }
        Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.safeDrawingPadding().imePadding().padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.apps), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onSave(draft) }, enabled = apps != null) { Text(stringResource(R.string.done)) }
                    }
                    Text(stringResource(R.string.protected_hint), style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(query, onValueChange = { query = it }, singleLine = true,
                        label = { Text(stringResource(R.string.search_apps)) }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                    if (failed) Text(stringResource(R.string.load_error))
                    else if (apps == null) Text(stringResource(R.string.loading_apps))
                    else if (apps!!.isEmpty()) Text(stringResource(R.string.empty_apps))
                    LazyColumn(Modifier.weight(1f)) {
                        items(apps.orEmpty().filter { it.label.contains(query, ignoreCase = true) }, key = { it.packageName }) { app ->
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(
                                value = app.packageName in draft, role = Role.Checkbox,
                                onValueChange = { checked -> draft = if (checked) draft + app.packageName else draft - app.packageName },
                            ), verticalAlignment = Alignment.CenterVertically) {
                                Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                Checkbox(checked = app.packageName in draft, onCheckedChange = null)
                            }
                        }
                    }
                    TextButton(onClick = onClose) { Text(stringResource(R.string.not_now)) }
                }
            }
        }
    }
