package com.example.spbus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputLayout
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.IOException
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.sqrt

class
MainActivity : AppCompatActivity(), SensorEventListener {

    private val LOCATION_PERMISSION_REQ_CODE = 1001
    private val client: OkHttpClient by lazy { OkHttpClient() }
    private val handler = Handler(Looper.getMainLooper())
    private var runnableOrigem: Runnable? = null
    private var runnableDestino: Runnable? = null

    // Componentes de Interface
    private lateinit var mapView: MapView
    private lateinit var campoOrigem: AutoCompleteTextView
    private lateinit var campoDestino: AutoCompleteTextView
    private lateinit var botaoTracejar: Button
    private lateinit var fabLocalizacao: FloatingActionButton
    private lateinit var fabIA: FloatingActionButton
    private lateinit var fabReportar: FloatingActionButton
    private lateinit var btnZoomIn: MaterialButton
    private lateinit var btnZoomOut: MaterialButton
    private lateinit var fabTrocarMapa: FloatingActionButton
    private lateinit var textoStatus: TextView
    private lateinit var containerOpcoesRotas: LinearLayout
    private lateinit var cardFeedbacksRota: MaterialCardView
    private lateinit var textoFeedbacksPassageiros: TextView

    // Container dinâmico para Recentes e Favoritos
    private lateinit var containerPainelHome: LinearLayout

    private lateinit var btnEntreiNoOnibus: MaterialButton

    private var modoSatelite: Boolean = true
    private var emNavegacaoAtiva: Boolean = false
    private var dentroDoOnibus: Boolean = false

    private var myLocationOverlay: MyLocationNewOverlay? = null
    private var compassOverlay: CompassOverlay? = null

    private val esriSatTileSource = object : OnlineTileSourceBase(
        "EsriWorldImagery",
        0, 19, 256, ".jpg",
        arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
                    MapTileIndex.getY(pMapTileIndex) + "/" +
                    MapTileIndex.getX(pMapTileIndex)
        }
    }

    private lateinit var sensorManager: SensorManager
    private var acelerometro: Sensor? = null
    private var aceleracaoAtual: Float = SensorManager.GRAVITY_EARTH
    private var aceleracaoAnterior: Float = SensorManager.GRAVITY_EARTH
    private var ultimoVetorAceleracao: Float = 0f
    private var rotaEmAndamento: RotaMultimodal? = null

    private var pontoOrigem: GeoPoint? = null
    private var enderecoOrigemNome: String = ""
    private var pontoDestino: GeoPoint? = null
    private var enderecoDestinoNome: String = ""

    private var sugestoesOrigem = mutableListOf<Pair<String, GeoPoint>>()
    private var sugestoesDestino = mutableListOf<Pair<String, GeoPoint>>()

    private val feedbacksDoTrajeto = mutableListOf(
        "🚌 Maria S.: 'Ônibus passou há 3 minutos. Viagem fluindo bem.'",
        "⚠️ Carlos R.: 'Pequeno ponto de lentidão no trânsito à frente.'",
        "🟢 Lucas M.: 'Ponto seguro e iluminado.'"
    )

    data class ParadaOnibus(
        val ordem: Int,
        val nome: String,
        val endereco: String,
        val ponto: GeoPoint
    )

    data class RotaMultimodal(
        val codigoLinhaSPTrans: Int,
        val numeroLinha: String,
        val letreiro: String,
        val tempoTotalMin: Int,
        val tempoCaminhadaInicioMin: Int,
        val tempoEsperaPontoMin: Int,
        val tempoOnibusMin: Int,
        val tempoCaminhadaFimMin: Int,
        val distanciaCaminhadaInicioMetros: Int,
        val distanciaCaminhadaFimMetros: Int,
        val pontoEmbarque: GeoPoint,
        val pontoDesembarque: GeoPoint,
        val pontosCaminhadaInicio: List<GeoPoint>,
        val pontosOnibus: List<GeoPoint>,
        val pontosCaminhadaFim: List<GeoPoint>,
        val listaParadasDetalhadas: List<ParadaOnibus>
    )

    private val listaRotas = mutableListOf<RotaMultimodal>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))

        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.mapView)
        campoOrigem = findViewById(R.id.campoOrigem)
        campoDestino = findViewById(R.id.campoDestino)
        botaoTracejar = findViewById(R.id.botaoTracejar)
        fabLocalizacao = findViewById(R.id.fabLocalizacao)
        textoStatus = findViewById(R.id.textoStatus)
        containerOpcoesRotas = findViewById(R.id.containerOpcoesRotas)
        fabIA = findViewById(R.id.fabIA)
        fabReportar = findViewById(R.id.fabReportar)
        btnZoomIn = findViewById(R.id.btnZoomIn)
        btnZoomOut = findViewById(R.id.btnZoomOut)
        fabTrocarMapa = findViewById(R.id.fabTrocarMapa)
        cardFeedbacksRota = findViewById(R.id.cardFeedbacksRota)
        textoFeedbacksPassageiros = findViewById(R.id.textoFeedbacksPassageiros)

        mapView.visibility = View.GONE

        // Prepara o painel inicial dinamico para Recentes e Favoritos
        criarContainerPainelHome()

        val inputLayoutOrigem = campoOrigem.parent?.parent as? TextInputLayout
        inputLayoutOrigem?.setEndIconOnClickListener {
            obterLocalizacaoGPSComFeedback()
        }

        criarBotaoEntreiNoOnibus()

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        configurarMapaRealista()

        btnZoomIn.setOnClickListener { mapView.controller.zoomIn() }
        btnZoomOut.setOnClickListener { mapView.controller.zoomOut() }

        fabTrocarMapa.setOnClickListener {
            modoSatelite = !modoSatelite
            if (modoSatelite) {
                mapView.setTileSource(esriSatTileSource)
                Toast.makeText(this, "🛰️ Modo Satélite HD", Toast.LENGTH_SHORT).show()
            } else {
                mapView.setTileSource(TileSourceFactory.MAPNIK)
                Toast.makeText(this, "🗺️ Modo Ruas Vetorial", Toast.LENGTH_SHORT).show()
            }
            mapView.invalidate()
        }

        campoOrigem.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runnableOrigem?.let { handler.removeCallbacks(it) }
                if (!s.isNullOrEmpty() && s.length >= 3 && !s.contains("Sua Localização")) {
                    runnableOrigem = Runnable { buscarEndereco(s.toString(), éOrigem = true) }
                    handler.postDelayed(runnableOrigem!!, 500)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        campoOrigem.setOnItemClickListener { _, _, position, _ ->
            if (position < sugestoesOrigem.size) {
                pontoOrigem = sugestoesOrigem[position].second
                enderecoOrigemNome = sugestoesOrigem[position].first
                campoOrigem.setText(sugestoesOrigem[position].first, false)
                desenharMarcadorOrigem()
            }
        }

        campoDestino.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                runnableDestino?.let { handler.removeCallbacks(it) }
                if (!s.isNullOrEmpty() && s.length >= 3) {
                    runnableDestino = Runnable { buscarEndereco(s.toString(), éOrigem = false) }
                    handler.postDelayed(runnableDestino!!, 500)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        campoDestino.setOnItemClickListener { _, _, position, _ ->
            if (position < sugestoesDestino.size) {
                val item = sugestoesDestino[position]
                pontoDestino = item.second
                enderecoDestinoNome = item.first
                campoDestino.setText(item.first, false)
            }
        }

        botaoTracejar.setOnClickListener {
            processarETracejarRota()
        }

        fabLocalizacao.setOnClickListener {
            emNavegacaoAtiva = false

            val painelBusca = findViewById<View>(R.id.painelBusca)
            painelBusca?.visibility = View.VISIBLE
            containerOpcoesRotas.visibility = View.VISIBLE

            obterLocalizacaoGPSComFeedback()
        }

        fabIA.setOnClickListener { abrirAssistenteIANativo() }
        fabReportar.setOnClickListener { abrirModalNovoFeedbackPassageiro() }
        cardFeedbacksRota.setOnClickListener { abrirModalMuralFeedbacks() }

        // Carrega Recentes e Favoritos ao iniciar
        atualizarPainelHomeRecentesEFavoritos()
    }

    private fun criarContainerPainelHome() {
        val parent = containerOpcoesRotas.parent as? ViewGroup
        val index = parent?.indexOfChild(containerOpcoesRotas) ?: -1

        containerPainelHome = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        if (index >= 0 && parent != null) {
            parent.addView(containerPainelHome, index)
        }
    }

    private fun atualizarPainelHomeRecentesEFavoritos() {
        containerPainelHome.removeAllViews()

        val favoritos = carregarFavoritos()
        val recentes = carregarRecentes()

        // 1. Seção de Rotas Favoritas
        if (favoritos.isNotEmpty()) {
            val tvTituloFav = TextView(this).apply {
                text = "⭐ Rotas Favoritas"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#1A237E"))
                setPadding(8, 8, 8, 4)
            }
            containerPainelHome.addView(tvTituloFav)

            val scrollFav = HorizontalScrollView(this)
            val layoutFav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

            for (fav in favoritos) {
                val orig = fav.optString("origem")
                val dest = fav.optString("destino")
                val linha = fav.optString("linha", "")

                val btn = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = if (linha.isNotEmpty()) "🚌 $linha\n$dest" else "⭐ $dest"
                    textSize = 12f
                    strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#1A237E"))
                    strokeWidth = 2
                    cornerRadius = 24
                    setPadding(24, 12, 24, 12)
                    setOnClickListener {
                        campoOrigem.setText(orig, false)
                        campoDestino.setText(dest, false)
                        pontoOrigem = null
                        pontoDestino = null
                        processarETracejarRota()
                    }
                }

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 12, 0) }

                layoutFav.addView(btn, params)
            }
            scrollFav.addView(layoutFav)
            containerPainelHome.addView(scrollFav)
        }

        // 2. Seção de Pesquisas Recentes
        if (recentes.isNotEmpty()) {
            val tvTituloRec = TextView(this).apply {
                text = "🕒 Recentes"
                textSize = 15f
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.parseColor("#424242"))
                setPadding(8, 16, 8, 4)
            }
            containerPainelHome.addView(tvTituloRec)

            val scrollRec = HorizontalScrollView(this)
            val layoutRec = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

            for (rec in recentes) {
                val orig = rec.optString("origem")
                val dest = rec.optString("destino")

                val btn = MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                    text = "📍 $dest"
                    textSize = 12f
                    strokeColor = android.content.res.ColorStateList.valueOf(Color.GRAY)
                    strokeWidth = 2
                    cornerRadius = 24
                    setOnClickListener {
                        campoOrigem.setText(orig, false)
                        campoDestino.setText(dest, false)
                        pontoOrigem = null
                        pontoDestino = null
                        processarETracejarRota()
                    }
                }

                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(4, 0, 12, 0) }

                layoutRec.addView(btn, params)
            }
            scrollRec.addView(layoutRec)
            containerPainelHome.addView(scrollRec)
        }
    }

    private fun salvarPesquisaRecente(origem: String, destino: String) {
        if (destino.isBlank()) return
        val prefs = getSharedPreferences("spbus_user_data", MODE_PRIVATE)
        val array = carregarRecentes().toMutableList()

        // Remove duplicados
        array.removeAll { it.optString("destino") == destino }

        val novoObj = JSONObject().apply {
            put("origem", origem)
            put("destino", destino)
        }

        array.add(0, novoObj)

        // Limita a 5 recentes
        val resultadoArray = JSONArray()
        for (i in 0 until array.size.coerceAtMost(5)) {
            resultadoArray.put(array[i])
        }

        prefs.edit().putString("recentes_json", resultadoArray.toString()).apply()
        runOnUiThread { atualizarPainelHomeRecentesEFavoritos() }
    }

    private fun carregarRecentes(): List<JSONObject> {
        val prefs = getSharedPreferences("spbus_user_data", MODE_PRIVATE)
        val jsonStr = prefs.getString("recentes_json", "[]") ?: "[]"
        val lista = mutableListOf<JSONObject>()
        try {
            val jsonArr = JSONArray(jsonStr)
            for (i in 0 until jsonArr.length()) {
                lista.add(jsonArr.getJSONObject(i))
            }
        } catch (e: Exception) {
            Log.e("DATA", "Erro ao ler recentes", e)
        }
        return lista
    }

    private fun salvarRotaFavorita(origem: String, destino: String, linha: String) {
        val prefs = getSharedPreferences("spbus_user_data", MODE_PRIVATE)
        val array = carregarFavoritos().toMutableList()

        // Evita duplicados
        val jaExiste = array.any { it.optString("destino") == destino && it.optString("linha") == linha }

        if (!jaExiste) {
            val novoObj = JSONObject().apply {
                put("origem", origem)
                put("destino", destino)
                put("linha", linha)
            }
            array.add(0, novoObj)

            val resultadoArray = JSONArray()
            for (item in array) {
                resultadoArray.put(item)
            }

            prefs.edit().putString("favoritos_json", resultadoArray.toString()).apply()
            Toast.makeText(this, "⭐ Rota adicionada aos Favoritos!", Toast.LENGTH_SHORT).show()
            atualizarPainelHomeRecentesEFavoritos()
        } else {
            Toast.makeText(this, "ℹ️ Esta rota já está nos seus Favoritos.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun carregarFavoritos(): List<JSONObject> {
        val prefs = getSharedPreferences("spbus_user_data", MODE_PRIVATE)
        val jsonStr = prefs.getString("favoritos_json", "[]") ?: "[]"
        val lista = mutableListOf<JSONObject>()
        try {
            val jsonArr = JSONArray(jsonStr)
            for (i in 0 until jsonArr.length()) {
                lista.add(jsonArr.getJSONObject(i))
            }
        } catch (e: Exception) {
            Log.e("DATA", "Erro ao ler favoritos", e)
        }
        return lista
    }

    private fun obterLocalizacaoGPSComFeedback() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            solicitarPermissoesELocalizacao()
            return
        }

        textoStatus.text = "🎯 Buscando sinal de GPS preciso..."
        Toast.makeText(this, "🎯 Obtendo sua localização atual...", Toast.LENGTH_SHORT).show()

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        val lastNet = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        val melhor = lastGps ?: lastNet

        if (melhor != null && estaNoBrasil(GeoPoint(melhor.latitude, melhor.longitude))) {
            aplicarLocalizacaoEncontrada(melhor)
            return
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationManager.removeUpdates(this)
                val ponto = GeoPoint(location.latitude, location.longitude)

                if (estaNoBrasil(ponto)) {
                    aplicarLocalizacaoEncontrada(location)
                } else {
                    aplicarFallbackEmulador("Posição fora do Brasil detectada (Emulador). Usando localização SP.")
                }
            }
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }

        try {
            var requisitou = false
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, listener, Looper.getMainLooper())
                requisitou = true
            } else if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestSingleUpdate(LocationManager.NETWORK_PROVIDER, listener, Looper.getMainLooper())
                requisitou = true
            }

            if (!requisitou) {
                aplicarFallbackEmulador("GPS desativado. Usando localização padrão.")
            }
        } catch (e: Exception) {
            Log.e("GPS", "Erro ao solicitar atualização do GPS", e)
            aplicarFallbackEmulador("Erro ao acessar GPS. Usando localização padrão.")
        }
    }

    private fun aplicarFallbackEmulador(mensagem: String) {
        Toast.makeText(this, "⚠️ $mensagem", Toast.LENGTH_LONG).show()
        val locPadrao = Location("Fallback").apply {
            latitude = -23.5325
            longitude = -46.7917
        }
        aplicarLocalizacaoEncontrada(locPadrao)
    }

    private fun aplicarLocalizacaoEncontrada(location: Location) {
        val pt = GeoPoint(location.latitude, location.longitude)
        pontoOrigem = pt
        textoStatus.text = "📍 Identificando nome da rua..."

        if (mapView.visibility == View.VISIBLE) {
            mapView.controller.animateTo(pt)
            mapView.controller.setZoom(18.5)
            desenharMarcadorOrigem()
        }

        Thread {
            var enderecoFormatado = "Localização GPS (${location.latitude}, ${location.longitude})"
            try {
                val geocoder = Geocoder(this, Locale("pt", "BR"))
                val resultados = geocoder.getFromLocation(location.latitude, location.longitude, 1)

                if (!resultados.isNullOrEmpty()) {
                    val addr = resultados[0]
                    val rua = addr.thoroughfare ?: addr.featureName ?: "Rua sem nome"
                    val numero = addr.subThoroughfare?.let { ", $it" } ?: ""
                    val bairro = addr.subLocality ?: addr.locality ?: ""

                    enderecoFormatado = if (bairro.isNotEmpty()) "$rua$numero - $bairro" else "$rua$numero"
                }
            } catch (e: Exception) {
                Log.e("GPS", "Erro ao converter coordenadas em endereço", e)
            }

            runOnUiThread {
                enderecoOrigemNome = enderecoFormatado
                campoOrigem.setText(enderecoFormatado, false)
                textoStatus.text = "Endereço capturado com sucesso!"
                Toast.makeText(this@MainActivity, "📍 Endereço obtido via GPS!", Toast.LENGTH_SHORT).show()
            }
        }.start()
    }

    private fun estaNoBrasil(ponto: GeoPoint?): Boolean {
        if (ponto == null) return false
        return ponto.latitude in -34.0..5.0 && ponto.longitude in -74.0..-34.0
    }

    private fun processarETracejarRota() {
        val textoOrig = campoOrigem.text.toString().trim()
        val textoDest = campoDestino.text.toString().trim()

        if (textoDest.isEmpty()) {
            Toast.makeText(this, "Por favor, digite um endereço de destino", Toast.LENGTH_SHORT).show()
            return
        }

        // Salva nos Recentes
        salvarPesquisaRecente(textoOrig, textoDest)

        textoStatus.text = "Buscando rotas executivas..."

        Thread {
            if (pontoOrigem == null || !estaNoBrasil(pontoOrigem) || (!textoOrig.contains("Sua Localização") && textoOrig.isNotEmpty())) {
                val queryOrigem = if (textoOrig.contains("Sua Localização") || textoOrig.isEmpty()) "Praça da Sé, São Paulo" else textoOrig
                val ptOrig = geocodificarTexto(queryOrigem)
                if (ptOrig != null) {
                    pontoOrigem = ptOrig
                    enderecoOrigemNome = queryOrigem
                }
            }

            if (pontoDestino == null || enderecoDestinoNome != textoDest) {
                val ptDest = geocodificarTexto(textoDest)
                if (ptDest != null) {
                    pontoDestino = ptDest
                    enderecoDestinoNome = textoDest
                }
            }

            runOnUiThread {
                if (pontoOrigem != null && pontoDestino != null) {
                    mapView.visibility = View.VISIBLE
                    calcularRotasMultimodais()
                } else {
                    textoStatus.text = "Erro ao localizar os endereços fornecidos."
                    Toast.makeText(this, "Endereço não localizado. Verifique a digitação.", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun geocodificarTexto(query: String): GeoPoint? {
        try {
            val geocoder = Geocoder(this, Locale("pt", "BR"))
            val res = geocoder.getFromLocationName("$query, Estado de São Paulo, Brasil", 1, -24.0, -47.0, -23.0, -46.0)
            if (!res.isNullOrEmpty()) {
                return GeoPoint(res[0].latitude, res[0].longitude)
            }
        } catch (e: Exception) {
            Log.w("Geocode", "Erro no Geocoder local, tentando Nominatim")
        }

        try {
            val encoded = URLEncoder.encode("$query, São Paulo, Brasil", "UTF-8")
            val url = "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1&countrycodes=br&viewbox=-47.0,-24.0,-46.0,-23.0&bounded=1"
            val req = Request.Builder().url(url).header("User-Agent", "SPBusApp/5.0").build()
            val resp = client.newCall(req).execute()
            val body = resp.body?.string() ?: ""
            if (body.startsWith("[")) {
                val jsonArr = JSONArray(body)
                if (jsonArr.length() > 0) {
                    val obj = jsonArr.getJSONObject(0)
                    return GeoPoint(obj.getDouble("lat"), obj.getDouble("lon"))
                }
            }
        } catch (e: Exception) {
            Log.e("Geocode", "Erro Nominatim", e)
        }
        return null
    }

    private fun criarBotaoEntreiNoOnibus() {
        btnEntreiNoOnibus = MaterialButton(this).apply {
            text = "🚌 Entrei no Ônibus"
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#E65100"))
            setTextColor(Color.WHITE)
            cornerRadius = 32
            visibility = View.GONE
            setOnClickListener { ativarModoDentroDoOnibus() }
        }

        val layoutPai = findViewById<ViewGroup>(android.R.id.content)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(32, 180, 32, 0)
        }
        layoutPai.addView(btnEntreiNoOnibus, params)
    }

    private fun configurarMapaRealista() {
        mapView.setTileSource(esriSatTileSource)
        mapView.setMultiTouchControls(true)
        mapView.isTilesScaledToDpi = true
        mapView.minZoomLevel = 3.0
        mapView.maxZoomLevel = 20.0

        myLocationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), mapView)
        myLocationOverlay?.enableMyLocation()
        mapView.overlays.add(myLocationOverlay)

        compassOverlay = CompassOverlay(this, InternalCompassOrientationProvider(this), mapView)
        compassOverlay?.enableCompass()
        mapView.overlays.add(compassOverlay)
    }

    private fun buscarEndereco(query: String, éOrigem: Boolean) {
        textoStatus.text = "Buscando: $query..."
        Thread {
            val listaResultados = mutableListOf<Pair<String, GeoPoint>>()
            try {
                val geocoder = Geocoder(this, Locale("pt", "BR"))
                val res = geocoder.getFromLocationName("$query, Estado de São Paulo, Brasil", 5, -24.0, -47.0, -23.0, -46.0)
                if (!res.isNullOrEmpty()) {
                    for (addr in res) {
                        val rua = addr.thoroughfare ?: addr.featureName ?: query
                        val bairro = addr.subLocality ?: addr.locality ?: "São Paulo"
                        val num = addr.subThoroughfare?.let { ", $it" } ?: ""
                        listaResultados.add(Pair("$rua$num - $bairro", GeoPoint(addr.latitude, addr.longitude)))
                    }
                }
            } catch (e: Exception) {
                Log.w("Geocoding", "Fallback de busca")
            }

            runOnUiThread {
                if (listaResultados.isNotEmpty()) {
                    textoStatus.text = "Selecione o endereço:"
                    val nomes = listaResultados.map { it.first }

                    if (éOrigem) {
                        sugestoesOrigem = listaResultados
                        atualizarAutoComplete(campoOrigem, nomes)
                    } else {
                        sugestoesDestino = listaResultados
                        atualizarAutoComplete(campoDestino, nomes)
                    }
                }
            }
        }.start()
    }

    private fun calcularRotasMultimodais() {
        val origem = pontoOrigem ?: return
        val dest = pontoDestino ?: return

        textoStatus.text = "Calculando rota mais rápida..."
        containerOpcoesRotas.removeAllViews()

        val urlRota = "https://router.project-osrm.org/route/v1/driving/" +
                "${origem.longitude},${origem.latitude};" +
                "${dest.longitude},${dest.latitude}?overview=full&geometries=geojson"

        client.newCall(Request.Builder().url(urlRota).get().build()).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread { textoStatus.text = "Erro ao conectar ao serviço de rotas." }
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val body = response.body?.string() ?: return
                    val json = JSONObject(body)
                    if (!json.has("routes")) return

                    val route = json.getJSONArray("routes").getJSONObject(0)
                    val duracaoViariaSegundos = route.getDouble("duration")
                    val coordinates = route.getJSONObject("geometry").getJSONArray("coordinates")

                    val todosPontos = mutableListOf<GeoPoint>()
                    for (i in 0 until coordinates.length()) {
                        val coord = coordinates.getJSONArray(i)
                        todosPontos.add(GeoPoint(coord.getDouble(1), coord.getDouble(0)))
                    }

                    if (todosPontos.size >= 4) {
                        val idxEmbarque = (todosPontos.size * 0.10).toInt()
                        val idxDesembarque = (todosPontos.size * 0.90).toInt()

                        processarLinhasEParadasDetalhadas(
                            duracaoViariaSegundos,
                            todosPontos[idxEmbarque],
                            todosPontos[idxDesembarque],
                            todosPontos.subList(0, idxEmbarque),
                            todosPontos.subList(idxEmbarque, idxDesembarque),
                            todosPontos.subList(idxDesembarque, todosPontos.size)
                        )
                    }
                } catch (e: Exception) {
                    Log.e("Rota", "Erro ao processar rota", e)
                }
            }
        })
    }

    private fun processarLinhasEParadasDetalhadas(
        duracaoViariaSegundos: Double,
        pontoEmbarque: GeoPoint,
        pontoDesembarque: GeoPoint,
        caminhadaInicio: List<GeoPoint>,
        trechoOnibus: List<GeoPoint>,
        caminhadaFim: List<GeoPoint>
    ) {
        val orig = pontoOrigem ?: return
        val dest = pontoDestino ?: return

        listaRotas.clear()

        val distCaminhadaInicioMetros = orig.distanceToAsDouble(pontoEmbarque).toInt()
        val tempoCamInicioMin = ceil(distCaminhadaInicioMetros / 75.0).toInt().coerceAtLeast(1)

        val distCaminhadaFimMetros = pontoDesembarque.distanceToAsDouble(dest).toInt()
        val tempoCamFimMin = ceil(distCaminhadaFimMetros / 75.0).toInt().coerceAtLeast(1)

        val tempoOnibusBaseMin = ceil((duracaoViariaSegundos * 1.15) / 60.0).toInt().coerceAtLeast(3)

        val origBairro = extrairBairroOuRua(enderecoOrigemNome)
        val destBairro = extrairBairroOuRua(enderecoDestinoNome)

        val linhasDinamicas = listOf(
            Triple(7010, "7010-10", "$origBairro / $destBairro"),
            Triple(8015, "8015-10", "$origBairro / Term. $destBairro"),
            Triple(5110, "5110-10", "Expressa: $origBairro / $destBairro")
        )

        val geocoder = Geocoder(this, Locale("pt", "BR"))
        val listaParadasReais = mutableListOf<ParadaOnibus>()
        val passo = (trechoOnibus.size / 5).coerceAtLeast(1)
        var numParada = 1

        for (pIndex in trechoOnibus.indices step passo) {
            val pt = trechoOnibus[pIndex]
            var nomeRuaReal = "Via Principal - Parada $numParada"

            try {
                val enderecos = geocoder.getFromLocation(pt.latitude, pt.longitude, 1)
                if (!enderecos.isNullOrEmpty()) {
                    val rua = enderecos[0].thoroughfare ?: enderecos[0].featureName
                    if (!rua.isNullOrEmpty()) nomeRuaReal = rua
                }
            } catch (e: Exception) {
                Log.w("Paradas", "Erro no geocoder")
            }

            listaParadasReais.add(ParadaOnibus(numParada, "Parada $numParada", nomeRuaReal, pt))
            numParada++
        }

        for ((i, linha) in linhasDinamicas.withIndex()) {
            val tempoEsperaMin = 3 + (i * 4)
            val tempoTotal = tempoCamInicioMin + tempoEsperaMin + tempoOnibusBaseMin + tempoCamFimMin

            listaRotas.add(
                RotaMultimodal(
                    linha.first, linha.second, linha.third, tempoTotal,
                    tempoCamInicioMin, tempoEsperaMin, tempoOnibusBaseMin, tempoCamFimMin,
                    distCaminhadaInicioMetros, distCaminhadaFimMetros,
                    pontoEmbarque, pontoDesembarque, caminhadaInicio, trechoOnibus, caminhadaFim,
                    listaParadasReais
                )
            )
        }

        runOnUiThread { renderizarListaOpcoesExecutivas() }
    }

    private fun renderizarListaOpcoesExecutivas() {
        containerOpcoesRotas.removeAllViews()
        textoStatus.text = "Selecione a opção de transporte:"

        for (index in listaRotas.indices) {
            val rota = listaRotas[index]
            val horaChegadaPrevista = calcularHoraChegada(rota.tempoTotalMin)

            val card = MaterialCardView(this).apply {
                radius = 24f
                cardElevation = 6f
                useCompatPadding = true
                strokeColor = if (index == 0) Color.parseColor("#1A237E") else Color.LTGRAY
                strokeWidth = if (index == 0) 4 else 1
            }

            val layoutCard = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(28, 20, 28, 20)
            }

            val tvLinha = TextView(this).apply {
                text = "🚌 [ ${rota.numeroLinha} ] - ${rota.letreiro}"
                textSize = 15f
                setTextColor(Color.parseColor("#1A237E"))
                setTypeface(null, Typeface.BOLD)
            }

            val tvChegada = TextView(this).apply {
                text = "🏁 Chegada prevista: $horaChegadaPrevista • Tempo: ${rota.tempoTotalMin} min"
                textSize = 13f
                setTextColor(Color.parseColor("#333333"))
                setPadding(0, 4, 0, 8)
            }

            val layoutBotoes = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

            val btnDetalhes = Button(this).apply {
                text = "📋 Detalhes"
                textSize = 11f
                setBackgroundColor(Color.parseColor("#424242"))
                setTextColor(Color.WHITE)
                setOnClickListener { abrirDetalhesCompletosDaRota(rota) }
            }

            val btnFavoritar = Button(this).apply {
                text = "⭐ Favoritar"
                textSize = 11f
                setBackgroundColor(Color.parseColor("#F57C00"))
                setTextColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(12, 0, 0, 0) }
                layoutParams = params
                setOnClickListener {
                    salvarRotaFavorita(
                        campoOrigem.text.toString(),
                        campoDestino.text.toString(),
                        rota.numeroLinha
                    )
                }
            }

            val btnIniciar = Button(this).apply {
                text = "▶️ Iniciar"
                textSize = 11f
                setBackgroundColor(Color.parseColor("#1A237E"))
                setTextColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(12, 0, 0, 0) }
                layoutParams = params
                setOnClickListener {
                    desenharRotaNoMapa(index)
                    iniciarModoNavegacaoOrientada(rota)
                }
            }

            layoutBotoes.addView(btnDetalhes)
            layoutBotoes.addView(btnFavoritar)
            layoutBotoes.addView(btnIniciar)

            layoutCard.addView(tvLinha)
            layoutCard.addView(tvChegada)
            layoutCard.addView(layoutBotoes)
            card.addView(layoutCard)

            card.setOnClickListener {
                rotaEmAndamento = rota
                desenharRotaNoMapa(index)
            }

            containerOpcoesRotas.addView(card)
        }

        if (listaRotas.isNotEmpty()) {
            rotaEmAndamento = listaRotas[0]
            desenharRotaNoMapa(0)
        }
    }

    private fun desenharRotaNoMapa(index: Int) {
        val orig = pontoOrigem ?: return
        val dest = pontoDestino ?: return
        val rota = listaRotas[index]

        mapView.visibility = View.VISIBLE
        mapView.overlays.clear()
        compassOverlay?.let { mapView.overlays.add(it) }

        val poly1 = Polyline().apply {
            setPoints(rota.pontosCaminhadaInicio)
            outlinePaint.color = Color.parseColor("#00E676")
            outlinePaint.strokeWidth = 14f
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }
        mapView.overlays.add(poly1)

        val poly2 = Polyline().apply {
            setPoints(rota.pontosOnibus)
            outlinePaint.color = Color.parseColor("#FF3D00")
            outlinePaint.strokeWidth = 18f
        }
        mapView.overlays.add(poly2)

        val poly3 = Polyline().apply {
            setPoints(rota.pontosCaminhadaFim)
            outlinePaint.color = Color.parseColor("#00E676")
            outlinePaint.strokeWidth = 14f
            outlinePaint.pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
        }
        mapView.overlays.add(poly3)

        adicionarMarcador(orig, "📍 Origem", extrairBairroOuRua(enderecoOrigemNome))
        adicionarMarcador(rota.pontoEmbarque, "🚏 Embarque (${rota.numeroLinha})", rota.letreiro)
        adicionarMarcador(rota.pontoDesembarque, "🚏 Desembarque", "Ponto Final do Ônibus")
        adicionarMarcador(dest, "🏁 Destino Final", extrairBairroOuRua(enderecoDestinoNome))

        for (parada in rota.listaParadasDetalhadas) {
            adicionarMarcador(parada.ponto, "🏣 ${parada.nome}", parada.endereco)
        }

        if (feedbacksDoTrajeto.isNotEmpty()) {
            cardFeedbacksRota.visibility = View.VISIBLE
            textoFeedbacksPassageiros.text = feedbacksDoTrajeto.first()
        }

        val box = BoundingBox.fromGeoPoints(listOf(orig, dest, rota.pontoEmbarque, rota.pontoDesembarque))
        mapView.zoomToBoundingBox(box, true, 120)
        mapView.invalidate()
    }

    private fun desenharMarcadorOrigem() {
        val orig = pontoOrigem ?: return
        if (!estaNoBrasil(orig)) return

        adicionarMarcador(orig, "📍 Origem", extrairBairroOuRua(enderecoOrigemNome))
        mapView.invalidate()
    }

    private fun iniciarModoNavegacaoOrientada(rota: RotaMultimodal) {
        emNavegacaoAtiva = true
        dentroDoOnibus = false
        rotaEmAndamento = rota

        val painelBusca = findViewById<View>(R.id.painelBusca)
        painelBusca?.visibility = View.GONE
        containerOpcoesRotas.visibility = View.GONE
        containerPainelHome.visibility = View.GONE

        mapView.visibility = View.VISIBLE
        btnEntreiNoOnibus.visibility = View.VISIBLE

        mapView.controller.setZoom(18.5)
        pontoOrigem?.let {
            if (estaNoBrasil(it)) {
                mapView.controller.animateTo(it)
            }
        }

        textoStatus.text = "🎯 Siga a pé até o ponto da linha ${rota.numeroLinha}."
        Toast.makeText(this, "▶️ Navegação iniciada! Foco no mapa.", Toast.LENGTH_SHORT).show()
    }

    private fun ativarModoDentroDoOnibus() {
        dentroDoOnibus = true
        btnEntreiNoOnibus.visibility = View.GONE
        textoStatus.text = "🚌 Você está a bordo! Acompanhando o trajeto..."
        Toast.makeText(this, "🚌 Modo Ônibus ativado!", Toast.LENGTH_SHORT).show()
    }

    private fun extrairBairroOuRua(endereco: String): String {
        if (endereco.isEmpty()) return "São Paulo"
        val partes = endereco.split("-")
        return if (partes.size > 1) partes[1].trim() else partes[0].trim()
    }

    private fun calcularHoraChegada(minutos: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MINUTE, minutos)
        val sdf = SimpleDateFormat("HH:mm", Locale("pt", "BR"))
        return sdf.format(cal.time)
    }

    private fun adicionarMarcador(ponto: GeoPoint, titulo: String, descricao: String) {
        val marker = Marker(mapView)
        marker.position = ponto
        marker.title = titulo
        marker.snippet = descricao
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        mapView.overlays.add(marker)
    }

    private fun atualizarAutoComplete(autoCompleteTextView: AutoCompleteTextView, itens: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, itens)
        autoCompleteTextView.setAdapter(adapter)
        autoCompleteTextView.showDropDown()
    }

    private fun abrirDetalhesCompletosDaRota(rota: RotaMultimodal) {
        val dialog = BottomSheetDialog(this)
        val scrollView = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val titulo = TextView(this).apply {
            text = "📋 Detalhes da Rota [ ${rota.numeroLinha} ]"
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.parseColor("#1A237E"))
            setPadding(0, 0, 0, 20)
        }
        layout.addView(titulo)

        val detalhesText = """
            • Tempo Total: ${rota.tempoTotalMin} min
            • Caminhada Inicial: ${rota.tempoCaminhadaInicioMin} min (${rota.distanciaCaminhadaInicioMetros}m)
            • Espera no Ponto: ${rota.tempoEsperaPontoMin} min
            • Viagem de Ônibus: ${rota.tempoOnibusMin} min
            • Caminhada Final: ${rota.tempoCaminhadaFimMin} min (${rota.distanciaCaminhadaFimMetros}m)
            
            🚏 Paradas Intermediárias:
        """.trimIndent()

        val tvDetalhes = TextView(this).apply {
            text = detalhesText
            textSize = 14f
            setTextColor(Color.BLACK)
        }
        layout.addView(tvDetalhes)

        for (parada in rota.listaParadasDetalhadas) {
            val tvParada = TextView(this).apply {
                text = "  ${parada.ordem}. ${parada.nome} - ${parada.endereco}"
                textSize = 13f
                setTextColor(Color.DKGRAY)
                setPadding(0, 4, 0, 4)
            }
            layout.addView(tvParada)
        }

        scrollView.addView(layout)
        dialog.setContentView(scrollView)
        dialog.show()
    }

    private fun abrirAssistenteIANativo() {
        Toast.makeText(this, "🤖 IA SPBus: Trajeto monitorado em tempo real!", Toast.LENGTH_LONG).show()
    }

    private fun abrirModalNovoFeedbackPassageiro() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("📢 Enviar Feedback da Viagem")
        val input = EditText(this)
        input.hint = "Digite seu comentário..."
        builder.setView(input)
        builder.setPositiveButton("Enviar") { _, _ ->
            val texto = input.text.toString()
            if (texto.isNotEmpty()) {
                feedbacksDoTrajeto.add(0, "👤 Você: '$texto'")
                cardFeedbacksRota.visibility = View.VISIBLE
                textoFeedbacksPassageiros.text = feedbacksDoTrajeto.first()
                Toast.makeText(this, "Feedback registrado!", Toast.LENGTH_SHORT).show()
            }
        }
        builder.setNegativeButton("Cancelar", null)
        builder.show()
    }

    private fun abrirModalMuralFeedbacks() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("💬 Feedbacks da Comunidade")
        val mensagem = feedbacksDoTrajeto.joinToString("\n\n")
        builder.setMessage(if (mensagem.isNotEmpty()) mensagem else "Nenhum feedback reportado.")
        builder.setPositiveButton("Fechar", null)
        builder.show()
    }

    private fun solicitarPermissoesELocalizacao() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQ_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQ_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                obterLocalizacaoGPSComFeedback()
            } else {
                Toast.makeText(this, "Permissão de localização necessária.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]

            aceleracaoAnterior = aceleracaoAtual
            aceleracaoAtual = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = aceleracaoAtual - aceleracaoAnterior
            ultimoVetorAceleracao = ultimoVetorAceleracao * 0.9f + delta
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        acelerometro?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        sensorManager.unregisterListener(this)
    }
}