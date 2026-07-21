package com.example.flutter_kinescope_sdk_example

import android.content.res.Configuration
import android.os.Build
import io.flutter.embedding.android.FlutterFragmentActivity
import io.kinescope.flutter_kinescope_sdk.KinescopePipRegistry

class MainActivity : FlutterFragmentActivity() {
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        }
        KinescopePipRegistry.dispatchModeChanged(isInPictureInPictureMode, newConfig)
    }

    override fun onStop() {
        KinescopePipRegistry.dispatchOnStop()
        super.onStop()
    }
}
