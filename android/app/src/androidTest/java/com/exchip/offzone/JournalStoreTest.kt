package com.exchip.offzone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class JournalStoreTest {
    @Test fun persistenceEntitlementImportAndCorruptRecovery() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"journal-test-${System.nanoTime()}")
        dir.mkdirs()
        try {
            val file=File(dir,"journal.json"); var pro=true
            val store=JournalStore(file) { pro }; val today=LocalDate.now()
            val plan=JournalPlan("Time together","presence",setOf(1,2,3))
            store.setPlan(plan,today); store.reflect(JournalReflection("Put phone away","Kept my intention"),today)
            assertEquals(Triple(3,1,1),store.summary(today))
            assertEquals(store.data,JournalStore(file) { true }.data)
            assertEquals(1,JournalStore.characterCount("👨‍👩‍👧‍👦"))
            val bytes=store.export(); pro=false
            assertTrue(runCatching { store.setPlan(plan,today.minusWeeks(1)) }.isFailure)
            assertTrue(runCatching { store.reflect(JournalReflection("new","Partly"),today) }.isFailure)
            assertTrue(runCatching { store.import(bytes) }.isFailure)
            assertArrayEquals(bytes,store.export())
            store.deletePlan(today); store.deleteReflection(today.toString())
            assertEquals(JournalData(),store.data)
            pro=true; store.import(bytes); assertEquals(Triple(3,1,1),store.summary(today))
            val conflict=String(bytes).replace("Time together","Changed intention").toByteArray()
            assertTrue(runCatching { store.import(conflict) }.isFailure); assertArrayEquals(bytes,store.export())
            assertTrue(runCatching { store.reflect(JournalReflection("future","Partly"),today.plusDays(1)) }.isFailure)
            assertTrue(runCatching { store.setPlan(plan.copy(weekdays=setOf(0)),today) }.isFailure)
            listOf("{}","{\"version\":2,\"plans\":{},\"reflections\":{}}",String(bytes)+"junk",String(bytes).replace(today.toString(),"2026-02-30")).forEach { invalid ->
                assertTrue(runCatching { JournalStore.decode(invalid.toByteArray()) }.isFailure)
            }
            val corrupt=byteArrayOf(0xff.toByte(),0x00,0x41); file.writeBytes(corrupt)
            val broken=JournalStore(file) { true }; assertTrue(broken.loadFailed)
            assertArrayEquals(corrupt,broken.export())
            assertTrue(runCatching { broken.setPlan(plan,today) }.isFailure)
            assertTrue(runCatching { broken.import(bytes) }.isFailure)
            assertArrayEquals(corrupt,file.readBytes())
            broken.deleteAll(); assertFalse(broken.loadFailed); broken.import(bytes)
            assertEquals(Triple(3,1,1),broken.summary(today))
        } finally { dir.deleteRecursively() }
    }
}
