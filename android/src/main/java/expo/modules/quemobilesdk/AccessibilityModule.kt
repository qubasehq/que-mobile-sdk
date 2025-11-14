package expo.modules.quemobilesdk

import android.graphics.Point
import android.view.Display
import android.view.WindowManager
import expo.modules.kotlin.Promise
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class AccessibilityModule : Module() {
    override fun definition() = ModuleDefinition {
        Name("AccessibilityModule")

        // Dump UI hierarchy as XML
        AsyncFunction("dumpHierarchy") { promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val hierarchy = service.dumpHierarchy()
                promise.resolve(hierarchy)
            } catch (e: Exception) {
                promise.reject("DUMP_HIERARCHY_FAILED", "Failed to dump hierarchy: ${e.message}", e)
            }
        }

        // Click on specific coordinates
        AsyncFunction("clickOnPoint") { x: Double, y: Double, promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.clickOnPoint(x.toFloat(), y.toFloat())
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("CLICK_FAILED", "Failed to click: ${e.message}", e)
            }
        }

        // Long press on specific coordinates
        AsyncFunction("longPressOnPoint") { x: Double, y: Double, promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.longPressOnPoint(x.toFloat(), y.toFloat())
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("LONG_PRESS_FAILED", "Failed to long press: ${e.message}", e)
            }
        }

        // Type text into focused field
        AsyncFunction("typeText") { text: String, promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.typeText(text)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("TYPE_TEXT_FAILED", "Failed to type text: ${e.message}", e)
            }
        }

        // Scroll in specified direction
        AsyncFunction("scroll") { direction: String, amount: Double, promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.scroll(direction, amount.toInt())
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("SCROLL_FAILED", "Failed to scroll: ${e.message}", e)
            }
        }

        // Perform back button action
        AsyncFunction("performBack") { promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.performBack()
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("BACK_FAILED", "Failed to perform back: ${e.message}", e)
            }
        }

        // Perform home button action
        AsyncFunction("performHome") { promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.performHome()
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("HOME_FAILED", "Failed to perform home: ${e.message}", e)
            }
        }

        // Perform recents (app switcher) action
        AsyncFunction("performRecents") { promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.performRecents()
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("RECENTS_FAILED", "Failed to perform recents: ${e.message}", e)
            }
        }

        // Press enter key
        AsyncFunction("pressEnter") { promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.pressEnter()
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("ENTER_FAILED", "Failed to press enter: ${e.message}", e)
            }
        }

        // Check if keyboard is open
        AsyncFunction("isKeyboardOpen") { promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.isKeyboardOpen()
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("KEYBOARD_CHECK_FAILED", "Failed to check keyboard: ${e.message}", e)
            }
        }

        // Get current activity name
        AsyncFunction("getCurrentActivity") { promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.getCurrentActivity()
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("ACTIVITY_CHECK_FAILED", "Failed to get activity: ${e.message}", e)
            }
        }

        // Open app by package name
        AsyncFunction("openApp") { packageName: String, promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.openApp(packageName)
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("OPEN_APP_FAILED", "Failed to open app: ${e.message}", e)
            }
        }

        // Get screen dimensions
        AsyncFunction("getScreenDimensions") { promise: Promise ->
            try {
                val context = appContext.reactContext ?: throw Exception("Context not available")
                val windowManager = context.getSystemService(android.content.Context.WINDOW_SERVICE) as WindowManager
                val display: Display = windowManager.defaultDisplay
                val size = Point()
                display.getRealSize(size)
                
                promise.resolve(mapOf(
                    "width" to size.x,
                    "height" to size.y
                ))
            } catch (e: Exception) {
                promise.reject("SCREEN_DIMENSIONS_FAILED", "Failed to get screen dimensions: ${e.message}", e)
            }
        }

        // Get scroll information
        AsyncFunction("getScrollInfo") { promise: Promise ->
            val service = QueAccessibilityService.instance
            if (service == null) {
                promise.reject("SERVICE_NOT_AVAILABLE", "Accessibility service is not enabled", null)
                return@AsyncFunction
            }
            
            try {
                val result = service.getScrollInfo()
                promise.resolve(result)
            } catch (e: Exception) {
                promise.reject("SCROLL_INFO_FAILED", "Failed to get scroll info: ${e.message}", e)
            }
        }

        // Find package name by app name
        AsyncFunction("findPackageByAppName") { appName: String, promise: Promise ->
            try {
                val context = appContext.reactContext ?: throw Exception("Context not available")
                val pm = context.packageManager
                val packages = pm.getInstalledApplications(0)

                // First, try for an exact match (case-insensitive)
                for (appInfo in packages) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    if (label.equals(appName, ignoreCase = true)) {
                        promise.resolve(appInfo.packageName)
                        return@AsyncFunction
                    }
                }

                // If no exact match, try for a partial match (contains)
                for (appInfo in packages) {
                    val label = pm.getApplicationLabel(appInfo).toString()
                    if (label.contains(appName, ignoreCase = true)) {
                        promise.resolve(appInfo.packageName)
                        return@AsyncFunction
                    }
                }

                // Not found
                promise.resolve(null)
            } catch (e: Exception) {
                promise.reject("FIND_PACKAGE_FAILED", "Failed to find package: ${e.message}", e)
            }
        }
    }
}
