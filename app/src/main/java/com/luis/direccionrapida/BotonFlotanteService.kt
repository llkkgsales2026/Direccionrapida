package com.luis.direccionrapida

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs

object LectorDireccion {
    private val claves = Regex(
        "\\b(calle|c/|ave|avenida|carr|carretera|urb|bo|barrio|res|cond|st|street|rd|road|dr|drive|ln|blvd|pr)\\b",
        RegexOption.IGNORE_CASE
    )

    fun leerYAbrir(ctx: Context, bmp: Bitmap) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            .process(InputImage.fromBitmap(bmp, 0))
            .addOnSuccessListener { r ->
                val lineas = r.textBlocks.flatMap { it.lines }.map { it.text.trim() }.filter { it.length > 3 }
                val dir = lineas.filter { claves.containsMatchIn(it) }.take(2).joinToString(", ")
                if (dir.isBlank()) {
                    Toast.makeText(ctx, "No encontré una dirección", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                Toast.makeText(ctx, "Buscando: $dir", Toast.LENGTH_SHORT).show()
                val i = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode("$dir, Puerto Rico")))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try {
                    ctx.startActivity(i)
                } catch (e: Exception) {
                    Toast.makeText(ctx, "No pude abrir el mapa", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(ctx, "No pude leer la pantalla", Toast.LENGTH_SHORT).show()
            }
    }
}

class BotonFlotanteService : Service() {

    private var proyeccion: MediaProjection? = null
    private var pantalla: VirtualDisplay? = null
    private var lector: ImageReader? = null
    private var ultima: Image? = null
    private var boton: View? = null
    private var wm: WindowManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        iniciarNotificacion()
        val code = intent?.getIntExtra("code", 0) ?: 0
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }
        if (data == null || proyeccion != null) return START_NOT_STICKY
        val mpm = getSystemService(MediaProjectionManager::class.java)
        proyeccion = mpm.getMediaProjection(code, data)
        proyeccion?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopSelf() }
        }, Handler(Looper.getMainLooper()))
        prepararCaptura()
        mostrarBoton()
        return START_NOT_STICKY
    }

    private fun iniciarNotificacion() {
        val n: Notification = if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("boton", "Botón flotante", NotificationManager.IMPORTANCE_LOW))
            Notification.Builder(this, "boton")
                .setContentTitle("Dirección Rápida activa")
                .setContentText("Toca el botón 📍 para ver la dirección")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Dirección Rápida activa")
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .build()
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, n)
        }
    }

    private fun prepararCaptura() {
        val m = resources.displayMetrics
        val r = ImageReader.newInstance(m.widthPixels, m.heightPixels, PixelFormat.RGBA_8888, 3)
        r.setOnImageAvailableListener({ reader ->
            val nueva = try { reader.acquireLatestImage() } catch (e: Exception) { null }
            if (nueva != null) {
                ultima?.close()
                ultima = nueva
            }
        }, Handler(Looper.getMainLooper()))
        lector = r
        pantalla = proyeccion?.createVirtualDisplay(
            "captura", m.widthPixels, m.heightPixels, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, r.surface, null, null
        )
    }

    private fun capturar() {
        val img = ultima
        if (img == null) {
            Toast.makeText(this, "Intenta otra vez", Toast.LENGTH_SHORT).show()
            return
        }
        val plano = img.planes[0]
        val buffer = plano.buffer
        buffer.rewind()
        val anchoFila = plano.rowStride / plano.pixelStride
        val completo = Bitmap.createBitmap(anchoFila, img.height, Bitmap.Config.ARGB_8888)
        completo.copyPixelsFromBuffer(buffer)
        val bmp = Bitmap.createBitmap(completo, 0, 0, img.width, img.height)
        LectorDireccion.leerYAbrir(this, bmp)
    }

    private fun mostrarBoton() {
        val w = getSystemService(WINDOW_SERVICE) as WindowManager
        wm = w
        val b = TextView(this).apply {
            text = "📍"
            textSize = 30f
            gravity = Gravity.CENTER
            setBackgroundColor(0xCC1565C0.toInt())
            setPadding(30, 20, 30, 20)
        }
        val tipo = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            tipo,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 20
            y = 400
        }
        var ix = 0
        var iy = 0
        var tx = 0f
        var ty = 0f
        var movio = false
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = lp.x; iy = lp.y; tx = e.rawX; ty = e.rawY; movio = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - tx).toInt()
                    val dy = (e.rawY - ty).toInt()
                    if (abs(dx) > 15 || abs(dy) > 15) movio = true
                    lp.x = ix + dx
                    lp.y = iy + dy
                    w.updateViewLayout(v, lp)
                }
                MotionEvent.ACTION_UP -> {
                    if (!movio) capturar()
                }
            }
            true
        }
        w.addView(b, lp)
        boton = b
    }

    override fun onDestroy() {
        boton?.let { wm?.removeView(it) }
        boton = null
        ultima?.close()
        pantalla?.release()
        proyeccion?.stop()
        proyeccion = null
        super.onDestroy()
    }
}
