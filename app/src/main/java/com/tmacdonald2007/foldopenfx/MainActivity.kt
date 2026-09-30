package com.tmacdonald2007.foldopenfx

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val captureRequest = 401
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(48, 64, 48, 48)
            setBackgroundColor(Color.rgb(8, 9, 12))
        }
        root.addView(TextView(this).apply {
            text = "FoldOpenFX

SCREEN CAPTURE TEST BUILD v0.4.2"
            textSize = 30f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = """

                This version uses Android screen capture to freeze the actual visible screen and animate that image as two folding panels.

                1. Grant overlay permission.
                2. Tap Enable and approve Android's screen-capture prompt.
                3. Leave FoldOpenFX running while testing the hinge animation.

                Some secure/DRM apps may appear black because Android blocks their capture.
            """.trimIndent()
            textSize = 17f
            setTextColor(Color.LTGRAY)
        })
        status = TextView(this).apply {
            text = "Status: waiting"
            textSize = 16f
            setTextColor(Color.YELLOW)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "Grant overlay permission"
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    android.net.Uri.parse("package:$packageName")))
            }
        })
        root.addView(Button(this).apply {
            text = "Enable FoldOpenFX"
            setOnClickListener {
                status.text = "Status: checking overlay permission..."
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    status.text = "Status: overlay permission is NOT granted"
                    Toast.makeText(this@MainActivity, "Grant overlay permission first.", Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val mpm = getSystemService(MediaProjectionManager::class.java)
                startActivityForResult(mpm.createScreenCaptureIntent(), captureRequest)
            }
        })
        root.addView(Button(this).apply {
            text = "Disable FoldOpenFX"
            setOnClickListener {
                stopService(Intent(this@MainActivity, FoldFxService::class.java))
                Toast.makeText(this@MainActivity, "FoldOpenFX disabled.", Toast.LENGTH_SHORT).show()
            }
        })
        setContentView(root)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != captureRequest || resultCode != RESULT_OK || data == null) {
            status.text = "Status: screen capture was not approved (resultCode=$resultCode)"
            Toast.makeText(this, "Screen capture was not approved.", Toast.LENGTH_LONG).show()
            return
        }
        val i = Intent(this, FoldFxService::class.java).apply {
            putExtra(FoldFxService.EXTRA_RESULT_CODE, resultCode)
            putExtra(FoldFxService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        status.text = "Status: screen capture approved; FoldOpenFX service starting"
        Toast.makeText(this, "FoldOpenFX enabled.", Toast.LENGTH_SHORT).show()
    }
}
