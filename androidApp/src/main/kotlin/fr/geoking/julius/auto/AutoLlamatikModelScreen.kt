package fr.geoking.julius.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.lifecycleScope
import fr.geoking.julius.SettingsManager
import fr.geoking.julius.ui.LlamatikModelHelper
import fr.geoking.julius.ui.LlamatikModelVariant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto screen to download a Llamatik / on-device model. Shown from Settings for on-device agents.
 */
class AutoLlamatikModelScreen(
    carContext: CarContext,
    private val settingsManager: SettingsManager
) : Screen(carContext) {

    private val helper = LlamatikModelHelper(carContext.applicationContext)

    /** When non-null, we are downloading this variant and show progress. */
    private var downloadVariant: LlamatikModelVariant? = null
    private var downloadBytes: Long = 0L
    private var downloadTotal: Long? = null
    private var downloadError: String? = null
    private var lastInvalidateTime: Long = 0L

    override fun onGetTemplate(): Template {
        val settings = settingsManager.settings.value

        // Show progress or error message while downloading
        downloadVariant?.let { variant ->
            val progressText = when {
                downloadError != null -> "Error: $downloadError"
                downloadTotal != null && downloadTotal!! > 0 -> {
                    val pct = (100 * downloadBytes / downloadTotal!!).toInt()
                    "Downloading ${variant.displayName}… $pct%"
                }
                else -> "Downloading ${variant.displayName}… ${downloadBytes / (1024 * 1024)} MB"
            }
            val cancelAction = Action.Builder().setTitle("Cancel").setOnClickListener {
                downloadVariant = null
                downloadError = null
                invalidate()
            }.build()
            return MessageTemplate.Builder(progressText)
                .setHeader(
                    Header.Builder()
                        .setTitle("Llamatik model")
                        .setStartHeaderAction(Action.BACK)
                        .addEndHeaderAction(cancelAction)
                        .build()
                )
                .build()
        }

        val listBuilder = ItemList.Builder()
        for (variant in LlamatikModelVariant.entries.filter { it.agentType == settings.selectedAgent }) {
            val isDownloaded = helper.isVariantDownloaded(variant)
            val subtitle = if (isDownloaded) "Downloaded" else variant.sizeDescription
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(variant.displayName)
                    .addText(subtitle)
                    .setOnClickListener {
                        if (isDownloaded) {
                            // Select this variant and update path
                            val path = helper.getDownloadDestinationPath(variant)
                            settingsManager.saveSettings(
                                settings.copy(
                                    llamatikModelPath = path,
                                    selectedLlamatikModelVariant = variant.name
                                )
                            )
                            screenManager.pop()
                        } else {
                            downloadError = null
                            downloadVariant = variant
                            downloadBytes = 0L
                            downloadTotal = null
                            invalidate()
                            lifecycleScope.launch {
                                val result = helper.download(variant) { bytes, total ->
                                    lifecycleScope.launch(Dispatchers.Main) {
                                        downloadBytes = bytes
                                        downloadTotal = total
                                        val now = System.currentTimeMillis()
                                        if (now - lastInvalidateTime > 500) {
                                            lastInvalidateTime = now
                                            invalidate()
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    downloadVariant = null
                                    result.fold(
                                        onSuccess = { path ->
                                            val current = settingsManager.settings.value
                                            settingsManager.saveSettings(
                                                current.copy(
                                                    llamatikModelPath = path,
                                                    selectedLlamatikModelVariant = variant.name
                                                )
                                            )
                                        },
                                        onFailure = { e ->
                                            downloadError = e.message ?: "Download failed"
                                        }
                                    )
                                    invalidate()
                                }
                            }
                        }
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setSingleList(listBuilder.build())
            .setHeader(Header.Builder().setTitle("Download model (Llamatik)").setStartHeaderAction(Action.BACK).build())
            .build()
    }
}
