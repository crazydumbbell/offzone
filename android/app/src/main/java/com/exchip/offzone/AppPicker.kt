package com.exchip.offzone

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun Drawable.toComposeBitmap(sizePx: Int): ImageBitmap {
    val safeSize = sizePx.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, safeSize, safeSize)
    draw(canvas)
    return bitmap.asImageBitmap()
}

@Composable
fun AppPicker(selected: Set<String>, onClose: () -> Unit, onSave: (Set<String>) -> Unit) {
    val context = LocalContext.current
    val iconSizePx = with(LocalDensity.current) { 40.dp.roundToPx() }
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
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // Edge-to-edge picker: the Butter surface also covers the system bars, so no dim band shows above and below it.
        (LocalView.current.parent as? DialogWindowProvider)?.window?.let { window ->
            SideEffect {
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = true
                    isAppearanceLightNavigationBars = true
                }
            }
        }
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text(stringResource(R.string.editor_cancel)) }
                    Text(stringResource(R.string.apps), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    TextButton(onClick = { onSave(draft) }, enabled = apps != null) { Text(stringResource(R.string.done)) }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Surface(color = Mint, shape = CircleShape) {
                        Text(
                            stringResource(R.string.apps_selected, draft.size),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Ink,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
                Text(stringResource(R.string.protected_hint), style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(top = 8.dp))
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    label = { Text(stringResource(R.string.search_apps)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                )
                if (failed) Text(stringResource(R.string.load_error), color = MaterialTheme.colorScheme.error)
                else if (apps == null) Text(stringResource(R.string.loading_apps))
                else if (apps!!.isEmpty()) Text(stringResource(R.string.empty_apps))
                LazyColumn(Modifier.weight(1f)) {
                    items(apps.orEmpty().filter { it.label.contains(query, ignoreCase = true) }, key = { it.packageName }) { app ->
                        val iconBitmap = remember(app.packageName, app.icon) { app.icon?.toComposeBitmap(iconSizePx) }
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 64.dp).toggleable(
                                value = app.packageName in draft,
                                role = Role.Checkbox,
                                onValueChange = { checked -> draft = if (checked) draft + app.packageName else draft - app.packageName },
                            ).padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (iconBitmap != null) {
                                Image(iconBitmap, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                            } else {
                                Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Mint))
                            }
                            Text(app.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            Checkbox(checked = app.packageName in draft, onCheckedChange = null)
                        }
                    }
                }
            }
        }
    }
}
