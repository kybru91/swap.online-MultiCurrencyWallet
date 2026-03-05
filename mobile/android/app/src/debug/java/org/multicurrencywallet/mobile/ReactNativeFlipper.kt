package org.multicurrencywallet.mobile

import android.content.Context
import com.facebook.react.ReactInstanceEventListener
import com.facebook.react.ReactInstanceManager
import com.facebook.react.bridge.ReactContext
import com.facebook.react.modules.network.NetworkingModule

/**
 * Class responsible for loading Flipper inside your React Native application.
 * This is currently configured only for Debug builds.
 *
 * NOTE: Flipper is disabled in the current setup to keep debug builds simpler.
 * To enable Flipper, add the following to app/build.gradle:
 *   debugImplementation("com.facebook.flipper:flipper:0.246.0") { ... }
 *   debugImplementation("com.facebook.flipper:flipper-network-plugin:0.246.0")
 *   debugImplementation("com.facebook.flipper:flipper-fresco-plugin:0.246.0")
 *
 * Then uncomment the code below.
 */
object ReactNativeFlipper {
    fun initializeFlipper(context: Context, reactInstanceManager: ReactInstanceManager) {
        // Flipper integration disabled — use adb logcat for debugging.
        // See mobile/DEBUGGING.md for crash analysis tools.
    }
}
