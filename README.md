# FoldOpenFX

Experimental Android project for a foldable opening/closing visual effect.

### Prototype features
- Reads `Sensor.TYPE_HINGE_ANGLE` when available.
- Runs a foreground service.
- Draws a lightweight system overlay around the center crease.
- Builds a debug APK through GitHub Actions.

### Important
This is a prototype approximation. It does not replace Samsung One UI's native fold/unfold transition. The overlay approach lets us test the visual effect across other apps before attempting deeper Samsung-specific integration.

### Build
GitHub Actions uses Java 17 and Gradle 8.10.2. The debug APK is uploaded as the `FoldOpenFX-debug` workflow artifact.

### Test
1. Install the debug APK.
2. Grant "Display over other apps".
3. Tap "Enable FoldOpenFX".
4. Open and close the foldable.
5. We can then tune the animation to match the reference video.
