package dev.tilevio.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AndroidLaunchableAppsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val packageManager = context.packageManager
    private val launchableApps = AndroidLaunchableApps(packageManager, OWN_PACKAGE_NAME, tileWidthPx = 320)

    @Test
    fun unchangedPackageKeepsItsLoadedAppWhenAnotherPackageIsUpdated() {
        install(ALPHA, "Alpha", Color.RED, lastUpdateTime = 1)
        install(BRAVO, "Bravo", Color.RED, lastUpdateTime = 1)
        val known = launchableApps.load(emptyList())

        install(BRAVO, "Bravo", Color.BLUE, lastUpdateTime = 2)
        val reloaded = launchableApps.load(known)

        assertSame(known.app(ALPHA), reloaded.app(ALPHA))
        assertNotSame(known.app(BRAVO), reloaded.app(BRAVO))
    }

    @Test
    fun updatedPackageShowsItsNewLabelIconAndBanner() {
        install(ALPHA, "Alpha", Color.RED, lastUpdateTime = 1)
        val known = launchableApps.load(emptyList())

        install(ALPHA, "Alpha 2", Color.BLUE, lastUpdateTime = 2)
        val updated = launchableApps.load(known).app(ALPHA)

        assertEquals("Alpha 2", updated.label)
        assertEquals(Color.BLUE, updated.icon.getPixel(0, 0))
        assertEquals(Color.BLUE, updated.banner?.getPixel(0, 0))
    }

    @Test
    fun removedPackageLeavesTheCatalog() {
        install(ALPHA, "Alpha", Color.RED, lastUpdateTime = 1)
        install(BRAVO, "Bravo", Color.RED, lastUpdateTime = 1)
        val known = launchableApps.load(emptyList())

        uninstall(BRAVO)

        assertEquals(listOf(ALPHA), launchableApps.load(known).map { app -> app.componentName.packageName })
    }

    @Test
    fun appWhoseMainActivityChangedIsLoadedAgain() {
        install(ALPHA, "Alpha", Color.RED, lastUpdateTime = 1)
        val known = launchableApps.load(emptyList())

        install(ALPHA, "Alpha", Color.RED, lastUpdateTime = 1, activityName = "OtherActivity")
        val reloaded = launchableApps.load(known).app(ALPHA)

        assertEquals("$ALPHA.OtherActivity", reloaded.componentName.className)
    }

    @Test
    fun tilevioIsLeftOutOfTheCatalog() {
        install(ALPHA, "Alpha", Color.RED, lastUpdateTime = 1)
        install(OWN_PACKAGE_NAME, "Tilevio", Color.RED, lastUpdateTime = 1)

        assertEquals(
            listOf(ALPHA),
            launchableApps.load(emptyList()).map { app -> app.componentName.packageName },
        )
    }

    private fun List<App>.app(packageName: String): App =
        single { app -> app.componentName.packageName == packageName }

    private fun install(
        packageName: String,
        label: String,
        color: Int,
        lastUpdateTime: Long,
        activityName: String = "TvActivity",
    ) {
        uninstall(packageName)
        val shadowPackageManager = shadowOf(packageManager)
        shadowPackageManager.installPackage(
            PackageInfo().apply {
                this.packageName = packageName
                this.lastUpdateTime = lastUpdateTime
            },
        )
        val bitmap = Bitmap.createBitmap(64, 36, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        shadowPackageManager.addDrawableResolution(packageName, ICON_RESOURCE, BitmapDrawable(context.resources, bitmap))
        shadowPackageManager.addDrawableResolution(packageName, BANNER_RESOURCE, BitmapDrawable(context.resources, bitmap))
        shadowPackageManager.addResolveInfoForIntent(
            leanbackIntent,
            ResolveInfo().apply {
                nonLocalizedLabel = label
                activityInfo = ActivityInfo().apply {
                    this.packageName = packageName
                    name = "$packageName.$activityName"
                    icon = ICON_RESOURCE
                    banner = BANNER_RESOURCE
                }
            },
        )
    }

    private fun uninstall(packageName: String) {
        val shadowPackageManager = shadowOf(packageManager)
        shadowPackageManager.removeResolveInfosForIntent(leanbackIntent, packageName)
        shadowPackageManager.removePackage(packageName)
    }

    private val leanbackIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

    private companion object {
        const val ALPHA = "example.alpha"
        const val BRAVO = "example.bravo"
        const val OWN_PACKAGE_NAME = "dev.tilevio.launcher"
        const val ICON_RESOURCE = 1001
        const val BANNER_RESOURCE = 1002
    }
}
