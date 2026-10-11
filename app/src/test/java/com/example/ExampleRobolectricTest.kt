package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `app is called Pip`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Pip", context.getString(R.string.app_name))
    }

    @Test
    fun `test write pip toggle and dynamic listening enable once more`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = com.example.engine.WhisperSttEngine(context, kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))
        engine.setDynamicListening(true)
        assertTrue(engine.isDynamicListeningEnabled.value)

        // Tapping write bubble disables listening while user is typing
        engine.setDynamicListening(false)
        org.junit.Assert.assertFalse(engine.isDynamicListeningEnabled.value)

        // Tapping down arrow re-enables listening once the bubble is no longer visible
        engine.setDynamicListening(true)
        assertTrue(engine.isDynamicListeningEnabled.value)
    }
}
