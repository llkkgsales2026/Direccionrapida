package com.luis.direccionrapida

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : Activity() {

    private val claves = listOf(
        "calle", "ave", "avenida", "carr", "carretera", "urb", "bo.", "barrio",
        "pr-", "cond", "sector", "camino", "parcela",
        "caguas", "san juan", "bayamón", "carolina", "gurabo", "cidra",
        "aguas buenas", "san lorenzo", "juncos", "guaynabo", "trujillo alto",
        "cayey", "humacao", "río piedras"
    )
    private lateinit var lista: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        lista = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        scroll.addView(lista)
        setContentView(scroll)
        procesar(intent)
    }

    override fun onNewIntent(nuevo: Intent) {
        super.onNewIntent(nuevo)
        procesar(nuevo)
    }

    @Suppress("DEPRECATION")
    private fun procesar(i: Intent?) {
        lista.removeAllViews()
        val uri: Uri? = if (i?.action == Intent.ACTION_SEND)
            i.getParcelableExtra(Intent.EXTRA_STREAM) else null
        if (uri == null) {
            titulo("Toma captura de la oferta y compártela con Dirección Rápida.")
            return
        }
        titulo("Leyendo captura...")
        try {
            val img = InputImage.fromFilePath(this, uri)
            TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(img)
                .addOnSuccessListener { r ->
                    mostrar(r.textBlocks.flatMap { it.lines }
                        .map { it.text.trim() }
                        .filter { it.length > 3 })
                }
                .addOnFailureListener { titulo("No pude leer la imagen.") }
        } catch (e: Exception) {
            titulo("No pude abrir la imagen.")
        }
    }

    private fun mostrar(lineas: List<String>) {
        lista.removeAllViews()
        val buenas = lineas.filter { l -> claves.any { l.lowercase().contains(it) } }
        titulo("Toca una línea para verla en el mapa:")
        (buenas + lineas.filter { it !in buenas }).forEach { l ->
            lista.addView(Button(this).apply {
                text = l
                isAllCaps = false
                if (l in buenas) setBackgroundColor(0xFF4CAF50.toInt())
                setOnClickListener { abrirMapa(l) }
            })
        }
    }

    private fun abrirMapa(t: String) {
        val q = Uri.encode("$t, Puerto Rico")
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$q")))
    }

    private fun titulo(t: String) {
        lista.addView(TextView(this).apply {
            text = t
            textSize = 20f
            setPadding(0, 0, 0, 24)
        })
    }
}
