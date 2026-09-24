package com.exchip.offzone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.net.Uri
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PlacePickerScreen(initialLat: Double?, initialLon: Double?, initialLabel: String, onBack: () -> Unit, onPick: (Double, Double, String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lat by rememberSaveable { mutableStateOf(initialLat) }
    var lon by rememberSaveable { mutableStateOf(initialLon) }
    var label by rememberSaveable { mutableStateOf(initialLabel) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Address>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var mapAllowed by rememberSaveable { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var cancelFix by remember { mutableStateOf<(() -> Unit)?>(null) }
    var latitudeText by rememberSaveable { mutableStateOf(initialLat?.toString() ?: "") }
    var longitudeText by rememberSaveable { mutableStateOf(initialLon?.toString() ?: "") }
    fun select(a: Double, b: Double, title: String) {
        if (!a.isFinite() || !b.isFinite() || a !in -90.0..90.0 || b !in -180.0..180.0) return
        lat = a; lon = b; label = title; latitudeText = a.toString(); longitudeText = b.toString()
        web?.evaluateJavascript("center($a,$b)", null)
    }
    fun locate() {
        cancelFix?.invoke(); busy = true; error = false
        cancelFix = PlaceMonitor.requestFix(context, { location ->
            select(location.latitude, location.longitude, ""); busy = false
        }, { busy = false; error = true })
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) locate() else error = true
    }
    DisposableEffect(Unit) { onDispose { cancelFix?.invoke(); web?.removeJavascriptInterface("OffzoneMap"); web?.destroy() } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.m_back)) }
            TextButton(onClick = { onPick(lat!!, lon!!, label) }, enabled = lat != null && lon != null) { Text(stringResource(R.string.done)) }
        }
        Text(stringResource(R.string.m_choose_place), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.m_search_place)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                busy = true; error = false
                scope.launch {
                    try {
                        @Suppress("DEPRECATION")
                        val found = withContext(Dispatchers.IO) { Geocoder(context, Locale.getDefault()).getFromLocationName(query.trim(), 5).orEmpty() }
                        results = found; error = found.isEmpty()
                    } catch (_: Exception) { error = true } finally { busy = false }
                }
            }, enabled = query.isNotBlank() && !busy) { Text(stringResource(R.string.m_search)) }
            OutlinedButton(onClick = {
                if (PlaceMonitor.permissionReady(context)) locate()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, enabled = !busy) { Text(stringResource(R.string.m_current_place)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (error) Text(stringResource(R.string.m_place_error), style = MaterialTheme.typography.bodySmall)
        if (results.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 150.dp)) {
            items(results) { address -> TextButton(onClick = { select(address.latitude, address.longitude, address.getAddressLine(0).orEmpty()); results = emptyList() }) { Text(address.getAddressLine(0).orEmpty()) } }
        }
        if (!mapAllowed) {
            Text(stringResource(R.string.m_map_privacy), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { mapAllowed = true }) { Text(stringResource(R.string.m_show_map)) }
        } else AndroidView(modifier = Modifier.fillMaxWidth().height(260.dp), factory = {
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
                web = this
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false; settings.allowContentAccess = false
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = "OffzoneAndroid/0.1 (+https://offzone-privacy-support.wogus5357.chatgpt.site/privacy) " + settings.userAgentString
                addJavascriptInterface(object {
                    @JavascriptInterface fun select(a: Double, b: Double) {
                        if (a.isFinite() && b.isFinite() && a in -90.0..90.0 && b in -180.0..180.0) post {
                            if (lat == null || lon == null || kotlin.math.abs(lat!! - a) > 0.000001 || kotlin.math.abs(lon!! - b) > 0.000001) label = ""
                            lat = a; lon = b; latitudeText = a.toString(); longitudeText = b.toString()
                        }
                    }
                }, "OffzoneMap")
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                        val uri = request.url
                        if (uri.host == "appassets.androidplatform.net") {
                            val file = uri.lastPathSegment
                            if (file !in listOf("index.html", "leaflet.js", "leaflet.css")) return WebResourceResponse("text/plain", "UTF-8", null)
                            return WebResourceResponse(if (file!!.endsWith("js")) "application/javascript" else if (file.endsWith("css")) "text/css" else "text/html", "UTF-8", context.assets.open("map/$file"))
                        }
                        if (uri.scheme != "https" || uri.host != "tile.openstreetmap.org") return WebResourceResponse("text/plain", "UTF-8", null)
                        return null
                    }
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                        if (request.url.host == "appassets.androidplatform.net") return false
                        if (request.url.toString() == "https://www.openstreetmap.org/copyright") runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                    override fun onPageFinished(view: WebView, url: String) { if (lat != null && lon != null) view.evaluateJavascript("center($lat,$lon)", null) }
                }
                loadUrl("https://appassets.androidplatform.net/map/index.html?lat=${lat ?: 20.0}&lon=${lon ?: 0.0}&zoom=${if (lat != null && lon != null) 16 else 2}")
            }
        })
        if (!mapAllowed) Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(latitudeText, { value -> latitudeText = value; label = ""; lat = value.toDoubleOrNull()?.takeIf { it.isFinite() && it in -90.0..90.0 } }, label = { Text(stringResource(R.string.m_latitude)) }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(longitudeText, { value -> longitudeText = value; label = ""; lon = value.toDoubleOrNull()?.takeIf { it.isFinite() && it in -180.0..180.0 } }, label = { Text(stringResource(R.string.m_longitude)) }, singleLine = true, modifier = Modifier.weight(1f))
        }
        Text(stringResource(R.string.m_place_note), style = MaterialTheme.typography.bodySmall)
    }
}
