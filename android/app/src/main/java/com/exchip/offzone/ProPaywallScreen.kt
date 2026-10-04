package com.exchip.offzone

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// D-49 motion language. Entrances decelerate, state changes use the standard curve, taps spring.
private val Decelerate = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val Standard = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
internal val LocalMotion = staticCompositionLocalOf { true }

private enum class Step { INVITE, REMIND, HOW, PLANS, DONE }

@Composable
fun ProPaywallScreen(
    store: AccountStore, onContinue: () -> Unit, onOpenAccount: (resumePlanId: String?, remind: Boolean) -> Unit,
    resumePlanId: String? = null, resumeRemind: Boolean = false, preview: String? = null,
) {
    val account by store.state.collectAsState()
    val context = LocalContext.current
    val sample = remember(preview) { ProPreview.plans(preview) }
    val plans = remember(account.packages, sample) { ProPlans.ordered(sample ?: account.packages.map(ProPlans::of)) }
    val startedPro = remember { store.hasProAccess }
    // A user with animations switched off in system settings gets still screens (Reduce Motion).
    val motion = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    // True once the first offerings request has finished, so a failed or empty answer falls through to the plain screen.
    var loadedOnce by remember { mutableStateOf(false) }
    // D-50: prices are public, so the funnel reads them signed out too. Sign-in is asked for only when the purchase button is tapped.
    LaunchedEffect(store.offersEnabled) { if (preview == null && store.offersEnabled) { store.loadOfferings().join(); loadedOnce = true } }
    LaunchedEffect(Unit) { delay(6000); loadedOnce = true } // never spin forever
    CompositionLocalProvider(LocalMotion provides motion) {
        when {
            plans.isNotEmpty() && !startedPro && (sample != null || store.offersEnabled) ->
                ProFunnel(store, account, plans, signedIn = preview != "signedout" && (preview != null || account.verified), preview = preview != null,
                    resumePlanId = resumePlanId, resumeRemind = resumeRemind, onContinue = onContinue, onOpenAccount = onOpenAccount)
            store.offersEnabled && !startedPro && !loadedOnce && !account.pro -> ProLoading(onContinue)
            else -> ProInfoScreen(store, account, onContinue) { onOpenAccount(null, false) }
        }
    }
}

@Composable
private fun ProFunnel(
    store: AccountStore, account: AccountState, plans: List<ProPlan>, signedIn: Boolean, preview: Boolean,
    resumePlanId: String?, resumeRemind: Boolean, onContinue: () -> Unit, onOpenAccount: (String?, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val default = ProPlans.defaultPlan(plans)!!
    val steps = remember(default.trialDays) {
        if (default.trialDays != null) listOf(Step.INVITE, Step.REMIND, Step.HOW, Step.PLANS) else listOf(Step.INVITE, Step.PLANS)
    }
    // Coming back from the sign-in screen that the purchase button opened (D-50): land on the plans step with the same choices.
    val resumed = plans.any { it.id == resumePlanId }
    var index by rememberSaveable { mutableIntStateOf(if (resumed) steps.lastIndex else 0) }
    var remind by rememberSaveable { mutableStateOf(resumed && resumeRemind) }
    var selectedId by rememberSaveable { mutableStateOf(if (resumed) resumePlanId!! else default.id) }
    var purchasing by rememberSaveable { mutableStateOf(false) }
    var previewDone by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(true) }
    val selected = plans.firstOrNull { it.id == selectedId } ?: default
    val done = account.pro || previewDone
    val step = if (done) Step.DONE else steps[index.coerceIn(0, steps.lastIndex)]
    fun go(to: Int) { forward = to > index; index = to.coerceIn(0, steps.lastIndex) }
    BackHandler(index > 0 && !done) { go(index - 1) }
    LaunchedEffect(account.busy) { if (!account.busy && !account.pro) purchasing = false }
    LaunchedEffect(done) {
        // Remind 24 h before the trial end the store confirmed; fall back to the advertised trial length when it is not known (preview).
        if (done && purchasing && remind) selected.trialDays?.let { days ->
            val end = account.proExpiresAt
            if (end != null) TrialReminder.scheduleAt(context, end - 86_400_000L) else TrialReminder.schedule(context, days)
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        remind = granted; go(index + 1)
    }
    val goal = remember { OnboardingProfile.goal(context) }
    val motion = LocalMotion.current

    Column(Modifier.fillMaxSize().background(Butter).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (steps.size > 1 && step != Step.DONE) StepDots(steps.size, index, Modifier.weight(1f)) else Spacer(Modifier.weight(1f))
            TextButton(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pro_close)) }
        }
        if (preview) Text(stringResource(R.string.pro_preview_note), style = MaterialTheme.typography.bodySmall, color = InkMuted,
            modifier = Modifier.padding(horizontal = 24.dp))
        AnimatedContent(
            targetState = step, modifier = Modifier.weight(1f), label = "pro-step",
            transitionSpec = {
                val shift = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                if (motion)
                    (fadeIn(tween(320, easing = Decelerate)) + slideInHorizontally(tween(320, easing = Decelerate)) { 24 * shift * 3 }) togetherWith
                        (fadeOut(tween(160, easing = Standard)) + slideOutHorizontally(tween(160, easing = Standard)) { -16 * shift * 3 })
                else fadeIn(tween(100)) togetherWith fadeOut(tween(100))
            },
        ) { current ->
            when (current) {
                Step.INVITE -> {
                    val trial = default.trialDays
                    StepScaffold(
                        center = true,
                        bottom = {
                            if (trial != null) Text(stringResource(R.string.pro_no_payment), style = MaterialTheme.typography.bodyMedium, color = InkMuted,
                                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().reveal(rememberReveal(560)))
                            PrimaryButton(onClick = { go(index + 1) }, modifier = Modifier.fillMaxWidth().reveal(rememberReveal(620))) {
                                Text(stringResource(if (trial != null) R.string.pro_start_trial else R.string.pro_see_plans))
                            }
                            TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).reveal(rememberReveal(680))) {
                                Text(stringResource(R.string.pro_continue_free))
                            }
                        },
                    ) {
                        InviteHero(compact = trial == null)
                        Text(
                            if (trial != null) stringResource(R.string.pro_try_title, pluralStringResource(R.plurals.pro_days, trial, trial)) else stringResource(goalHeadline(goal)),
                            style = headline(), modifier = Modifier.reveal(rememberReveal(300)),
                        )
                        if (trial == null) BenefitList(Modifier.reveal(rememberReveal(380)))
                    }
                }
                Step.REMIND -> StepScaffold(
                    center = true,
                    bottom = {
                        PrimaryButton(onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else { remind = true; go(index + 1) }
                        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pro_remind_cta)) }
                        TextButton(onClick = { remind = false; go(index + 1) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text(stringResource(R.string.pro_skip))
                        }
                    },
                ) {
                    RemindHero()
                    Text(stringResource(R.string.pro_remind_title), style = headline(), modifier = Modifier.reveal(rememberReveal(300)))
                    Text(stringResource(R.string.pro_remind_body), style = MaterialTheme.typography.bodyLarge, color = InkMuted, modifier = Modifier.reveal(rememberReveal(370)))
                }
                Step.HOW -> StepScaffold(
                    bottom = {
                        PrimaryButton(onClick = { go(index + 1) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pro_continue)) }
                    },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.pro_how_title), style = headline(), modifier = Modifier.weight(1f).reveal(rememberReveal(0)))
                        Image(painterResource(R.drawable.pro_nook_tea), null, Modifier.height(96.dp).reveal(rememberReveal(120)), contentScale = ContentScale.Fit)
                    }
                    TrialTimeline(ProPlans.timelineDays(default.trialDays ?: 1), remind)
                }
                Step.PLANS -> PlansStep(
                    plans = plans, selected = selected, onSelect = { selectedId = it.id }, account = account, signedIn = signedIn,
                    onPurchase = {
                        when {
                            !signedIn -> onOpenAccount(selected.id, remind)
                            preview -> { purchasing = true; previewDone = true }
                            else -> selected.pkg?.let { pkg -> activity?.let { purchasing = true; store.purchase(it, pkg) } }
                        }
                    },
                    onRestore = { if (signedIn && account.purchasesConfigured) store.restorePurchases() else onOpenAccount(selected.id, remind) },
                )
                Step.DONE -> StepScaffold(
                    center = true,
                    bottom = {
                        PrimaryButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pro_continue)) }
                    },
                ) {
                    Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                        val enter = rememberReveal(0, 500)
                        Box(Modifier.size(224.dp).graphicsLayer { alpha = enter.value }.background(Mint, CircleShape))
                        Image(painterResource(NookExpression.READY_FULL.asset), null, Modifier.height(200.dp).graphicsLayer {
                            val s = 0.92f + 0.08f * enter.value; scaleX = s; scaleY = s; alpha = enter.value
                        })
                        ConfettiBurst(trigger = 1, Modifier.fillMaxSize(), originX = 0.5f, originY = 0.5f)
                    }
                    Text(stringResource(R.string.pro_done_title), style = headline(), modifier = Modifier.reveal(rememberReveal(300)))
                    Text(stringResource(if (purchasing && selected.trialDays != null) R.string.pro_done_trial else R.string.pro_done_body),
                        style = MaterialTheme.typography.bodyLarge, color = InkMuted, modifier = Modifier.reveal(rememberReveal(370)))
                }
            }
        }
    }
}

@Composable
private fun StepScaffold(bottom: @Composable ColumnScope.() -> Unit, center: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f)) {
            val viewport = maxHeight
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                Column(Modifier.heightIn(min = viewport), verticalArrangement = Arrangement.spacedBy(16.dp, if (center) Alignment.CenterVertically else Alignment.Top), content = content)
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp), content = bottom)
    }
}

@Composable
private fun StepDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.pro_step_of, current + 1, count)
    Row(modifier.semantics(mergeDescendants = true) { contentDescription = label }, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp, tween(220, easing = Standard), label = "dot")
            Box(Modifier.height(8.dp).width(width).background(if (i == current) Pine else PineLine, CircleShape))
        }
    }
}

/** Fade-up entrance that starts after [delayMs]. Resting state straight away when animations are off. */
@Composable
private fun rememberReveal(delayMs: Int, durationMs: Int = 360): Animatable<Float, AnimationVector1D> {
    val motion = LocalMotion.current
    val progress = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) { if (motion) { delay(delayMs.toLong()); progress.animateTo(1f, tween(durationMs, easing = Decelerate)) } }
    return progress
}

private fun Modifier.reveal(progress: Animatable<Float, AnimationVector1D>, rise: Dp = 16.dp) = graphicsLayer {
    alpha = progress.value; translationY = (1f - progress.value) * rise.toPx()
}

@Composable
private fun BenefitList(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(WarmIvory, RoundedCornerShape(24.dp)).border(1.dp, PineHairline, RoundedCornerShape(24.dp))
        .padding(horizontal = 20.dp, vertical = 8.dp)) {
        listOf(R.string.pro_benefit_plan, R.string.pro_benefit_journal, R.string.pro_benefit_review).forEachIndexed { index, label ->
            if (index > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("✓", color = Pine, modifier = Modifier.padding(end = 14.dp))
                Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** Balanced, phrase-aware wrapping so a Korean headline breaks between words instead of inside one. */
@Composable
private fun headline() = MaterialTheme.typography.headlineMedium.copy(lineBreak = LineBreak.Heading)

private fun goalHeadline(goal: String) = when (goal) {
    "rest" -> R.string.pro_headline_rest; "presence" -> R.string.pro_headline_presence
    "personal" -> R.string.pro_headline_personal; else -> R.string.pro_headline_work
}

private class TileSpec(@DrawableRes val res: Int, val dx: Int, val dy: Int, val rotation: Float, val size: Int, val phase: Float)

private val inviteTiles = listOf(
    TileSpec(R.drawable.pro_tile_1, -112, -64, -10f, 68, 0f), TileSpec(R.drawable.pro_tile_2, 112, -88, 8f, 60, .25f),
    TileSpec(R.drawable.pro_tile_3, -104, 56, 6f, 64, .5f), TileSpec(R.drawable.pro_tile_4, 116, 72, -7f, 72, .75f),
)

/** The "make room" motion: app tiles slide out from behind Nook and leave the middle free. */
@Composable
private fun InviteHero(compact: Boolean = false) {
    val motion = LocalMotion.current
    val k = (LocalConfiguration.current.screenHeightDp * 0.34f).coerceIn(250f, 330f) / 270f * if (compact) 0.8f else 1f
    val room = rememberReveal(0, 600)
    val nook = rememberReveal(100, 500)
    val float: State<Float>? = if (motion) rememberInfiniteTransition(label = "nook").animateFloat(
        -1f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "float") else null
    Box(Modifier.fillMaxWidth().height((270 * k).dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size((224 * k).dp).graphicsLayer { val s = 0.6f + 0.4f * room.value; scaleX = s; scaleY = s; alpha = room.value }.background(Mint, CircleShape))
        inviteTiles.forEachIndexed { i, tile -> FloatingTile(tile, 120 + 80 * i, k) }
        Image(painterResource(NookExpression.WELCOME_FULL.asset), stringResource(R.string.nook_description), Modifier.height((190 * k).dp).graphicsLayer {
            val s = 0.92f + 0.08f * nook.value; scaleX = s; scaleY = s; alpha = nook.value
            translationY = (float?.value ?: 0f) * 4.dp.toPx()
        })
    }
}

@Composable
private fun FloatingTile(tile: TileSpec, delayMs: Int, k: Float) {
    val enter = rememberReveal(delayMs, 700)
    val drift: State<Float>? = if (LocalMotion.current) rememberInfiniteTransition(label = "tile").animateFloat(
        0f, 1f, infiniteRepeatable(tween(4400, easing = LinearEasing)), label = "drift") else null
    Image(painterResource(tile.res), null, Modifier.size((tile.size * k).dp).graphicsLayer {
        val p = enter.value
        translationX = (tile.dx * k).dp.toPx() * p
        translationY = (tile.dy * k).dp.toPx() * p + sin(((drift?.value ?: 0f) + tile.phase) * 2f * PI.toFloat()) * 3.dp.toPx() * p
        rotationZ = tile.rotation * p
        val s = 0.55f + 0.45f * p; scaleX = s; scaleY = s; alpha = p
    })
}

/** A mock notification drops in over Nook holding a clock, which wiggles and gets a badge. */
@Composable
private fun RemindHero() {
    val motion = LocalMotion.current
    val drop = remember { Animatable(if (motion) 0f else 1f) }
    val pop = remember { Animatable(if (motion) 0f else 1f) }
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (motion) {
            launch { delay(200); drop.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 300f)) }
            launch { delay(800); pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 420f)) }
            delay(1200)
            while (true) { wiggle.animateTo(1f, tween(700, easing = LinearEasing)); wiggle.snapTo(0f); delay(2700) }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier.fillMaxWidth().graphicsLayer {
                translationY = -72.dp.toPx() * (1f - drop.value); alpha = drop.value.coerceIn(0f, 1f)
            },
            shape = RoundedCornerShape(20.dp), color = WarmIvory, shadowElevation = 6.dp, border = androidx.compose.foundation.BorderStroke(1.dp, PineHairline),
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Image(painterResource(R.drawable.offzone_icon), null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.pro_banner_title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.pro_banner_body), style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            }
        }
        Box {
            Image(painterResource(R.drawable.pro_nook_clock), null, Modifier.height(190.dp).graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 1f)
                val t = wiggle.value; rotationZ = sin(t * 6f * PI.toFloat()) * 4f * (1f - t)
            })
            Box(Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 14.dp).size(30.dp).graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .background(Pine, CircleShape), contentAlignment = Alignment.Center) {
                Text("1", color = Butter, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Today / reminder / trial end. One progress line fills over 2.4 s and lights each node as it arrives. */
@Composable
private fun TrialTimeline(days: List<Int>, remind: Boolean) {
    val motion = LocalMotion.current
    val fill = remember { Animatable(if (motion) 0f else 1.08f) } // 1.08: the last node lights once the line has arrived
    val card = rememberReveal(0, 360)
    LaunchedEffect(Unit) { if (motion) { delay(300); fill.animateTo(1.08f, tween(2600, easing = LinearEasing)) } }
    val icons = listOf(Icons.Filled.Star, Icons.Filled.Notifications, Icons.Filled.DateRange)
    val last = days.lastIndex
    Column(Modifier.fillMaxWidth().reveal(card, 24.dp).background(WarmIvory, RoundedCornerShape(24.dp))
        .border(1.dp, PineHairline, RoundedCornerShape(24.dp)).padding(horizontal = 20.dp, vertical = 20.dp)) {
        days.forEachIndexed { i, day ->
            val litAt = i.toFloat() / last
            val row = rememberReveal(200 + 90 * i)
            val icon: ImageVector = if (i == 0) icons[0] else if (i == last) icons[2] else icons[1]
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).reveal(row)) {
                Column(Modifier.width(36.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimelineNode(icon, fill, litAt)
                    if (i < last) Box(Modifier.width(4.dp).weight(1f).padding(vertical = 4.dp).drawBehind {
                        val span = 1f / last
                        val part = ((fill.value - litAt) / span).coerceIn(0f, 1f)
                        drawRoundRect(PineHairline, cornerRadius = CornerRadius(2.dp.toPx()))
                        drawRoundRect(Pine, size = Size(size.width, size.height * part), cornerRadius = CornerRadius(2.dp.toPx()))
                    })
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.padding(bottom = if (i < last) 22.dp else 0.dp).graphicsLayer {
                    alpha = 0.55f + 0.45f * ((fill.value - litAt) / 0.08f).coerceIn(0f, 1f)
                }) {
                    Text(if (i == 0) stringResource(R.string.pro_how_today) else pluralStringResource(R.plurals.pro_in_days, day, day),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(when {
                        i == 0 -> R.string.pro_how_today_body
                        i == last -> R.string.pro_how_end_body
                        remind -> R.string.pro_how_remind_body
                        else -> R.string.pro_how_free_body
                    }), style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                }
            }
        }
    }
}

@Composable
private fun TimelineNode(icon: ImageVector, fill: Animatable<Float, AnimationVector1D>, litAt: Float) {
    Box(Modifier.size(36.dp).graphicsLayer {
        val lit = ((fill.value - litAt) / 0.08f).coerceIn(0f, 1f)
        val s = 1f + 0.15f * sin(PI.toFloat() * lit); scaleX = s; scaleY = s
    }.drawBehind {
        val lit = ((fill.value - litAt) / 0.08f).coerceIn(0f, 1f)
        drawCircle(androidx.compose.ui.graphics.lerp(PineHairline, Pine, lit))
    }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Butter, modifier = Modifier.size(20.dp).graphicsLayer { alpha = ((fill.value - litAt) / 0.08f).coerceIn(0f, 1f) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlansStep(
    plans: List<ProPlan>, selected: ProPlan, onSelect: (ProPlan) -> Unit, account: AccountState, signedIn: Boolean,
    onPurchase: () -> Unit, onRestore: () -> Unit,
) {
    val context = LocalContext.current
    val motion = LocalMotion.current
    val yearly = plans.firstOrNull { it.annual }
    val monthly = plans.firstOrNull { !it.annual }
    val save = ProPlans.savingsPercent(yearly, monthly)
    var burst by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { if (motion) { delay(700); burst++ } }
    val shimmer = remember { Animatable(0f) }
    LaunchedEffect(account.busy) {
        if (motion && !account.busy) { delay(2000); while (true) { shimmer.snapTo(0f); shimmer.animateTo(1f, tween(600, easing = Standard)); delay(5000) } }
    }
    val trial = selected.trialDays
    val zero = remember(selected) { ProPlans.money(0, selected.currency) ?: "0" }
    val cadence = stringResource(if (selected.annual) R.string.pro_per_year else R.string.pro_per_month, selected.price)
    StepScaffold(
        bottom = {
            if (trial != null) Text(stringResource(R.string.pro_due_today, zero), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().reveal(rememberReveal(520)))
            PrimaryButton(onClick = onPurchase, enabled = !account.busy, modifier = Modifier.fillMaxWidth().reveal(rememberReveal(560)).shimmer(shimmer)) {
                if (account.busy) CircularProgressIndicator(Modifier.size(24.dp), color = Butter, strokeWidth = 2.dp)
                else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(if (trial != null) R.string.pro_start_trial else R.string.account_subscribe))
                    AnimatedContent(
                        targetState = stringResource(if (trial != null) R.string.pro_cta_trial_sub else R.string.pro_cta_sub, cadence), label = "cta-sub",
                        transitionSpec = {
                            if (motion) (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 2 }) togetherWith (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 2 })
                            else fadeIn(tween(100)) togetherWith fadeOut(tween(100))
                        },
                    ) { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
    ) {
        val peek = rememberReveal(0, 420)
        Box(Modifier.fillMaxWidth().height(88.dp).graphicsLayer { translationY = (1f - peek.value) * 24.dp.toPx(); alpha = peek.value }, contentAlignment = Alignment.BottomCenter) {
            Image(painterResource(R.drawable.pro_nook_peek), null, Modifier.height(112.dp).offset(y = 24.dp), contentScale = ContentScale.Fit)
        }
        Text(stringResource(R.string.pro_plans_title), style = headline(), modifier = Modifier.reveal(rememberReveal(120)))
        Text(stringResource(R.string.pro_plans_sub), style = MaterialTheme.typography.bodyLarge, color = InkMuted, modifier = Modifier.reveal(rememberReveal(180)))
        plans.forEachIndexed { i, plan ->
            val badges = buildList {
                if (plan.annual && save != null) { add(stringResource(R.string.pro_best_value)); add(stringResource(R.string.pro_save, save)) }
            }
            PlanCard(
                selected = plan.id == selected.id, badges = badges, burst = if (plan.annual) burst else 0,
                title = stringResource(if (plan.annual) R.string.pro_yearly else R.string.pro_monthly),
                detail = if (plan.trialDays != null) stringResource(R.string.pro_free_then, pluralStringResource(R.plurals.pro_days, plan.trialDays, plan.trialDays),
                    stringResource(if (plan.annual) R.string.pro_per_year else R.string.pro_per_month, plan.price))
                else stringResource(if (plan.annual) R.string.pro_per_year else R.string.pro_per_month, plan.price),
                extra = if (plan.annual) ProPlans.perMonth(plan)?.let { stringResource(R.string.pro_per_month, it) } else null,
                modifier = Modifier.reveal(rememberReveal(240 + 90 * i), 24.dp),
                onClick = { onSelect(plan); if (plan.annual && !plan.id.equals(selected.id)) burst++ },
            )
        }
        if (!signedIn) Text(stringResource(R.string.pro_sign_in_note), style = MaterialTheme.typography.bodySmall, color = InkMuted)
        // D-23: before paying, say where Pro data lives; and that the store, not this screen, confirms eligibility and the final price.
        Text(stringResource(R.string.account_journal_local), style = MaterialTheme.typography.bodySmall, color = InkMuted)
        Text(stringResource(R.string.account_play_confirms), style = MaterialTheme.typography.bodySmall, color = InkMuted)
        account.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        account.notice?.let { Text(it) }
        Text(stringResource(R.string.pro_legal), style = MaterialTheme.typography.bodySmall, color = InkMuted, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.Center) {
            if (AccountStore.https(BuildConfig.TERMS_URL)) TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TERMS_URL))) },
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.account_terms), style = MaterialTheme.typography.bodySmall) }
            if (AccountStore.https(BuildConfig.PRIVACY_URL)) TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))) },
                modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.account_privacy), style = MaterialTheme.typography.bodySmall) }
            TextButton(onClick = onRestore, enabled = !account.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.account_restore), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanCard(
    selected: Boolean, title: String, detail: String, extra: String?, badges: List<String>, burst: Int,
    modifier: Modifier = Modifier, onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    val width by animateDpAsState(if (selected) 2.dp else 1.dp, tween(220, easing = Standard), label = "card-border")
    val line by animateColorAsState(if (selected) Pine else PineLine, tween(220, easing = Standard), label = "card-line")
    val fill by animateColorAsState(if (selected) Mint else WarmIvory, tween(220, easing = Standard), label = "card-fill")
    val scale by animateFloatAsState(if (selected) 1.02f else 1f, spring(dampingRatio = 0.55f, stiffness = 380f), label = "card-scale")
    val check by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 500f), label = "card-check")
    Box(modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale }) {
        Row(Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(shape).background(fill, shape).border(width, line, shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    badges.forEachIndexed { i, label ->
                        Surface(color = if (i == 0) Pine else SoftButter, shape = CircleShape) {
                            Text(label, color = if (i == 0) Butter else Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                        }
                    }
                }
                Text(detail, style = MaterialTheme.typography.bodyMedium)
                extra?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = InkMuted) }
            }
            Box(Modifier.size(24.dp).border(1.5.dp, if (selected) Pine else InkMuted, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(14.dp).graphicsLayer { scaleX = check; scaleY = check }.background(Pine, CircleShape))
            }
        }
        if (burst > 0) ConfettiBurst(trigger = burst, Modifier.matchParentSize(), originX = 0.78f)
    }
}

private class Particle(val angle: Float, val speed: Float, val spin: Float, val kind: Int, val wide: Boolean)

/** 24 small pieces burst up and fall for 700 ms. Draws nothing when animations are off. */
@Composable
private fun ConfettiBurst(trigger: Int, modifier: Modifier = Modifier, originX: Float, originY: Float = 0f) {
    if (!LocalMotion.current) return
    val progress = remember { Animatable(1f) }
    val pieces = remember(trigger) { List(24) { Particle(Random.nextFloat(), Random.nextFloat(), Random.nextFloat(), Random.nextInt(4), Random.nextBoolean()) } }
    LaunchedEffect(trigger) { if (trigger > 0) { progress.snapTo(0f); progress.animateTo(1f, tween(700, easing = LinearEasing)) } }
    val palette = remember { listOf(Pine, SoftButter, Mint, Pine.copy(alpha = 0.6f)) }
    Canvas(modifier) {
        val t = progress.value
        if (t >= 1f) return@Canvas
        val fade = 1f - ((t - 0.7f) / 0.3f).coerceIn(0f, 1f)
        pieces.forEach { p ->
            val radians = (-160f + 140f * p.angle) * (PI.toFloat() / 180f)
            val reach = (90f + 60f * p.speed).dp.toPx() * t
            val x = size.width * originX + cos(radians) * reach
            val y = size.height * originY + sin(radians) * reach + 140.dp.toPx() * t * t
            val w = (if (p.wide) 8f else 5f).dp.toPx()
            val h = (if (p.wide) 4f else 5f).dp.toPx()
            rotate(360f * p.spin * t, Offset(x, y)) {
                drawRoundRect(palette[p.kind].copy(alpha = fade * palette[p.kind].alpha), Offset(x - w / 2, y - h / 2), Size(w, h), CornerRadius(h / 2))
            }
        }
    }
}

/** A soft diagonal light sweeps once across the button. */
private fun Modifier.shimmer(progress: Animatable<Float, AnimationVector1D>) = drawWithContent {
    drawContent()
    val t = progress.value
    if (t > 0f && t < 1f) {
        val band = size.width * 0.25f
        val x = -band + (size.width + 2 * band) * t
        clipPath(Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(size.height / 2))) }) {
            drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.28f), Color.Transparent),
                Offset(x - band, 0f), Offset(x + band, size.height)))
        }
    }
}

@Composable
private fun ProLoading(onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Butter).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onContinue, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.pro_close)) }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pine) }
        TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).heightIn(min = 48.dp)) {
            Text(stringResource(R.string.pro_continue_free))
        }
    }
}

/** Sales off, or Pro already active: the plain screen from D-45. No price, trial or purchase button. */
@Composable
private fun ProInfoScreen(store: AccountStore, account: AccountState, onContinue: () -> Unit, onOpenAccount: () -> Unit) {
    val context = LocalContext.current
    val heading = when (OnboardingProfile.goal(context)) {
        "rest" -> R.string.pro_headline_rest
        "presence" -> R.string.pro_headline_presence
        "personal" -> R.string.pro_headline_personal
        else -> R.string.pro_headline_work
    }
    Column(Modifier.fillMaxSize().background(Butter).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.pro_eyebrow), style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onContinue) { Text(stringResource(R.string.pro_close)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)) {
            NookCatView(NookExpression.WELCOME_FULL, Modifier.fillMaxWidth().height(184.dp))
            Text(stringResource(if (store.offersEnabled || store.hasProAccess) heading else R.string.audit_pro_unavailable_title), style = MaterialTheme.typography.headlineLarge)
            if (!store.offersEnabled && !store.hasProAccess) Text(stringResource(R.string.audit_pro_unavailable_body), style = MaterialTheme.typography.bodyLarge)
            if (store.offersEnabled || store.hasProAccess) BenefitList()
            Surface(color = Mint, shape = RoundedCornerShape(16.dp)) {
                Text(stringResource(if (store.hasProAccess) R.string.account_pro_active else R.string.account_plans_unavailable),
                    modifier = Modifier.fillMaxWidth().padding(18.dp), style = MaterialTheme.typography.bodyMedium)
            }
            if (store.offersEnabled && !store.hasProAccess) {
                PrimaryButton(onClick = onOpenAccount, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pro_view_plans))
                }
            }
            account.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            account.notice?.let { Text(it) }
            TextButton(onClick = {
                if (account.verified && account.purchasesConfigured) store.restorePurchases() else onOpenAccount()
            }, enabled = !account.busy, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.account_restore))
            }
            if (AccountStore.https(BuildConfig.TERMS_URL)) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TERMS_URL))) },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.account_terms)) }
            }
            if (AccountStore.https(BuildConfig.PRIVACY_URL)) {
                TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))) },
                    modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.account_privacy)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        PrimaryButton(onClick = onContinue, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.pro_continue_free))
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.findActivity(); else -> null }
