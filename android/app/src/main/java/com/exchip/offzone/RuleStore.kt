package com.exchip.offzone

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class RuleStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("rules", Context.MODE_PRIVATE)
    private val mutableRules = MutableStateFlow(emptyList<FocusRule>())
    private val mutableError = MutableStateFlow(false)
    val rules = mutableRules.asStateFlow()
    val error = mutableError.asStateFlow()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "saved") reload()
    }
    init { reload(); preferences.registerOnSharedPreferenceChangeListener(listener) }
    fun close() { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    private fun reload() {
        runCatching {
            val array = JSONArray(preferences.getString("saved", "[]"))
            val read = (0 until array.length()).map { decode(array.getJSONObject(it)) }
            require(read.map { it.id }.distinct().size == read.size)
            mutableRules.value = read
            mutableError.value = false
        }.onFailure { mutableError.value = true }
    }
    fun save(rule: FocusRule): Boolean {
        if (!rule.valid() || error.value) return false
        val copy = rule.copy(packages = rule.packages.toSet())
        val updated = if (rules.value.any { it.id == copy.id }) rules.value.map { if (it.id == copy.id) copy else it } else rules.value + copy
        return write(updated)
    }
    fun delete(id: String): Boolean {
        if (error.value) return false
        if (FocusController.state.value.appliedRule?.id == id) FocusController.pauseRule()
        return write(rules.value.filterNot { it.id == id })
    }
    fun hasUnappliedChanges(id: String): Boolean {
        val applied = FocusController.state.value.appliedRule ?: return false
        return applied.id == id && rules.value.firstOrNull { it.id == id }?.let { it != applied } == true
    }
    private fun write(rules: List<FocusRule>): Boolean {
        val editor = preferences.edit()
        if (!preferences.contains("original")) preferences.getString("saved", null)?.let { editor.putString("original", it) }
        val ok = editor.putString("saved", JSONArray(rules.map(::encode)).toString()).commit()
        if (ok) mutableRules.value = rules
        return ok
    }
    internal fun applied(): FocusRule? = preferences.getString("applied", null)?.let { decode(JSONObject(it)) }
    internal fun apply(rule: FocusRule?): Boolean = preferences.edit().apply {
        if (rule == null) remove("applied") else putString("applied", encode(rule).toString())
    }.commit()
    companion object {
        internal fun encode(rule: FocusRule) = JSONObject().put("id", rule.id).put("name", rule.name)
            .put("packages", JSONArray(rule.packages.sorted())).put("start", rule.startMinutes).put("end", rule.endMinutes)
            .put("latitude", rule.latitude).put("longitude", rule.longitude).put("placeLabel", rule.placeLabel)
        internal fun decode(value: JSONObject): FocusRule {
            val apps = value.getJSONArray("packages")
            return FocusRule(value.getString("id"), value.getString("name"),
                (0 until apps.length()).map { apps.getString(it) }.toSet(), value.getInt("start"), value.getInt("end"),
                value.getDouble("latitude"), value.getDouble("longitude"), value.optString("placeLabel"))
                .also { require(it.valid()) }
        }
    }
}
