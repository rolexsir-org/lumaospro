package com.lumaos.pro.launcher.robolectric

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.lumaos.pro.launcher.R
import com.lumaos.pro.launcher.wallpaper.WallpaperCatalog
import com.lumaos.pro.launcher.wallpaper.WallpaperException
import com.lumaos.pro.launcher.wallpaper.WallpaperImageStore
import com.lumaos.pro.launcher.wallpaper.WallpaperLimits
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.RandomAccessFile

/** Android bitmap/file integration, intended for Robolectric's native graphics mode. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WallpaperImageStoreRuntimeTest {
    private lateinit var root: File
    private lateinit var context: Context

    @Before fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        root = File(base.cacheDir, "wallpaper_test_${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(base) {
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
        }
    }

    @After fun tearDown() { root.deleteRecursively() }

    @Test fun `bundled image bounds pass can return null bitmap but preview still decodes`() = runBlocking {
        val image = WallpaperImageStore(context).preview(WallpaperCatalog.bundled().first(), 240)
        assertNotNull(image)
        assertTrue(image!!.width > 0 && image.height > 0)
    }

    @Test fun `every gallery entry decodes and supports launcher only application`() = runBlocking {
        val store = WallpaperImageStore(context)
        val entries = store.entries()
        assertEquals(30, entries.size)
        for (entry in entries) {
            val thumbnail = store.preview(entry, 240)
            assertNotNull("Missing thumbnail for ${entry.id}", thumbnail)
            assertTrue(thumbnail!!.width > 0 && thumbnail.height > 0)
            assertNull(store.apply(entry, com.lumaos.pro.launcher.wallpaper.WallpaperTarget.LUMA_HOME))
        }
    }

    @Test fun `photo is copied privately survives store recreation and deletion preserves original`() = runBlocking {
        val original = File(root, "original.png")
        val source = Bitmap.createBitmap(80, 60, Bitmap.Config.ARGB_8888)
        original.outputStream().use { assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        source.recycle()
        val entry = WallpaperImageStore(context).import(Uri.fromFile(original))
        val reopened = WallpaperImageStore(context)
        assertTrue(reopened.entries().any { it.id == entry.id })
        assertNotNull(reopened.preview(entry, 240))
        assertTrue(reopened.delete(entry))
        assertTrue(original.isFile)
        assertFalse(reopened.entries().any { it.id == entry.id })
    }

    @Test fun `oversized input is rejected without publishing a partial photo`() = runBlocking {
        val original = File(root, "oversized.bin")
        RandomAccessFile(original, "rw").use { it.setLength(WallpaperLimits.MAX_FILE_BYTES + 1) }
        val store = WallpaperImageStore(context)
        try {
            store.import(Uri.fromFile(original))
            fail("Expected the size limit")
        } catch (e: WallpaperException) {
            assertEquals(R.string.wallpaper_limit_size, e.messageRes)
        }
        assertTrue(store.entries().none { it.isCustom })
        assertTrue(original.isFile)
    }
}
