package dev.anilbeesetti.nextplayer.feature.player.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Classifies the active Android audio output route so [KodiAudioProcessor] can adapt
 * multichannel downmixing and LFE (sub-bass) handling automatically:
 *
 * - [SPEAKER]: Built-in phone/tablet speaker or earpiece. Cuts LFE (0.0f) to prevent speaker
 *   cone rattle and preserve headroom for dialogue.
 * - [STEREO_HEADPHONES]: Wired, USB, or Bluetooth headphones/earbuds without active Spatial Audio.
 *   Retains 35% LFE (0.35f) for warm sub-bass response while downmixing to 2.0 stereo.
 * - [MULTICHANNEL_OR_SPATIAL]: External HDMI/eARC multichannel sinks or headphones with Android
 *   Spatializer actively enabled and available. Preserves native 5.1/7.1 channels and applies
 *   Kodi's peak limiter across all channels.
 */
enum class AudioOutputRouteMode {
    SPEAKER,
    STEREO_HEADPHONES,
    MULTICHANNEL_OR_SPATIAL,
}

class AudioOutputRouteDetector(
    context: Context,
    private val onRouteChanged: (AudioOutputRouteMode) -> Unit,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    var currentRouteMode: AudioOutputRouteMode = AudioOutputRouteMode.SPEAKER
        private set

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshRouteMode()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            refreshRouteMode()
        }
    }

    private var spatializerListener: Any? = null

    fun start() {
        val manager = audioManager ?: return
        refreshRouteMode()
        manager.registerAudioDeviceCallback(audioDeviceCallback, mainHandler)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            runCatching {
                val spatializer = manager.spatializer
                val listener = object : Spatializer.OnSpatializerStateChangedListener {
                    override fun onSpatializerEnabledChanged(spat: Spatializer, enabled: Boolean) {
                        refreshRouteMode()
                    }

                    override fun onSpatializerAvailableChanged(spat: Spatializer, available: Boolean) {
                        refreshRouteMode()
                    }
                }
                spatializer.addOnSpatializerStateChangedListener(
                    { command -> mainHandler.post(command) },
                    listener,
                )
                spatializerListener = listener
            }
        }
    }

    fun stop() {
        val manager = audioManager ?: return
        runCatching {
            manager.unregisterAudioDeviceCallback(audioDeviceCallback)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            val listener = spatializerListener as? Spatializer.OnSpatializerStateChangedListener
            if (listener != null) {
                runCatching {
                    manager.spatializer.removeOnSpatializerStateChangedListener(listener)
                }
            }
            spatializerListener = null
        }
    }

    fun refreshRouteMode(): AudioOutputRouteMode {
        val detected = detectRouteMode()
        currentRouteMode = detected
        onRouteChanged(detected)
        return detected
    }

    private fun detectRouteMode(): AudioOutputRouteMode {
        val manager = audioManager ?: return AudioOutputRouteMode.SPEAKER
        val outputDevices = runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        }.getOrNull() ?: return AudioOutputRouteMode.SPEAKER

        var hasExternalMultichannel = false
        var hasHeadphones = false

        for (device in outputDevices) {
            if (!device.isSink) continue
            when (device.type) {
                AudioDeviceInfo.TYPE_HDMI,
                AudioDeviceInfo.TYPE_HDMI_ARC,
                AudioDeviceInfo.TYPE_HDMI_EARC,
                -> {
                    val maxChannels = device.channelCounts.maxOrNull() ?: 2
                    if (maxChannels > 2 || device.channelCounts.isEmpty()) {
                        hasExternalMultichannel = true
                    }
                }

                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                -> {
                    hasHeadphones = true
                }

                else -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                        (device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                            device.type == AudioDeviceInfo.TYPE_BLE_BROADCAST)
                    ) {
                        hasHeadphones = true
                    }
                }
            }
        }

        if (hasExternalMultichannel) {
            return AudioOutputRouteMode.MULTICHANNEL_OR_SPATIAL
        }

        if (hasHeadphones) {
            if (isSpatializerActive(manager)) {
                return AudioOutputRouteMode.MULTICHANNEL_OR_SPATIAL
            }
            return AudioOutputRouteMode.STEREO_HEADPHONES
        }

        return AudioOutputRouteMode.SPEAKER
    }

    private fun isSpatializerActive(manager: AudioManager): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return false
        return runCatching {
            val spatializer = manager.spatializer
            spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE &&
                spatializer.isEnabled &&
                spatializer.isAvailable
        }.getOrDefault(false)
    }
}
