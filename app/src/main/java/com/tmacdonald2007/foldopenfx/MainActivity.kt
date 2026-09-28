package com.tmacdonald2007.foldopenfx

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(48, 64, 48, 48)
            setBackgroundColor(Color.rgb(8, 9, 12))
        }

        root.addView(TextView(this).apply {
            text = "FoldOpenFX"
            textSize = 30f
            setTextColor(Color.WHITE)
        })

        root.addView(TextView(this).apply {
            text = """
                
                Prototype for a foldable opening/closing visual effect.
                
                The service watches the hinge-angle sensor and draws a lightweight center-crease overlay while the phone is opening or closing.
            """.trimIndent()

            textSize = 17f
            setTextColor(Color.LTGRAY)
        })

        root.addView(Button(this).apply {
            text = "Grant overlay permission"

            setOnClickListener {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        })

        root.addView(Button(this).apply {
            text = "Enable FoldOpenFX"

            setOnClickListener {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(
                        this@MainActivity,
                        "Grant overlay permission first.",
                        Toast.LENGTH_LONG
                    ).show()

                    return@setOnClickListener
                }

                if (
                    Build.VERSION.SDK_INT >= 33 &&
                    checkSelfPermission(
                        Manifest.permission.POST_NOTIFICATIONS
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissions(
                        arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                        100
                    )
                }

                val intent = Intent(
                    this@MainActivity,
                    FoldFxService::class.java
                )

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }

                Toast.makeText(
                    this@MainActivity,
                    "FoldOpenFX enabled.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        root.addView(Button(this).apply {
            text = "Disable FoldOpenFX"

            setOnClickListener {
                stopService(
                    Intent(
                        this@MainActivity,
                        FoldFxService::class.java
                    )
                )

                Toast.makeText(
                    this@MainActivity,
                    "FoldOpenFX disabled.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })

        setContentView(root)
    }
}
