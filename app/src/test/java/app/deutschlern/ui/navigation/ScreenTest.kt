package app.deutschlern.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File
import java.net.URLClassLoader

class ScreenTest {

    /**
     * Touching a screen object before the Screen class used to build the list while that object was
     * still null, which crashed the app on launch. Class initialization happens once per loader, so
     * only a fresh loader can recreate that order regardless of which test ran first.
     */
    @Test
    fun bottomNavItems_haveNoNulls_whenAScreenObjectIsInitializedFirst() {
        val classPath = System.getProperty("java.class.path")!!
            .split(File.pathSeparator)
            .map { File(it).toURI().toURL() }
            .toTypedArray()

        URLClassLoader(classPath, null).use { loader ->
            Class.forName("app.deutschlern.ui.navigation.Screen\$Translate", true, loader)

            val screen = loader.loadClass("app.deutschlern.ui.navigation.Screen")
            val companion = screen.getField("Companion").get(null)
            val items = companion.javaClass.getMethod("getBottomNavItems").invoke(companion) as List<*>

            assertEquals(5, items.size)
            assertFalse(items.toString(), items.contains(null))
        }
    }
}
