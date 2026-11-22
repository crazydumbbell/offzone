package com.exchip.offzone

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Address
import android.location.Geocoder
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

class OffzoneMapBridge(private val onSelect: (Double, Double) -> Unit) {
    @JavascriptInterface
    fun select(a: Double, b: Double) {
        onSelect(a, b)
    }
}

private suspend fun reverseGeocode(context: Context, locale: Locale, lat: Double, lon: Double): String =
    withContext(Dispatchers.IO) {
        runCatching {
            @Suppress("DEPRECATION")
            val address = Geocoder(context, locale).getFromLocation(lat, lon, 1)?.firstOrNull()
            address?.getAddressLine(0).orEmpty().trim()
        }.getOrDefault("")
    }

@OptIn(ExperimentalLayoutApi::class)
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun PlacePickerScreen(initialLat: Double?, initialLon: Double?, initialLabel: String, onBack: () -> Unit, onPick: (Double, Double, String) -> Unit) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    var lat by rememberSaveable { mutableStateOf(initialLat) }
    var lon by rememberSaveable { mutableStateOf(initialLon) }
    var label by rememberSaveable { mutableStateOf(initialLabel) }
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Address>()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var web by remember { mutableStateOf<WebView?>(null) }
    var cancelFix by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun select(a: Double, b: Double, title: String) {
        if (!a.isFinite() || !b.isFinite() || a !in -90.0..90.0 || b !in -180.0..180.0) return
        lat = a; lon = b; label = title
        web?.evaluateJavascript("center($a,$b)", null)
    }

    fun runSearch() {
        val term = query.trim()
        if (term.isEmpty() || busy) return
        keyboard?.hide()
        busy = true; error = false
        scope.launch {
            try {
                @Suppress("DEPRECATION")
                val found = withContext(Dispatchers.IO) { Geocoder(context, locale).getFromLocationName(term, 5).orEmpty() }
                results = found; error = found.isEmpty()
            } catch (_: Exception) {
                error = true
            } finally {
                busy = false
            }
        }
    }

    fun locate() {
        cancelFix?.invoke(); busy = true; error = false
        cancelFix = PlaceMonitor.requestFix(context, { location ->
            val pickedLat = location.latitude
            val pickedLon = location.longitude
            select(pickedLat, pickedLon, "")
            scope.launch {
                val resolved = reverseGeocode(context, locale, pickedLat, pickedLon)
                if (resolved.isNotBlank() && lat != null && lon != null &&
                    abs(lat!! - pickedLat) < 0.0001 && abs(lon!! - pickedLon) < 0.0001
                ) {
                    label = resolved
                }
                busy = false
            }
        }, { busy = false; error = true })
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.ACCESS_FINE_LOCATION] == true) locate() else error = true
    }

    DisposableEffect(Unit) { onDispose { cancelFix?.invoke(); web?.removeJavascriptInterface("OffzoneMap"); web?.destroy() } }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.m_back)) }
            TextButton(onClick = { onPick(lat!!, lon!!, label) }, enabled = lat != null && lon != null) { Text(stringResource(R.string.done)) }
        }
        Text(stringResource(R.string.m_choose_place), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.m_search_place)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PrimaryButton(onClick = ::runSearch, enabled = query.isNotBlank() && !busy) { Text(stringResource(R.string.m_search)) }
            SecondaryButton(onClick = {
                if (PlaceMonitor.permissionReady(context)) locate()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }, enabled = !busy) { Text(stringResource(R.string.m_current_place)) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = PineHairline)
        if (error) Text(stringResource(R.string.m_place_error), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        if (results.isNotEmpty()) LazyColumn(Modifier.heightIn(max = 150.dp)) {
            items(results) { address ->
                TextButton(onClick = { select(address.latitude, address.longitude, address.getAddressLine(0).orEmpty()); results = emptyList() }) {
                    Text(address.getAddressLine(0).orEmpty())
                }
            }
        }
        Surface(
            color = WarmIvory,
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, PineHairline),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(20.dp)),
                factory = {
                    WebView(context).apply {
                        layoutParams = android.view.ViewGroup.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        web = this
                        setOnTouchListener { view, event ->
                            when (event.actionMasked) {
                                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> view.parent?.requestDisallowInterceptTouchEvent(true)
                                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                            false
                        }
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false; settings.allowContentAccess = false
                        settings.cacheMode = WebSettings.LOAD_DEFAULT
                        settings.userAgentString = "OffzoneAndroid/0.1 (+https://crazydumbbell.github.io/offzone/privacy/) " + settings.userAgentString
                        addJavascriptInterface(OffzoneMapBridge { a, b ->
                            if (a.isFinite() && b.isFinite() && a in -90.0..90.0 && b in -180.0..180.0) post {
                                if (lat == null || lon == null || abs(lat!! - a) > 0.000001 || abs(lon!! - b) > 0.000001) label = ""
                                lat = a; lon = b
                            }
                        }, "OffzoneMap")
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? {
                                val uri = request.url
                                if (uri.host == "appassets.androidplatform.net") {
                                    val file = uri.lastPathSegment
                                    if (file !in listOf("index.html", "leaflet.js", "leaflet.css")) return WebResourceResponse("text/plain", "UTF-8", null)
                                    return WebResourceResponse(
                                        if (file!!.endsWith("js")) "application/javascript" else if (file.endsWith("css")) "text/css" else "text/html",
                                        "UTF-8",
                                        context.assets.open("map/$file"),
                                    )
                                }
                                if (uri.scheme != "https" || uri.host != "tile.openstreetmap.org") return WebResourceResponse("text/plain", "UTF-8", null)
                                return null
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                                if (request.url.host == "appassets.androidplatform.net") return false
                                if (request.url.toString() == "https://www.openstreetmap.org/copyright") runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                                return true
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                if (lat != null && lon != null) view.evaluateJavascript("center($lat,$lon)", null)
                            }
                        }
                        loadUrl("https://appassets.androidplatform.net/map/index.html?lat=${lat ?: 20.0}&lon=${lon ?: 0.0}&zoom=${if (lat != null && lon != null) 16 else 2}")
                    }
                },
            )
        }
        if (lat != null && lon != null) {
            val summary = label.ifBlank { String.format(locale, "%.5f, %.5f", lat, lon) }
            Surface(color = Mint, shape = RoundedCornerShape(16.dp)) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, color = Ink, modifier = Modifier.fillMaxWidth().padding(14.dp))
            }
        }
        Text(stringResource(R.string.m_place_note), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        Text(stringResource(R.string.m_map_privacy), style = MaterialTheme.typography.bodySmall, color = InkMuted)
    }
}
