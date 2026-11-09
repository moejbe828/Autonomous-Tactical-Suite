
package com.ats.sentinel

import android.content.Context
import java.io.File
import java.io.FileInputStream

data class LocalAIReport(
    val runtimeIntegrated: Boolean,
    val modelInstalled: Boolean,
    val modelValid: Boolean,
    val modelSizeMb: Long,
    val modelPath: String,
    val message: String
)

object LocalAIEngine {

    private const val MODEL_DIRECTORY = "models"
    private const val MODEL_FILE_NAME = "ats-model.gguf"

    /*
     * Native llama.cpp runtime will be integrated
     * in the next development step.
     */
    private const val RUNTIME_INTEGRATED = false

    fun getModelDirectory(context: Context): File {
        return File(
            context.filesDir,
            MODEL_DIRECTORY
        ).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    fun inspect(context: Context): LocalAIReport {

        val modelFile = File(
            getModelDirectory(context),
            MODEL_FILE_NAME
        )

        if (!modelFile.exists() || !modelFile.isFile) {
            return LocalAIReport(
                runtimeIntegrated = RUNTIME_INTEGRATED,
                modelInstalled = false,
                modelValid = false,
                modelSizeMb = 0L,
                modelPath = modelFile.absolutePath,
                message = "MODEL NOT INSTALLED"
            )
        }

        if (!modelFile.canRead()) {
            return LocalAIReport(
                runtimeIntegrated = RUNTIME_INTEGRATED,
                modelInstalled = true,
                modelValid = false,
                modelSizeMb = 0L,
                modelPath = modelFile.absolutePath,
                message = "MODEL NOT READABLE"
            )
        }

        val validHeader = try {
            FileInputStream(modelFile).use { stream ->
                val header = ByteArray(4)
                stream.read(header) == 4 &&
                    String(header, Charsets.US_ASCII) == "GGUF"
            }
        } catch (_: Exception) {
            false
        }

        val sizeMb =
            modelFile.length() / (1024L * 1024L)

        val message = when {
            !validHeader -> "INVALID GGUF MODEL"
            !RUNTIME_INTEGRATED ->
                "MODEL FOUND • RUNTIME PENDING"
            else -> "MODEL READY FOR RUNTIME CHECK"
        }

        return LocalAIReport(
            runtimeIntegrated = RUNTIME_INTEGRATED,
            modelInstalled = true,
            modelValid = validHeader,
            modelSizeMb = sizeMb,
            modelPath = modelFile.absolutePath,
            message = message
        )
    }
}
