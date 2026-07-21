package io.kinescope.flutter_kinescope_sdk

import android.content.res.Configuration

object KinescopePipRegistry {
    private val bindings = mutableSetOf<KinescopePipBinding>()

    fun register(binding: KinescopePipBinding) {
        bindings.add(binding)
    }

    fun unregister(binding: KinescopePipBinding) {
        bindings.remove(binding)
    }

    fun dispatchModeChanged(isInPictureInPictureMode: Boolean, configuration: Configuration) {
        bindings.forEach { binding ->
            binding.onPictureInPictureModeChanged(isInPictureInPictureMode, configuration)
        }
    }

    fun dispatchOnStop() {
        bindings.forEach { it.onStop() }
    }
}
