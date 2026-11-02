package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.ClipDuration
import com.example.model.EditingStyle
import com.example.model.OutputQuality
import com.example.model.VideoFramingMode
import com.example.service.BatchManager
import com.example.service.VideoAnalyzerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {
    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AutoClip", appName)
    }

    @Test
    fun `plan cuts respects 30s max duration`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val service = VideoAnalyzerService(context)
        val cuts = service.planCuts(75_000L, ClipDuration.TWENTY_FIVE)
        assertTrue(cuts.isNotEmpty())
        for (cut in cuts) {
            assertTrue("Cut duration ${cut.durationMs}ms exceeds 30s", cut.durationMs <= 30_000L)
        }
    }

    @Test
    fun `default editing styles contains dual banner`() {
        val defaults = EditingStyle.defaultSelection()
        assertTrue(defaults.contains(EditingStyle.DUAL_BANNER))
    }

    @Test
    fun `output quality maintains 1080x1920 and 9 to 16 aspect ratio`() {
        val high = OutputQuality.HIGH
        assertEquals(1080, high.width)
        assertEquals(1920, high.height)
        assertEquals(9f / 16f, high.width.toFloat() / high.height.toFloat(), 0.001f)

        val standard = OutputQuality.STANDARD
        assertEquals(720, standard.width)
        assertEquals(1280, standard.height)
        assertEquals(9f / 16f, standard.width.toFloat() / standard.height.toFloat(), 0.001f)
    }

    @Test
    fun `video framing modes support 4 to 3 expanded 1 to 1 and 16 to 9`() {
        assertEquals(4f / 3f, VideoFramingMode.EXPANDED_4_3.aspectRatio, 0.001f)
        assertEquals(1f / 1f, VideoFramingMode.SQUARE_1_1.aspectRatio, 0.001f)
        assertEquals(16f / 9f, VideoFramingMode.FIT_16_9.aspectRatio, 0.001f)
    }

    @Test
    fun `batch manager creates sequential batch folders and persists across sessions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val batchManager = BatchManager(context)
        val batch1 = batchManager.createNextBatch("AutoClip")
        assertEquals("Batch_001", batch1)

        val batch2 = batchManager.createNextBatch("AutoClip")
        assertEquals("Batch_002", batch2)

        val reopenedManager = BatchManager(context)
        val batch3 = reopenedManager.createNextBatch("AutoClip")
        assertEquals("Batch_003", batch3)
    }

    @Test
    fun `batch manager formats sequential clip filenames accurately`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val batchManager = BatchManager(context)

        val clipNames18 = (1..18).map { batchManager.formatClipFileName(it) }
        assertEquals("Clip_001.mp4", clipNames18.first())
        assertEquals("Clip_018.mp4", clipNames18.last())
        assertEquals(18, clipNames18.size)

        val clipNames35 = (1..35).map { batchManager.formatClipFileName(it) }
        assertEquals("Clip_001.mp4", clipNames35.first())
        assertEquals("Clip_035.mp4", clipNames35.last())
        assertEquals(35, clipNames35.size)
    }
}
