package com.luis.direccionrapida

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
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

    private val pedirCaptura = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        col.addView(TextView(this).apply {
            text = "Activa el botón flotante. Cuando te salga una oferta en Uber Eats o DoorDash, toca el botón 📍 y te abro la dirección en el mapa."
            textSize = 18f
            setPadding(0, 0, 0, 40)
        })
        col.addView(Button(this).apply {
            text = "Activar botón flotante"
            textSize = 20f
            setOnClickListener { activar() }
        })
        col.addView(Button(this).apply {
            text = "Apagar botón flotante"
            textSize = 18f
            setOnClickListener {
                stopService(Intent(this@MainActivity, BotonFlotanteService::class.java))
                Toast.makeText(this@MainActivity, "Botón apagado", Toast.LENGTH_SHORT).show()
            }
        })
        setContentView(col)
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        manejarCompartido(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        manejarCompartido(intent)
    }

    private fun activar() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Activa el permiso para mostrar sobre otras apps y vuelve", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), pedirCaptura)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pedirCaptura && resultCode == RESULT_OK && data != null) {
            val i = Intent(this, BotonFlotanteService::class.java)
                .putExtra("code", resultCode)
                .putExtra("data", data)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
            moveTaskToBack(true)
        }
    }

    private fun manejarCompartido(i: Intent?) {
        if (i?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            i.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            i.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri == null) return
        val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
        LectorDireccion.leerYAbrir(this, bmp)
    }
}
