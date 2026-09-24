package com.exchip.offzone

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/** Runs only with -e offzoneAuthEmulator true and the isolated demo project emulators. */
@RunWith(AndroidJUnit4::class)
class AccountStoreAuthIntegrationTest {
    @Test fun signupVerifyGoalResetSignoutSigninDelete() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("offzoneAuthEmulator") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val project = "demo-offzone-android"
        val app = FirebaseApp.initializeApp(instrumentation.targetContext,
            FirebaseOptions.Builder().setProjectId(project).setApplicationId("1:123456789:android:test")
                .setApiKey("fake-emulator-api-key").build(), "account-test-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(app)
        auth.useEmulator("10.0.2.2", 9099)
        lateinit var store: AccountStore
        instrumentation.runOnMainSync {
            store = AccountStore(instrumentation.targetContext, app)
            FirebaseFirestore.getInstance(app).useEmulator("10.0.2.2", 8080)
        }
        val email = "offzone-${UUID.randomUUID()}@example.test"
        val password = "Test-only-pass-9284"
        fun action(block: () -> Unit) {
            instrumentation.runOnMainSync(block)
            val deadline = System.currentTimeMillis() + 25_000
            while (store.state.value.busy && System.currentTimeMillis() < deadline) Thread.sleep(50)
            assertFalse("Account operation timed out", store.state.value.busy)
        }
        fun noError() = assertNull(store.state.value.error, store.state.value.error)
        try {
            action { store.createAccount(email, password) }; noError()
            val uid = store.state.value.uid
            assertNotNull(uid); assertFalse(store.state.value.verified)
            action { store.saveGoal("work") }
            assertNotNull("Unverified account must not save its goal", store.state.value.error)
            assertNull(store.state.value.savedGoal)
            action { store.sendVerification() }; noError()
            val connection = URL("http://10.0.2.2:9099/emulator/v1/projects/$project/oobCodes").openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000; connection.readTimeout = 5_000
            val codes = try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }).getJSONArray("oobCodes") } finally { connection.disconnect() }
            val code = (0 until codes.length()).map { codes.getJSONObject(it) }.last {
                it.optString("email") == email && it.optString("requestType") == "VERIFY_EMAIL"
            }.getString("oobCode")
            runBlocking { auth.applyActionCode(code).await() }
            action { store.reloadVerification() }; noError(); assertTrue(store.state.value.verified)
            action { store.saveGoal("presence") }; noError(); assertEquals("presence", store.state.value.savedGoal)
            action { store.resetPassword(email) }; noError()
            action { store.signOut() }; noError(); assertNull(store.state.value.uid); assertFalse(store.hasProAccess)
            action { store.signIn(email, password) }; noError(); assertEquals(uid, store.state.value.uid)
            assertEquals("presence", store.state.value.savedGoal)
            action { store.deleteWithPassword(password) }; noError(); assertNull(store.state.value.uid)
            val result = runCatching { runBlocking { auth.signInWithEmailAndPassword(email, password).await() } }
            assertTrue("Deleted account must no longer authenticate", result.isFailure)
        } finally {
            // This Firebase app only ever talks to the demo emulators.
            runBlocking { runCatching { auth.currentUser?.delete()?.await() } }
            instrumentation.runOnMainSync { store.close() }
            runBlocking { FirebaseFirestore.getInstance(app).terminate().await() }
            app.delete()
        }
    }
}
