
package com.ats.sentinel

object NativeRuntimeBridge {

    private const val LIBRARY_NAME = "ats-native"

    val isLibraryLoaded: Boolean

    init {
        isLibraryLoaded = try {
            System.loadLibrary(LIBRARY_NAME)
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    external fun nativeStatus(): String
}
