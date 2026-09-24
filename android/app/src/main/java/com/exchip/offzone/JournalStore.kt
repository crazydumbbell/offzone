package com.exchip.offzone

import android.content.Context
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

data class JournalPlan(val intention: String, val context: String, val weekdays: Set<Int>)
data class JournalReflection(val note: String, val outcome: String)
data class JournalData(val plans: Map<String, JournalPlan> = emptyMap(), val reflections: Map<String, JournalReflection> = emptyMap())

/** Local, excluded from backup; malformed data remains untouched until explicit deletion. */
class JournalStore(private val file: File, private val hasAccess: () -> Boolean) {
    constructor(context: Context, hasAccess: () -> Boolean) : this(File(context.noBackupFilesDir, "journal.json"), hasAccess)
    private val atomic = AtomicFile(file)
    var data = JournalData(); private set
    var loadFailed = false; private set
    private fun exists() = file.exists() || File(file.path + ".bak").exists()
    init { if (exists()) try { data = decode(atomic.readFully()) } catch (_: Exception) { loadFailed = true } }
    @Synchronized fun setPlan(plan: JournalPlan, date: LocalDate) {
        check(hasAccess()) { "access" }
        save(data.copy(plans = data.plans + (week(date).toString() to plan)))
    }
    @Synchronized fun reflect(reflection: JournalReflection, date: LocalDate) {
        check(hasAccess()) { "access" }; require(date <= LocalDate.now())
        save(data.copy(reflections = data.reflections + (date.toString() to reflection)))
    }
    @Synchronized fun deletePlan(date: LocalDate) = save(data.copy(plans = data.plans - week(date).toString()))
    @Synchronized fun deleteReflection(date: String) = save(data.copy(reflections = data.reflections - date))
    @Synchronized fun deleteAll() {
        atomic.delete()
        check(!exists() && !File(file.path + ".new").exists())
        data = JournalData(); loadFailed = false
    }
    @Synchronized fun export(): ByteArray = if (loadFailed) atomic.readFully() else encode(data)
    /** Merge only after validating the complete import; conflicting entries require explicit deletion first. */
    @Synchronized fun import(bytes: ByteArray) {
        check(hasAccess()) { "access" }; check(!loadFailed)
        val incoming = decode(bytes)
        require(incoming.plans.all { (k,v) -> data.plans[k] == null || data.plans[k] == v })
        require(incoming.reflections.all { (k,v) -> data.reflections[k] == null || data.reflections[k] == v })
        save(JournalData(data.plans + incoming.plans, data.reflections + incoming.reflections))
    }
    fun summary(date: LocalDate): Triple<Int, Int, Int> {
        val start = week(date)
        val entries = (0L..6L).mapNotNull { data.reflections[start.plusDays(it).toString()] }
        return Triple(data.plans[start.toString()]?.weekdays?.size ?: 0, entries.size, entries.count { it.outcome == outcomes.first() })
    }
    private fun save(next: JournalData) {
        check(!loadFailed)
        val bytes = encode(next)
        decode(bytes)
        file.parentFile?.mkdirs()
        val stream = atomic.startWrite()
        try { stream.write(bytes); stream.fd.sync(); atomic.finishWrite(stream) } catch (e: Exception) { atomic.failWrite(stream); throw e }
        check(atomic.readFully().contentEquals(bytes))
        data = next
    }
    companion object {
        // ponytail: 4 MiB archive ceiling; use a streaming merge if journals outgrow it.
        const val MAX_BYTES = 4 * 1024 * 1024
        val contexts = listOf("work", "rest", "presence", "personal")
        val outcomes = listOf("Kept my intention", "Partly", "Try again")
        fun characterCount(text: String): Int {
            val iterator = android.icu.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
            iterator.setText(text)
            var count = 0
            while (iterator.next() != android.icu.text.BreakIterator.DONE) count++
            return count
        }
        fun week(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        fun decode(bytes: ByteArray): JournalData {
            require(bytes.size <= MAX_BYTES)
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            val text = decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
            android.util.JsonReader(java.io.StringReader(text)).use { reader ->
                reader.isLenient = false
                var depth = 0
                while (reader.peek() != android.util.JsonToken.END_DOCUMENT) {
                    when (reader.peek()) {
                        android.util.JsonToken.BEGIN_OBJECT -> { reader.beginObject(); depth++ }
                        android.util.JsonToken.END_OBJECT -> { reader.endObject(); depth-- }
                        android.util.JsonToken.BEGIN_ARRAY -> { reader.beginArray(); depth++ }
                        android.util.JsonToken.END_ARRAY -> { reader.endArray(); depth-- }
                        android.util.JsonToken.NAME -> reader.nextName()
                        android.util.JsonToken.STRING, android.util.JsonToken.NUMBER -> reader.nextString()
                        android.util.JsonToken.BOOLEAN -> reader.nextBoolean()
                        android.util.JsonToken.NULL -> reader.nextNull()
                        else -> error("Invalid JSON")
                    }
                    require(depth in 0..8)
                }
            }
            val parser = org.json.JSONTokener(text)
            val root = parser.nextValue() as JSONObject
            require(parser.nextClean() == '\u0000')
            require(root.get("version") == 1)
            val plans = root.getJSONObject("plans"); val reflections = root.getJSONObject("reflections")
            fun validDay(key: String) { require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(key)); require(LocalDate.parse(key).toString() == key) }
            val p = plans.keys().asSequence().associateWith { key ->
                validDay(key)
                val item = plans.getJSONObject(key)
                val intention = item.get("intention") as String; val context = item.get("context") as String
                val days = item.getJSONArray("weekdays")
                val weekdays = (0 until days.length()).map { days.get(it).also { day -> require(day is Int && day in 1..7) } as Int }.toSet()
                require(intention.isNotBlank() && characterCount(intention) <= 160 && context in contexts && weekdays.isNotEmpty())
                JournalPlan(intention, context, weekdays)
            }
            val r = reflections.keys().asSequence().associateWith { key ->
                validDay(key)
                val item = reflections.getJSONObject(key)
                val note = item.get("note") as String; val outcome = item.get("outcome") as String
                require(note.isNotBlank() && characterCount(note) <= 2000 && outcome in outcomes)
                JournalReflection(note, outcome)
            }
            return JournalData(p,r)
        }
        fun encode(data: JournalData): ByteArray {
            val plans = JSONObject(); val reflections = JSONObject()
            data.plans.forEach { (key,p) -> plans.put(key, JSONObject().put("intention",p.intention).put("context",p.context).put("weekdays",JSONArray(p.weekdays.sorted()))) }
            data.reflections.forEach { (key,r) -> reflections.put(key,JSONObject().put("note",r.note).put("outcome",r.outcome)) }
            return JSONObject().put("version",1).put("plans",plans).put("reflections",reflections).toString(2).toByteArray(Charsets.UTF_8)
        }
    }
}
