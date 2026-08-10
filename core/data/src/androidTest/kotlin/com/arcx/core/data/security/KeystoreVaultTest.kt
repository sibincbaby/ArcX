package com.arcx.core.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented rather than a unit test because the whole point of the class is the AndroidKeyStore,
 * which only exists on a device — a JVM test would either fake it away or test nothing.
 */
@RunWith(AndroidJUnit4::class)
class KeystoreVaultTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var vault: KeystoreVault

    @Before
    fun setUp() {
        context.preferencesDataStoreFile(STORE).delete()
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile(STORE)
        }
        vault = KeystoreVault(store)
    }

    @After
    fun tearDown() {
        scope.cancel()
        context.preferencesDataStoreFile(STORE).delete()
    }

    @Test
    fun putThenGetRoundTripsThePlaintextKey() = runBlocking {
        vault.put(PROVIDER_ID, API_KEY)

        assertEquals(API_KEY, vault.get(PROVIDER_ID))
        assertTrue(vault.has(PROVIDER_ID))
    }

    @Test
    fun getReturnsNullForAnUnknownProvider() = runBlocking {
        vault.put(PROVIDER_ID, API_KEY)

        assertNull(vault.get("never-configured"))
        assertFalse(vault.has("never-configured"))
    }

    @Test
    fun eachEntryGetsItsOwnIvSoIdenticalKeysDoNotShareCiphertext() = runBlocking {
        vault.put("openai", API_KEY)
        vault.put("groq", API_KEY)

        val stored = store.data.first().asMap().values.map { it.toString() }
        assertEquals(2, stored.size)
        assertTrue("the plaintext key reached disk", stored.none { it.contains(API_KEY) })
        assertEquals("the same key encrypted twice produced the same blob", 2, stored.toSet().size)
    }

    @Test
    fun removeDropsTheEntry() = runBlocking {
        vault.put(PROVIDER_ID, API_KEY)
        vault.remove(PROVIDER_ID)

        assertNull(vault.get(PROVIDER_ID))
        assertFalse(vault.has(PROVIDER_ID))
    }

    private companion object {
        const val STORE = "arcx_keys_test"
        const val PROVIDER_ID = "openai"
        const val API_KEY = "sk-test-0123456789abcdef"
    }
}
