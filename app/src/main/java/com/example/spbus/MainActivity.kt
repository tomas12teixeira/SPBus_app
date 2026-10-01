package com.example.spbus

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.spbus.data.FirebaseRouteStore
import com.example.spbus.data.SpTransService
import com.example.spbus.data.ThingSpeakService
import com.example.spbus.data.TransitRoute
import com.example.spbus.data.TransitRouteFinder
import com.example.spbus.data.WalkingDistanceTracker
import com.example.spbus.ui.SpBusApp
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity(), SensorEventListener {

    private val handler = Handler(Looper.getMainLooper())
    private val spTrans = SpTransService()
    private val routeFinder by lazy { TransitRouteFinder(spTrans) }
    private val routeStore by lazy { FirebaseRouteStore(applicationContext) }
    private val thingSpeak by lazy { ThingSpeakService() }
    private lateinit var distanceTracker: WalkingDistanceTracker
    private val distanceMetersFlow = MutableStateFlow(0)
    private lateinit var sensorManager: SensorManager
    private var stepCounter: Sensor? = null
    private var stepDetector: Sensor? = null
    private var accelerometer: Sensor? = null
    private lateinit var mapView: MapView
    private lateinit var originInput: AutoCompleteTextView
    private lateinit var destinationInput: AutoCompleteTextView
    private lateinit var searchButton: MaterialButton
    private lateinit var routeContainer: LinearLayout
    private lateinit var savedContainer: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var distanceText: TextView
    private var selectedRoute: TransitRoute? = null
    private var currentLocation: Location? = null
    private var locationListener: LocationListener? = null
    private var vehiclePoller: Runnable? = null
    private var screenResumed = false
    private val vehicleMarkers = mutableListOf<Marker>()

    private val palette = Palette()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        distanceTracker = WalkingDistanceTracker(this)
        distanceMetersFlow.value = distanceTracker.distanceTodayMeters
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        setContent {
            SpBusApp(
                routeFinder = routeFinder,
                spTrans = spTrans,
                thingSpeak = thingSpeak,
                routeStore = routeStore,
                distanceMeters = distanceMetersFlow,
                hasStepSensor = stepCounter != null || stepDetector != null,
                onEnableStepTracking = { requestStepTrackingPermission() }
            )
        }
        loadSavedRoutes()
    }

    private fun buildScreen(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(palette.surface)
        }

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(8), dp(16), dp(8))
            setBackgroundColor(Color.WHITE)
        }
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL }
        brand.addView(label("SPBus", 21, palette.ink, bold = true))
        brand.addView(label("SÃO PAULO  /  MOBILIDADE", 9, palette.muted, bold = true))
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        distanceText = label("Ativar passos", 12, palette.primary, bold = true).apply {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setBackgroundColor(palette.softGreen)
            setOnClickListener { showDistancePanel() }
        }
        header.addView(distanceText)
        root.addView(header, LinearLayout.LayoutParams(-1, dp(62)))

        val mapFrame = FrameLayout(this)
        mapView = MapView(this).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 10.0
            maxZoomLevel = 19.0
            controller.setZoom(12.5)
            controller.setCenter(GeoPoint(-23.5505, -46.6333))
        }
        mapFrame.addView(mapView, FrameLayout.LayoutParams(-1, -1))
        val locateButton = MaterialButton(this).apply {
            text = "Minha localização"
            isAllCaps = false
            setTextColor(palette.ink)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            elevation = dp(4).toFloat()
            setOnClickListener { requestCurrentLocation() }
        }
        mapFrame.addView(locateButton, FrameLayout.LayoutParams(-2, dp(46), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(12), dp(12), 0)
        })
        root.addView(mapFrame, LinearLayout.LayoutParams(-1, 0, 0.82f))

        val scroll = ScrollView(this).apply {
            clipToPadding = false
            setFillViewport(false)
        }
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(20))
            setBackgroundColor(palette.surface)
        }
        scroll.addView(panel)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1.18f))

        panel.addView(label("Planeje sua viagem", 20, palette.ink, bold = true))
        statusText = label("Linhas oficiais SPTrans, direto ao ponto.", 12, palette.muted).apply {
            setPadding(0, dp(4), 0, dp(14))
        }
        panel.addView(statusText)

        val searchCard = MaterialCardView(this).apply {
            radius = dp(12).toFloat()
            cardElevation = dp(1).toFloat()
            strokeWidth = dp(1)
            strokeColor = palette.border
            setCardBackgroundColor(Color.WHITE)
            setContentPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val searchFields = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        originInput = addressField("De onde voce sai?")
        destinationInput = addressField("Para onde voce vai?")
        searchFields.addView(inputLayout(originInput), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        searchFields.addView(inputLayout(destinationInput), LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        searchButton = MaterialButton(this).apply {
            text = "Buscar rotas de ônibus"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(palette.primary)
            setOnClickListener { searchRoutes() }
        }
        searchFields.addView(searchButton, LinearLayout.LayoutParams(-1, dp(48)))
        searchCard.addView(searchFields)
        panel.addView(searchCard)

        val shortcuts = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val shortcutRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(8))
        }
        val favoriteShortcut = textButton("Favoritos", { loadSavedRoutes(favoritesOnly = true) })
        val historyShortcut = textButton("Recentes", { loadSavedRoutes(favoritesOnly = false) })
        shortcutRow.addView(favoriteShortcut, spacedButtonParams())
        shortcutRow.addView(historyShortcut, spacedButtonParams())
        shortcuts.addView(shortcutRow)
        panel.addView(shortcuts)

        savedContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(savedContainer)
        panel.addView(label("MELHORES OPÇÕES", 10, palette.muted, bold = true).apply {
            setPadding(0, dp(14), 0, dp(8))
        })
        routeContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        routeContainer.addView(emptyRoutesMessage())
        panel.addView(routeContainer)

        return root
    }

    private fun addressField(hint: String) = AutoCompleteTextView(this).apply {
        setSingleLine(true)
        textSize = 14f
        setTextColor(palette.ink)
        setHintTextColor(palette.muted)
        this.hint = hint
        threshold = 2
        setPadding(dp(12), dp(12), dp(12), dp(12))
    }

    private fun inputLayout(field: AutoCompleteTextView) = TextInputLayout(this).apply {
        hint = field.hint
        field.hint = null
        boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
        boxStrokeColor = palette.border
        setBoxCornerRadii(dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat(), dp(8).toFloat())
        addView(field, LinearLayout.LayoutParams(-1, -2))
    }

    private fun searchRoutes() {
        val origin = originInput.text.toString().trim()
        val destination = destinationInput.text.toString().trim()
        if (origin.isBlank() || destination.isBlank()) {
            Toast.makeText(this, "Informe origem e destino para comparar as linhas.", Toast.LENGTH_SHORT).show()
            return
        }
        searchButton.isEnabled = false
        statusText.text = "Consultando paradas e linhas oficiais..."
        routeContainer.removeAllViews()
        routeContainer.addView(progressMessage("Encontrando linhas diretas e estimando o trajeto"))
        routeFinder.findDirectRoutes(origin, destination) { routes, error ->
            runOnUiThread {
                searchButton.isEnabled = true
                routeContainer.removeAllViews()
                if (routes.isEmpty()) {
                    statusText.text = error ?: "Nenhuma rota encontrada."
                    routeContainer.addView(emptyRoutesMessage(error))
                    return@runOnUiThread
                }
                statusText.text = "${routes.size} alternativa${if (routes.size == 1) "" else "s"} · selecione para consultar a previsão SPTrans"
                renderRoutes(routes)
                selectRoute(routes.first(), persistHistory = true)
            }
        }
    }

    private fun renderRoutes(routes: List<TransitRoute>) {
        routeContainer.removeAllViews()
        routes.forEachIndexed { index, route ->
            val card = MaterialCardView(this).apply {
                radius = dp(10).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                strokeColor = palette.border
                setCardBackgroundColor(Color.WHITE)
                setContentPadding(dp(14), dp(12), dp(14), dp(12))
                setOnClickListener { selectRoute(route, persistHistory = true) }
            }
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val headline = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
            val badge = label(route.line.number, 14, Color.WHITE, bold = true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(10), dp(7), dp(10), dp(7))
                setBackgroundColor(if (index == 0) palette.primary else palette.ink)
            }
            headline.addView(badge)
            headline.addView(label("  ${route.stops.size} paradas", 12, palette.muted), LinearLayout.LayoutParams(0, -2, 1f))
            column.addView(headline)
            column.addView(label(route.line.destination.ifBlank { route.alightingStop.name }, 13, palette.ink, bold = true).apply {
                setPadding(0, dp(9), 0, dp(3))
            })
            column.addView(label("Embarque: ${route.boardingStop.name}", 11, palette.muted))
            column.addView(label("Desembarque: ${route.alightingStop.name}", 11, palette.muted).apply {
                setPadding(0, dp(3), 0, 0)
            })
            val actions = LinearLayout(this).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }
            actions.addView(textButton("Ver no mapa") { selectRoute(route, persistHistory = true) })
            actions.addView(textButton("Salvar") { saveFavorite(route) })
            column.addView(actions)
            card.addView(column)
            routeContainer.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
        }
    }

    private fun selectRoute(route: TransitRoute, persistHistory: Boolean) {
        selectedRoute = route
        drawRoute(route)
        if (persistHistory) {
            routeStore.saveRoute(
                originInput.text.toString().trim(),
                destinationInput.text.toString().trim(),
                route.line.number,
                favorite = false
            ) { error -> if (error != null) android.util.Log.w("Firebase", error) }
        }
        spTrans.arrivals(route.boardingStop.code, route.line.code) { arrivals, _ ->
            val wait = arrivals?.firstOrNull()?.let(::minutesUntilArrival)
            runOnUiThread {
                val waitLabel = wait?.let { "Previsão SPTrans: ${it} min" } ?: "SPTrans sem previsão para este ponto agora"
                statusText.text = "Linha ${route.line.number} · $waitLabel"
            }
        }
        startVehicleTracking(route)
    }

    private fun drawRoute(route: TransitRoute) {
        mapView.overlays.removeAll { it is Marker || it is Polyline }
        val line = Polyline().apply {
            setPoints(route.stops.map { GeoPoint(it.latitude, it.longitude) })
            outlinePaint.color = palette.primary
            outlinePaint.strokeWidth = dp(5).toFloat()
        }
        mapView.overlays.add(line)
        addStopMarker(route.boardingStop, "Embarque · ${route.line.number}")
        addStopMarker(route.alightingStop, "Desembarque")
        val points = route.stops.map { GeoPoint(it.latitude, it.longitude) }
        if (points.isNotEmpty()) {
            mapView.controller.setCenter(points[points.size / 2])
            mapView.controller.setZoom(14.5)
        }
        mapView.invalidate()
    }

    private fun addStopMarker(stop: com.example.spbus.data.SpTransStop, title: String) {
        mapView.overlays.add(Marker(mapView).apply {
            position = GeoPoint(stop.latitude, stop.longitude)
            this.title = title
            snippet = stop.name
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        })
    }

    private fun startVehicleTracking(route: TransitRoute) {
        vehiclePoller?.let(handler::removeCallbacks)
        val poller = object : Runnable {
            override fun run() {
                if (!screenResumed || selectedRoute?.line?.code != route.line.code) return
                spTrans.vehiclesForLine(route.line.code) { vehicles, error ->
                    runOnUiThread {
                        vehicleMarkers.forEach(mapView.overlays::remove)
                        vehicleMarkers.clear()
                        vehicles.orEmpty().forEach { vehicle ->
                            val marker = Marker(mapView).apply {
                                position = GeoPoint(vehicle.latitude, vehicle.longitude)
                                title = "Onibus ${vehicle.prefix}"
                                snippet = "Atualizacao ${vehicle.updatedAt}"
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            vehicleMarkers.add(marker)
                            mapView.overlays.add(marker)
                        }
                        if (!error.isNullOrBlank()) statusText.text = "Veiculos ao vivo indisponiveis; rota continua no mapa."
                        else if (vehicles.isNullOrEmpty()) statusText.text = "Linha ${route.line.number} · sem posicoes de veiculo agora."
                        else statusText.text = "Linha ${route.line.number} · ${vehicles.size} onibus ao vivo no mapa."
                        mapView.invalidate()
                        if (screenResumed && selectedRoute?.line?.code == route.line.code) handler.postDelayed(this, VEHICLE_REFRESH_MS)
                    }
                }
            }
        }
        vehiclePoller = poller
        poller.run()
    }

    private fun configureStopSuggestions(field: AutoCompleteTextView) {
        var pending: Runnable? = null
        field.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                pending?.let(handler::removeCallbacks)
                val query = s?.toString()?.trim().orEmpty()
                if (query.length < 3) return
                val task = Runnable {
                    spTrans.searchStops(query) { stops, _ ->
                        val suggestions = stops.orEmpty().take(5).map { it.name.ifBlank { it.address } }
                        runOnUiThread {
                            if (suggestions.isNotEmpty() && field.hasFocus()) {
                                field.setAdapter(ArrayAdapter(this@MainActivity, android.R.layout.simple_dropdown_item_1line, suggestions))
                                field.showDropDown()
                            }
                        }
                    }
                }
                pending = task
                handler.postDelayed(task, 450)
            }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
    }

    private fun requestCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_REQUEST
            )
            return
        }
        val manager = getSystemService(LOCATION_SERVICE) as LocationManager
        val lastKnown = runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?: runCatching { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
        if (lastKnown != null) {
            useLocation(lastKnown)
            return
        }
        statusText.text = "Obtendo sua localização..."
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            statusText.text = "Ative a localização do aparelho e tente novamente."
            return
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                locationListener?.let { manager.removeUpdates(it) }
                locationListener = null
                useLocation(location)
            }
            @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }
        locationListener = listener
        try {
            manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            handler.postDelayed({
                if (locationListener === listener) {
                    manager.removeUpdates(listener)
                    locationListener = null
                    statusText.text = "A localização demorou. Digite a origem manualmente."
                }
            }, LOCATION_TIMEOUT_MS)
        } catch (_: SecurityException) {
            statusText.text = "Permita o acesso à localização para usar esta função."
        }
    }

    private fun useLocation(location: Location) {
        currentLocation = location
        val point = GeoPoint(location.latitude, location.longitude)
        mapView.controller.animateTo(point)
        mapView.controller.setZoom(15.0)
        val marker = Marker(mapView).apply {
            position = point
            title = "Sua localizacao"
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        }
        mapView.overlays.removeAll { it is Marker && it.title == "Sua localizacao" }
        mapView.overlays.add(marker)
        mapView.invalidate()
        statusText.text = "Localização obtida; identificando o endereço..."
        Thread {
            val address = runCatching {
                @Suppress("DEPRECATION")
                Geocoder(this, Locale("pt", "BR")).getFromLocation(location.latitude, location.longitude, 1)
                    ?.firstOrNull()?.let { result ->
                        listOfNotNull(result.thoroughfare, result.subLocality, result.locality).distinct().joinToString(", ")
                    }
            }.getOrNull().orEmpty()
            runOnUiThread {
                if (address.isNotBlank()) {
                    originInput.setText(address)
                    statusText.text = "Origem preenchida pela sua localização."
                } else {
                    statusText.text = "Não consegui identificar a rua. Informe a origem manualmente."
                }
            }
        }.start()
    }

    private fun loadSavedRoutes(favoritesOnly: Boolean = false) {
        if (!::savedContainer.isInitialized) return
        savedContainer.removeAllViews()
        val store = routeStore
        if (!store.isConfigured) {
            savedContainer.addView(label("Configure Firebase para sincronizar favoritos e recentes entre aparelhos.", 11, palette.muted))
            return
        }
        store.loadRoutes(favoritesOnly) { routes, error ->
            runOnUiThread {
                savedContainer.removeAllViews()
                if (error != null) {
                    savedContainer.addView(label("Firebase indisponível. Confira autenticação anônima e regras do Firestore.", 11, palette.muted))
                    return@runOnUiThread
                }
                if (routes.isEmpty()) {
                    savedContainer.addView(label(if (favoritesOnly) "Seus favoritos salvos aparecerão aqui." else "Suas viagens recentes aparecerão aqui.", 11, palette.muted))
                    return@runOnUiThread
                }
                routes.forEach { saved ->
                    savedContainer.addView(textButton("${saved.line}  ·  ${saved.origin} → ${saved.destination}") {
                        originInput.setText(saved.origin)
                        destinationInput.setText(saved.destination)
                    })
                }
            }
        }
    }

    private fun saveFavorite(route: TransitRoute) {
        routeStore.saveRoute(
            originInput.text.toString().trim(),
            destinationInput.text.toString().trim(),
            route.line.number,
            favorite = true
        ) { error ->
            runOnUiThread {
                Toast.makeText(this, error ?: "Rota salva nos favoritos.", Toast.LENGTH_SHORT).show()
                if (error == null) loadSavedRoutes(favoritesOnly = true)
            }
        }
    }

    private fun showDistancePanel() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(18), dp(24), dp(20))
        }
        content.addView(label("Caminhada de hoje", 18, palette.ink, bold = true))
        content.addView(label("${distanceTracker.distanceTodayMeters} m", 30, palette.primary, bold = true).apply {
            setPadding(0, dp(8), 0, dp(4))
        })
        content.addView(label("Medição local pelo sensor de passos.", 12, palette.muted))
        content.addView(MaterialButton(this).apply {
            text = "Ativar contagem de passos"
            isAllCaps = false
            setOnClickListener { requestStepTrackingPermission() }
        })
        val status = label("Consultando canal ThingSpeak...", 12, palette.muted).apply {
            setPadding(0, dp(12), 0, dp(8))
        }
        content.addView(status)
        val fieldTitle = label("Campo do canal", 14, palette.ink, bold = true)
        val chartContainer = FrameLayout(this)
        val history = label("Sem leituras carregadas.", 11, palette.muted).apply {
            setPadding(0, dp(8), 0, 0)
        }
        content.addView(fieldTitle)
        content.addView(chartContainer, LinearLayout.LayoutParams(-1, dp(180)))
        content.addView(history)
        content.addView(MaterialButton(this).apply {
            text = "Atualizar telemetria"
            isAllCaps = false
            setOnClickListener { loadThingSpeakData(status, fieldTitle, chartContainer, history) }
        })
        AlertDialog.Builder(this).setView(content).setPositiveButton("Fechar", null).show()
        loadThingSpeakData(status, fieldTitle, chartContainer, history)
    }

    private fun loadThingSpeakData(status: TextView, fieldTitle: TextView, chart: FrameLayout, history: TextView) {
        status.text = "Consultando canal ThingSpeak..."
        thingSpeak.loadChannelData { feed, error ->
            runOnUiThread {
                if (error != null || feed == null) {
                    status.text = error ?: "Não foi possível ler o canal."
                    fieldTitle.text = "Campo do canal"
                    chart.removeAllViews()
                    history.text = ""
                    return@runOnUiThread
                }
                val field = feed.fields.firstOrNull()
                status.text = "${feed.channelName} · canal ${feed.channelId} · ${feed.readings.size} registros"
                if (field == null) {
                    fieldTitle.text = "Nenhum campo configurado no canal."
                    chart.removeAllViews()
                    history.text = "Adicione e nomeie um campo no ThingSpeak para visualizar os dados."
                    return@runOnUiThread
                }
                fieldTitle.text = "Field ${field.number} · ${field.label}"
                val values = feed.readings.mapNotNull { it.fields[field.number]?.toFloat() }
                chart.removeAllViews()
                if (values.isEmpty()) {
                    chart.visibility = View.GONE
                    history.text = "${feed.readings.size} registros, mas sem valor numérico neste campo."
                } else {
                    chart.visibility = View.VISIBLE
                    chart.addView(createTelemetryChart(values), FrameLayout.LayoutParams(-1, -1))
                    val latest = feed.readings.lastOrNull()
                    history.text = latest?.let { "Última leitura: ${it.fields[field.number]} · ${it.createdAt}" }.orEmpty()
                }
            }
        }
    }

    private fun createTelemetryChart(values: List<Float>): View = object : View(this) {
        private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.primary
            strokeWidth = dp(2).toFloat()
            style = Paint.Style.STROKE
        }
        private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = palette.border
            strokeWidth = dp(1).toFloat()
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val left = dp(8).toFloat()
            val right = width - dp(8).toFloat()
            val top = dp(12).toFloat()
            val bottom = height - dp(12).toFloat()
            repeat(4) { index ->
                val y = top + (bottom - top) * index / 3f
                canvas.drawLine(left, y, right, y, gridPaint)
            }
            if (values.isEmpty()) return
            val minimum = values.minOrNull() ?: return
            val maximum = values.maxOrNull() ?: return
            val span = (maximum - minimum).takeIf { it > 0f } ?: 1f
            path.reset()
            values.forEachIndexed { index, value ->
                val x = if (values.size == 1) (left + right) / 2 else left + (right - left) * index / (values.size - 1)
                val y = bottom - (value - minimum) / span * (bottom - top)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            canvas.drawPath(path, linePaint)
        }
    }

    private fun updateDistanceLabel() {
        if (::distanceText.isInitialized) distanceText.text = "${distanceTracker.distanceTodayMeters} m a pé"
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val sensorEvent = event ?: return
        when (sensorEvent.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> distanceTracker.recordCumulativeSensorSteps(sensorEvent.values.firstOrNull()?.toLong() ?: return)
            Sensor.TYPE_STEP_DETECTOR -> distanceTracker.recordDetectedSteps(sensorEvent.values.firstOrNull()?.toInt() ?: 1)
            Sensor.TYPE_ACCELEROMETER -> if (stepCounter == null && stepDetector == null) {
                distanceTracker.recordAccelerometer(
                    sensorEvent.values[0], sensorEvent.values[1], sensorEvent.values[2], sensorEvent.timestamp / 1_000_000L
                )
            }
        }
        updateDistanceLabel()
        distanceMetersFlow.value = distanceTracker.distanceTodayMeters
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onResume() {
        super.onResume()
        screenResumed = true
        if (::mapView.isInitialized) mapView.onResume()
        registerStepSensorsIfAllowed()
        updateDistanceLabel()
        distanceMetersFlow.value = distanceTracker.distanceTodayMeters
        selectedRoute?.let(::startVehicleTracking)
    }

    override fun onPause() {
        screenResumed = false
        vehiclePoller?.let(handler::removeCallbacks)
        locationListener?.let { listener -> (getSystemService(LOCATION_SERVICE) as LocationManager).removeUpdates(listener) }
        locationListener = null
        if (::distanceTracker.isInitialized) {
            distanceTracker.persist()
        }
        if (::mapView.isInitialized) mapView.onPause()
        if (::sensorManager.isInitialized) sensorManager.unregisterListener(this)
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_REQUEST -> {
                if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) requestCurrentLocation()
                else statusText.text = "Localização negada; informe a origem manualmente."
            }
            RECOGNITION_REQUEST -> {
                if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) registerStepSensorsIfAllowed()
                else Toast.makeText(this, "Contagem de passos desativada; voce ainda pode planejar rotas.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun requestStepTrackingPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION), RECOGNITION_REQUEST)
        } else {
            registerStepSensorsIfAllowed()
            Toast.makeText(this, "Contagem de passos ativada.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerStepSensorsIfAllowed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED
        ) return
        stepCounter?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (stepCounter == null) stepDetector?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (stepCounter == null && stepDetector == null) accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun minutesUntilArrival(time: String): Int? {
        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        val calendar = java.util.Calendar.getInstance()
        val now = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        return ((hour * 60 + minute - now + 1440) % 1440).coerceAtMost(90)
    }

    private fun emptyRoutesMessage(message: String? = null) = label(
        message ?: "Informe origem e destino para ver linhas, pontos de embarque e previsão.",
        12,
        palette.muted
    ).apply {
        setPadding(dp(4), dp(8), dp(4), dp(8))
    }

    private fun progressMessage(message: String) = label(message, 12, palette.muted).apply {
        setPadding(dp(4), dp(14), dp(4), dp(14))
    }

    private fun textButton(text: String, action: () -> Unit) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTextColor(palette.primary)
        setPadding(dp(8), dp(8), dp(8), dp(8))
        setOnClickListener { action() }
    }

    private fun spacedButtonParams() = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(12) }

    private fun label(text: String, size: Int, color: Int, bold: Boolean = false) = TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size.toFloat())
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private data class Palette(
        val primary: Int = Color.rgb(23, 107, 88),
        val ink: Int = Color.rgb(23, 37, 33),
        val muted: Int = Color.rgb(100, 115, 109),
        val surface: Int = Color.rgb(244, 246, 242),
        val border: Int = Color.rgb(224, 230, 224),
        val softGreen: Int = Color.rgb(225, 239, 232)
    )

    companion object {
        private const val LOCATION_REQUEST = 2304
        private const val RECOGNITION_REQUEST = 2305
        private const val LOCATION_TIMEOUT_MS = 15_000L
        private const val VEHICLE_REFRESH_MS = 30_000L
    }
}
