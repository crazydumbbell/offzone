package com.exchip.offzone

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object OnboardingProfile {
    private fun prefs(context: Context) = context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
    fun completed(context: Context) = prefs(context).getBoolean("completed", false)
    fun goal(context: Context) = prefs(context).getString("goal", "work")!!.takeIf { it in JournalStore.contexts } ?: "work"
    fun window(context: Context) = prefs(context).getString("window", "morning")!!.takeIf { it in listOf("morning","afternoon","evening") } ?: "morning"
    fun save(context: Context, goal: String, window: String): Boolean {
        require(goal in JournalStore.contexts && window in listOf("morning","afternoon","evening"))
        return prefs(context).edit().putString("goal",goal).putString("window",window).putBoolean("completed",true).commit()
    }
}
internal fun goalLabel(goal: String) = when(goal) { "work" -> R.string.goal_work; "rest" -> R.string.goal_rest; "presence" -> R.string.goal_presence; else -> R.string.goal_personal }

@Composable fun OnboardingScreen(onComplete: (goal: String, window: String) -> Unit) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableIntStateOf(0) }
    var goal by rememberSaveable { mutableStateOf(OnboardingProfile.goal(context)) }
    var window by rememberSaveable { mutableStateOf(OnboardingProfile.window(context)) }
    var failed by remember { mutableStateOf(false) }; var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (step > 0) TextButton(onClick = { step-- }) { Text(stringResource(R.string.journal_back)) }
            RueView(modifier = Modifier.fillMaxWidth().height(170.dp))
            Text(stringResource(when(step) { 0 -> R.string.onboarding_title; 1 -> R.string.onboarding_goal; else -> R.string.onboarding_rhythm }), style = MaterialTheme.typography.headlineLarge)
            if (step == 0) { Text(stringResource(R.string.onboarding_detail)); Text(stringResource(R.string.onboarding_safe)) }
            if (step == 1) JournalStore.contexts.forEach { item ->
                val detail = when(item) { "work" -> R.string.goal_work_detail; "rest" -> R.string.goal_rest_detail; "presence" -> R.string.goal_presence_detail; else -> R.string.goal_personal_detail }
                Row(Modifier.fillMaxWidth().selectable(goal == item, role=Role.RadioButton, onClick = { goal=item; window=if(item=="work") "morning" else "evening" }).padding(vertical=10.dp)) {
                    RadioButton(goal==item, onClick=null)
                    Column(Modifier.padding(start=12.dp)) { Text(stringResource(goalLabel(item)),style=MaterialTheme.typography.titleMedium); Text(stringResource(detail)) }
                }
            }
            if (step == 2) {
                Text(stringResource(R.string.onboarding_suggest))
                listOf("morning" to R.string.window_morning,"afternoon" to R.string.window_afternoon,"evening" to R.string.window_evening).forEach { (key,label) ->
                    Row(Modifier.fillMaxWidth().selectable(window==key,role=Role.RadioButton,onClick={window=key}).padding(vertical=12.dp)) { RadioButton(window==key,onClick=null); Text(stringResource(label),Modifier.padding(start=12.dp)) }
                }
            }
            if (failed) Text(stringResource(R.string.journal_error),color=MaterialTheme.colorScheme.error)
            Button(onClick={ if(step<2) step++ else { busy=true; scope.launch { val saved=withContext(Dispatchers.IO) { OnboardingProfile.save(context,goal,window) }; busy=false; if(saved) onComplete(goal,window) else failed=true } } },enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                Text(stringResource(when(step) { 0 -> R.string.onboarding_start; 1 -> R.string.onboarding_continue; else -> R.string.onboarding_done }))
            }
        }
    }
}
