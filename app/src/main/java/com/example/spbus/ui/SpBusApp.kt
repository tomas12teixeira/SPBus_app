package com.example.spbus.ui

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.spbus.data.FirebaseRouteStore
import com.example.spbus.data.AddressSuggestion
import com.example.spbus.data.NominatimService
import com.example.spbus.data.SavedTransitRoute
import com.example.spbus.data.SpTransVehicle
import com.example.spbus.data.ThingSpeakFeed
import com.example.spbus.data.ThingSpeakService
import com.example.spbus.data.SpTransService
import com.example.spbus.data.TransitRoute
import com.example.spbus.data.TransitRouteFinder
import com.example.spbus.assistant.AssistantEngine
import com.example.spbus.assistant.AssistantContext
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import androidx.core.content.ContextCompat
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import java.util.Locale
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive

private val busGreen = Color(0xFF176B58)
private val appSurface = Color(0xFFF4F6F2)
private val appInk = Color(0xFF172521)
private val appMuted = Color(0xFF64736D)

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SpBusApp(
    routeFinder: TransitRouteFinder,
    spTrans: SpTransService,
    thingSpeak: ThingSpeakService,
    routeStore: FirebaseRouteStore,
    distanceMeters: StateFlow<Int>,
    hasStepSensor: Boolean,
    onEnableStepTracking: () -> Unit
) {
    MaterialTheme(colorScheme = lightColorScheme(
        primary = busGreen,
        secondary = busGreen,
        secondaryContainer = busGreen,
        onSecondaryContainer = Color.White,
        background = appSurface,
        surface = Color.White
    )) {
        val navController = rememberNavController()
        val planner: RoutePlannerViewModel = viewModel(factory = RoutePlannerViewModel.Factory(routeFinder, spTrans))
        val tabs = listOf(
            AppTab("home", "Mapa"),
            AppTab("favorites", "Salvos"),
            AppTab("iot", "IoT"),
            AppTab("assistant", "Ajuda"),
            AppTab("profile", "Perfil")
        )
        val entry by navController.currentBackStackEntryAsState()
        val currentDestination = entry?.destination

        Scaffold(
            containerColor = appSurface,
            topBar = {
                TopAppBar(
                    title = { Text("SPBus", color = appInk) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
            },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.route == tab.route } == true,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {},
                            label = { Text(tab.label) }
                        )
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = "home",
                modifier = Modifier.padding(padding)
            ) {
                composable("home") {
                    HomeScreen(planner, routeStore)
                }
                composable("favorites") {
                    SavedRoutesScreen(routeStore)
                }
                composable("iot") {
                    TelemetryScreen(thingSpeak, distanceMeters, hasStepSensor, onEnableStepTracking)
                }
                composable("assistant") {
                    AssistantScreen(planner)
                }
                composable("profile") {
                    ProfileScreen(routeStore) { navController.navigate("feedback") }
                }
                composable("feedback") {
                    FeedbackEntryScreen(routeStore)
                }
            }
        }
    }
}

private data class AppTab(val route: String, val label: String)

data class PlannerUiState(
    val isLoading: Boolean = false,
    val routes: List<TransitRoute> = emptyList(),
    val selectedRoute: TransitRoute? = null,
    val status: String = "Informe origem e destino para consultar linhas oficiais.",
    val arrival: String = "",
    val arrivalMinutes: Int? = null,
    val origin: String = "",
    val destination: String = "",
    val vehicles: List<SpTransVehicle> = emptyList(),
    val vehicleStatus: String = ""
)

class RoutePlannerViewModel(private val finder: TransitRouteFinder, private val spTrans: SpTransService) : ViewModel() {
    private val mutableState = MutableStateFlow(PlannerUiState())
    val state = mutableState.asStateFlow()
    private var vehicleRefreshJob: Job? = null

    fun search(origin: String, destination: String) {
        if (origin.isBlank() || destination.isBlank()) {
            mutableState.update { it.copy(status = "Informe origem e destino.") }
            return
        }
        stopVehicleTracking()
        mutableState.update { it.copy(isLoading = true, routes = emptyList(), selectedRoute = null, arrival = "", arrivalMinutes = null, origin = origin.trim(), destination = destination.trim(), vehicles = emptyList(), vehicleStatus = "", status = "Consultando linhas e paradas SPTrans...") }
        finder.findDirectRoutes(origin.trim(), destination.trim()) { routes, error ->
            val firstRoute = routes.firstOrNull()
            mutableState.update {
                it.copy(
                    isLoading = false,
                    routes = routes,
                    selectedRoute = firstRoute,
                    status = error ?: "${routes.size} opção(ões) direta(s). Selecione uma para consultar a previsão oficial."
                )
            }
            firstRoute?.let(::select)
        }
    }

    fun select(route: TransitRoute) {
        stopVehicleTracking()
        mutableState.update { it.copy(selectedRoute = route, arrival = "Consultando previsão SPTrans...", arrivalMinutes = null) }
        spTrans.arrivals(route.boardingStop.code, route.line.code) { arrivals, error ->
            val time = arrivals?.firstOrNull()?.let(::minutesUntilArrival)
            mutableState.update {
                if (it.selectedRoute?.line?.code != route.line.code) it else it.copy(
                    selectedRoute = route,
                    arrivalMinutes = time,
                    arrival = when {
                        error != null -> "Falha ao consultar previsão SPTrans."
                        time != null -> "Previsão SPTrans: $time min"
                        else -> "SPTrans sem previsão para este ponto agora."
                    }
                )
            }
        }
        startVehicleTracking(route)
    }

    fun startVehicleTracking(route: TransitRoute) {
        if (vehicleRefreshJob?.isActive == true && mutableState.value.selectedRoute?.line?.code == route.line.code) return
        stopVehicleTracking()
        vehicleRefreshJob = viewModelScope.launch {
            while (isActive && mutableState.value.selectedRoute?.line?.code == route.line.code) {
                spTrans.vehiclesForLine(route.line.code) { vehicles, error ->
                    mutableState.update { current ->
                        if (current.selectedRoute?.line?.code != route.line.code) current
                        else current.copy(
                            vehicles = vehicles.orEmpty(),
                            vehicleStatus = when {
                                error != null -> "Posições ao vivo indisponíveis."
                                vehicles.isNullOrEmpty() -> "Nenhum veículo reportando posição agora."
                                else -> "${vehicles.size} ônibus · atualização ${vehicles.last().updatedAt}"
                            }
                        )
                    }
                }
                delay(VEHICLE_REFRESH_MS)
            }
        }
    }

    fun stopVehicleTracking() {
        vehicleRefreshJob?.cancel()
        vehicleRefreshJob = null
    }

    override fun onCleared() {
        stopVehicleTracking()
        super.onCleared()
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

    class Factory(private val finder: TransitRouteFinder, private val spTrans: SpTransService) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RoutePlannerViewModel::class.java))
            return RoutePlannerViewModel(finder, spTrans) as T
        }
    }

    companion object {
        private const val VEHICLE_REFRESH_MS = 30_000L
    }
}

@Composable
private fun HomeScreen(viewModel: RoutePlannerViewModel, routeStore: FirebaseRouteStore) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    var origin by remember { mutableStateOf("") }
    var destination by remember { mutableStateOf("") }
    var saveStatus by remember { mutableStateOf("") }
    var locationMessage by remember { mutableStateOf("") }
    var userLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var searchLocation by remember { mutableStateOf<GeoPoint?>(null) }
    var activeSearch by remember { mutableStateOf("origin") }
    var selectedOriginQuery by remember { mutableStateOf("") }
    var selectedDestinationQuery by remember { mutableStateOf("") }
    var originSuggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    var destinationSuggestions by remember { mutableStateOf<List<AddressSuggestion>>(emptyList()) }
    val context = LocalContext.current
    val nominatim = remember { NominatimService() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    DisposableEffect(lifecycleOwner, viewModel, state.selectedRoute) {
        val selectedRoute = state.selectedRoute
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> selectedRoute?.let(viewModel::startVehicleTracking)
                Lifecycle.Event.ON_PAUSE -> viewModel.stopVehicleTracking()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) selectedRoute?.let(viewModel::startVehicleTracking)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopVehicleTracking()
        }
    }
    val fusedLocationClient = remember(context) { LocationServices.getFusedLocationProviderClient(context) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) {
            requestDeviceLocation(fusedLocationClient, context) { location, address ->
                userLocation = location?.let { GeoPoint(it.latitude, it.longitude) }
                if (address != null) origin = address
                locationMessage = address ?: "Localização obtida; informe a origem manualmente."
            }
        } else {
            locationMessage = "Permissão negada; informe a origem manualmente."
        }
    }
    val locate: () -> Unit = {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            requestDeviceLocation(fusedLocationClient, context) { location, address ->
                userLocation = location?.let { GeoPoint(it.latitude, it.longitude) }
                if (address != null) origin = address
                locationMessage = address ?: "Localização obtida; informe a origem manualmente."
            }
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    LaunchedEffect(activeSearch, origin, destination) {
        val isOrigin = activeSearch == "origin"
        val query = if (isOrigin) origin else destination
        originSuggestions = emptyList()
        destinationSuggestions = emptyList()
        val selectedQuery = if (isOrigin) selectedOriginQuery else selectedDestinationQuery
        if (query != selectedQuery && query.trim().length >= 3) {
            delay(1_100)
            nominatim.searchAddress(query) { suggestions, error ->
                mainHandler.post {
                    val current = if (activeSearch == "origin") origin else destination
                    if (current == query && activeSearch == if (isOrigin) "origin" else "destination") {
                        if (isOrigin) originSuggestions = suggestions else destinationSuggestions = suggestions
                        if (error != null) locationMessage = error
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("ROTAS DE ÔNIBUS · SÃO PAULO", style = MaterialTheme.typography.labelMedium, color = appMuted)
        Spacer(Modifier.height(8.dp))
        TransitMap(state.selectedRoute, userLocation, searchLocation, state.vehicles)
        TextButton(onClick = locate) { Text("Usar minha localização") }
        if (locationMessage.isNotBlank()) Text(locationMessage, color = appMuted)
        Spacer(Modifier.height(14.dp))
        Text("Planeje sua viagem", style = MaterialTheme.typography.titleLarge, color = appInk)
        OutlinedTextField(
            value = origin,
            onValueChange = { activeSearch = "origin"; selectedOriginQuery = ""; origin = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            label = { Text("Origem ou ponto de partida") }
        )
        originSuggestions.forEach { suggestion ->
            TextButton(onClick = {
                selectedOriginQuery = suggestion.displayName
                origin = suggestion.displayName
                searchLocation = GeoPoint(suggestion.latitude, suggestion.longitude)
                originSuggestions = emptyList()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(suggestion.displayName, color = appInk, maxLines = 2)
            }
        }
        OutlinedTextField(
            value = destination,
            onValueChange = { activeSearch = "destination"; selectedDestinationQuery = ""; destination = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
            label = { Text("Destino ou ponto de chegada") }
        )
        destinationSuggestions.forEach { suggestion ->
            TextButton(onClick = {
                selectedDestinationQuery = suggestion.displayName
                destination = suggestion.displayName
                searchLocation = GeoPoint(suggestion.latitude, suggestion.longitude)
                destinationSuggestions = emptyList()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(suggestion.displayName, color = appInk, maxLines = 2)
            }
        }
        Button(
            onClick = { viewModel.search(origin, destination) },
            enabled = !state.isLoading,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
        ) {
            Text("Buscar rotas de ônibus")
        }
        if (state.isLoading) CircularProgressIndicator(Modifier.padding(16.dp))
        Text(state.status, style = MaterialTheme.typography.bodyMedium, color = appMuted, modifier = Modifier.padding(vertical = 12.dp))
        if (state.arrival.isNotBlank()) Text(state.arrival, color = busGreen, style = MaterialTheme.typography.titleSmall)
        if (state.vehicleStatus.isNotBlank()) Text(state.vehicleStatus, color = appMuted, style = MaterialTheme.typography.bodySmall)
        state.routes.forEach { route ->
            Card(
                onClick = { viewModel.select(route) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(route.line.number, style = MaterialTheme.typography.titleMedium, color = busGreen)
                        Text("${route.stops.size} paradas", style = MaterialTheme.typography.bodySmall, color = appMuted)
                    }
                    Text(route.line.destination.ifBlank { route.alightingStop.name }, color = appInk)
                    Text("Embarque · ${route.boardingStop.name}", style = MaterialTheme.typography.bodySmall, color = appMuted)
                    Text("Desembarque · ${route.alightingStop.name}", style = MaterialTheme.typography.bodySmall, color = appMuted)
                    Row {
                        TextButton(onClick = {
                            routeStore.saveFavoriteLine(route.line.number) { error ->
                                Handler(Looper.getMainLooper()).post { saveStatus = error ?: "Linha salva nos favoritos." }
                            }
                        }) { Text("Favoritar linha") }
                        TextButton(onClick = {
                            routeStore.saveRoute(origin, destination, route.line.number, favorite = true) { error ->
                                Handler(Looper.getMainLooper()).post { saveStatus = error ?: "Rota salva nos favoritos." }
                            }
                        }) { Text("Salvar rota") }
                    }
                }
            }
        }
        if (saveStatus.isNotBlank()) Text(saveStatus, color = appMuted)
    }
}

private fun requestDeviceLocation(
    client: FusedLocationProviderClient,
    context: android.content.Context,
    callback: (Location?, String?) -> Unit
) {
    try {
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, CancellationTokenSource().token)
            .addOnSuccessListener { location ->
                if (location == null) {
                    callback(null, "Localização indisponível. Confira se o GPS está ligado.")
                    return@addOnSuccessListener
                }
                Thread {
                    val address = runCatching {
                        @Suppress("DEPRECATION")
                        Geocoder(context, Locale("pt", "BR")).getFromLocation(location.latitude, location.longitude, 1)
                            ?.firstOrNull()?.getAddressLine(0)
                    }.getOrNull()?.takeIf(String::isNotBlank)
                    Handler(Looper.getMainLooper()).post { callback(location, address) }
                }.start()
            }
            .addOnFailureListener { callback(null, "Falha ao obter localização. Tente novamente.") }
    } catch (_: SecurityException) {
        callback(null, "Permita a localização para usar este recurso.")
    }
}

@Composable
private fun TransitMap(route: TransitRoute?, userLocation: GeoPoint?, searchLocation: GeoPoint?, vehicles: List<SpTransVehicle>) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView?.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView?.onPause()
        }
    }
    AndroidView(
        factory = { MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            minZoomLevel = 10.0
            maxZoomLevel = 19.0
            controller.setZoom(12.5)
            controller.setCenter(GeoPoint(-23.5505, -46.6333))
        }.also { mapView = it } },
        update = { map ->
            map.overlays.clear()
            route?.let { selected ->
                val points = selected.stops.map { GeoPoint(it.latitude, it.longitude) }
                if (points.isNotEmpty()) {
                    map.overlays.add(Polyline().apply {
                        setPoints(points)
                        outlinePaint.color = android.graphics.Color.rgb(23, 107, 88)
                        outlinePaint.strokeWidth = 8f
                    })
                    listOf(selected.boardingStop, selected.alightingStop).forEach { stop ->
                        map.overlays.add(Marker(map).apply {
                            position = GeoPoint(stop.latitude, stop.longitude)
                            title = stop.name
                            snippet = stop.address
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                    map.controller.setCenter(points[points.size / 2])
                    map.controller.setZoom(14.0)
                }
            }
            userLocation?.let { location ->
                map.overlays.add(Marker(map).apply {
                    position = location
                    title = "Sua localização"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
                if (route == null) {
                    map.controller.setCenter(location)
                    map.controller.setZoom(15.0)
                }
            }
            searchLocation?.let { location ->
                map.overlays.add(Marker(map).apply {
                    position = location
                    title = "Local pesquisado"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                })
                if (route == null && userLocation == null) {
                    map.controller.setCenter(location)
                    map.controller.setZoom(15.0)
                }
            }
            vehicles.forEach { vehicle ->
                map.overlays.add(Marker(map).apply {
                    position = GeoPoint(vehicle.latitude, vehicle.longitude)
                    title = "Ônibus ${vehicle.prefix}"
                    snippet = if (vehicle.accessible) "Acessível · atualização ${vehicle.updatedAt}" else "Atualização ${vehicle.updatedAt}"
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                })
            }
            map.invalidate()
        },
        onRelease = { map ->
            if (mapView === map) mapView = null
            map.onPause()
            map.onDetach()
        },
        modifier = Modifier.fillMaxWidth().height(230.dp)
    )
}

@Composable
private fun SavedRoutesScreen(store: FirebaseRouteStore) {
    var routes by remember { mutableStateOf<List<SavedTransitRoute>>(emptyList()) }
    var message by remember { mutableStateOf("Favoritos e rotas recentes sincronizados na sua conta Firebase.") }
    val context = LocalContext.current
    LaunchedEffect(store) {
        store.loadRoutes(favorites = true) { saved, error ->
            Handler(Looper.getMainLooper()).post {
                routes = saved
                message = error ?: if (saved.isEmpty()) "Nenhuma rota favorita salva." else "${saved.size} rotas favoritas"
            }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Rotas salvas", style = MaterialTheme.typography.headlineSmall, color = appInk)
        Text(message, color = appMuted, modifier = Modifier.padding(vertical = 10.dp))
        routes.forEach { route ->
            Card(Modifier.fillMaxWidth().padding(bottom = 8.dp), shape = RoundedCornerShape(8.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(route.line, color = busGreen, style = MaterialTheme.typography.titleMedium)
                    Text("${route.origin} → ${route.destination}", color = appInk)
                }
            }
        }
    }
}

@Composable
private fun TelemetryScreen(
    service: ThingSpeakService,
    distanceMeters: StateFlow<Int>,
    hasStepSensor: Boolean,
    onEnableStepTracking: () -> Unit
) {
    val localDistance by distanceMeters.collectAsStateWithLifecycle()
    var feed by remember { mutableStateOf<ThingSpeakFeed?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val refresh: () -> Unit = {
        loading = true
        service.loadChannelData { result, failure ->
            mainHandler.post {
                feed = result
                error = failure
                loading = false
            }
        }
    }
    LaunchedEffect(service) { refresh() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Telemetria", style = MaterialTheme.typography.headlineSmall, color = appInk)
        Text("ThingSpeak · dados e unidades conforme definidos no canal", color = appMuted)
        Text("Distância a pé hoje · $localDistance m", style = MaterialTheme.typography.titleMedium, color = busGreen, modifier = Modifier.padding(top = 12.dp))
        Text(if (hasStepSensor) "Contagem local pelo sensor do aparelho." else "Sem sensor dedicado; o acelerômetro será usado quando disponível.", color = appMuted)
        Button(onClick = onEnableStepTracking, modifier = Modifier.padding(top = 8.dp)) { Text("Ativar contagem de passos") }
        Button(onClick = refresh, enabled = !loading, modifier = Modifier.padding(vertical = 12.dp)) {
            Text(if (loading) "Atualizando..." else "Atualizar")
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        feed?.let { channel ->
            Text("${channel.channelName} · ${channel.channelId}", style = MaterialTheme.typography.titleMedium, color = appInk)
            channel.fields.forEach { field ->
                val readings = channel.readings.mapNotNull { it.fields[field.number]?.toFloat() }
                Text("Field ${field.number} · ${field.label}", modifier = Modifier.padding(top = 12.dp), color = appInk)
                if (readings.isEmpty()) {
                    Text("${channel.readings.size} registros sem valor numérico neste campo.", color = appMuted)
                } else {
                    TelemetryChart(readings)
                    channel.readings.lastOrNull()?.let { latest ->
                        Text("Última leitura: ${latest.fields[field.number]} · ${latest.createdAt}", color = appMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryChart(values: List<Float>) {
    Canvas(Modifier.fillMaxWidth().height(160.dp).padding(vertical = 8.dp)) {
        val left = 8.dp.toPx()
        val right = size.width - left
        val top = 8.dp.toPx()
        val bottom = size.height - top
        repeat(4) { index ->
            val y = top + (bottom - top) * index / 3f
            drawLine(Color(0xFFE0E6E0), androidx.compose.ui.geometry.Offset(left, y), androidx.compose.ui.geometry.Offset(right, y))
        }
        val min = values.minOrNull() ?: return@Canvas
        val max = values.maxOrNull() ?: return@Canvas
        val span = (max - min).takeIf { it > 0f } ?: 1f
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = if (values.size == 1) (left + right) / 2 else left + (right - left) * index / (values.size - 1)
            val y = bottom - (value - min) / span * (bottom - top)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, busGreen, style = Stroke(width = 2.dp.toPx()))
    }
}

@Composable
private fun AssistantScreen(viewModel: RoutePlannerViewModel) {
    val routeState by viewModel.state.collectAsStateWithLifecycle()
    var question by remember { mutableStateOf("") }
    val engine = remember { AssistantEngine() }
    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                false,
                "Posso ajudar com rotas, linhas, pontos e previsões disponíveis. As respostas usam regras locais e dados reais do SPBus."
            )
        )
    }
    val context = AssistantContext(
        origin = routeState.origin,
        destination = routeState.destination,
        selectedLine = routeState.selectedRoute?.line?.number,
        arrivalMinutes = routeState.arrivalMinutes,
        nearbyLines = routeState.routes.map { it.line.number }
    )
    val send: () -> Unit = {
        val prompt = question.trim()
        if (prompt.isNotBlank()) {
            messages.add(ChatMessage(true, prompt))
            messages.add(ChatMessage(false, engine.respond(prompt, context).message))
            question = ""
        }
    }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Assistente SPBus", style = MaterialTheme.typography.headlineSmall, color = appInk)
            TextButton(onClick = {
                messages.clear()
                messages.add(ChatMessage(false, "Conversa limpa. Pergunte sobre linhas, pontos, rotas ou previsão."))
            }) { Text("Limpar") }
        }
        Text("Ajuda local por regras, sem API externa de IA.", color = appMuted)
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            messages.forEach { message ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = if (message.fromUser) Color(0xFFE1EFE8) else Color.White)
                ) {
                    Text(message.text, Modifier.padding(12.dp), color = appInk)
                }
            }
        }
        Row {
            listOf("Como vejo uma rota?", "Quanto falta para chegar?", "Esse ônibus está lotado?").forEach { sample ->
                TextButton(onClick = { question = sample; send() }) { Text(sample, maxLines = 1) }
            }
        }
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                modifier = Modifier.weight(1f),
                label = { Text("Pergunte sobre o transporte") },
                maxLines = 3
            )
            TextButton(onClick = send, enabled = question.isNotBlank()) { Text("Enviar") }
        }
    }
}

private data class ChatMessage(val fromUser: Boolean, val text: String)

@Composable
private fun ProfileScreen(store: FirebaseRouteStore, onFeedback: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var accountEmail by remember { mutableStateOf(store.accountEmail.orEmpty()) }
    var signedIn by remember { mutableStateOf(store.isSignedIn) }
    var processing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("Perfil", style = MaterialTheme.typography.headlineSmall, color = appInk)
        Text("${accountEmail.ifBlank { if (signedIn) "Sessão anônima" else "Sem sessão" }} · Firebase ${if (store.isConfigured) "conectado" else "não configurado"}", color = appMuted, modifier = Modifier.padding(vertical = 12.dp))
        if (!store.isConfigured) {
            Text("Adicione google-services.json em app/ e habilite Email/Senha e autenticação anônima no Firebase Console.", color = appMuted)
        } else if (accountEmail.isNotBlank()) {
            TextButton(onClick = {
                store.signOut()
                signedIn = false
                accountEmail = ""
                status = "Sessão encerrada."
            }) { Text("Sair") }
        } else {
            OutlinedTextField(email, { email = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, label = { Text("E-mail") })
            OutlinedTextField(
                password,
                { password = it },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
                label = { Text("Senha") },
                visualTransformation = PasswordVisualTransformation()
            )
            Row {
                TextButton(enabled = !processing, onClick = {
                    if (email.isBlank() || password.length < 6) {
                        status = "Informe e-mail e senha com pelo menos 6 caracteres."
                    } else {
                        processing = true
                        store.signIn(email, password) { error ->
                            Handler(Looper.getMainLooper()).post {
                                processing = false
                                status = error ?: "Login realizado."
                                if (error == null) {
                                    accountEmail = store.accountEmail.orEmpty()
                                    signedIn = store.isSignedIn
                                }
                            }
                        }
                    }
                }) { Text("Entrar") }
                TextButton(enabled = !processing, onClick = {
                    if (email.isBlank() || password.length < 6) {
                        status = "Informe e-mail e senha com pelo menos 6 caracteres."
                    } else {
                        processing = true
                        store.register(email, password) { error ->
                            Handler(Looper.getMainLooper()).post {
                                processing = false
                                status = error ?: "Conta criada."
                                if (error == null) {
                                    accountEmail = store.accountEmail.orEmpty()
                                    signedIn = store.isSignedIn
                                }
                            }
                        }
                    }
                }) { Text("Criar conta") }
            }
        }
        if (processing) CircularProgressIndicator()
        if (status.isNotBlank()) Text(status, color = appMuted)
        Button(onClick = onFeedback) { Text("Enviar relato") }
    }
}

@Composable
private fun FeedbackEntryScreen(store: FirebaseRouteStore) {
    var line by remember { mutableStateOf("") }
    var comment by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Lotado") }
    var status by remember { mutableStateOf(if (store.isConfigured) "Relato associado à sua sessão Firebase." else "Adicione a configuração Firebase para enviar relatos.") }
    var sending by remember { mutableStateOf(false) }
    val categories = listOf("Lotado", "Atrasado", "Normal", "Vazio", "Problema no veículo", "Outro")
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        Text("Relato do passageiro", style = MaterialTheme.typography.headlineSmall, color = appInk)
        OutlinedTextField(line, { line = it }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp), label = { Text("Linha") }, singleLine = true)
        Text("Categoria", modifier = Modifier.padding(top = 12.dp), color = appMuted)
        Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            categories.forEach { option ->
                FilterChip(
                    selected = category == option,
                    onClick = { category = option },
                    label = { Text(option) },
                    modifier = Modifier.padding(vertical = 3.dp)
                )
            }
        }
        OutlinedTextField(comment, { comment = it }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Comentário (máximo 500 caracteres)") }, minLines = 3)
        Text(status, color = appMuted, modifier = Modifier.padding(vertical = 10.dp))
        Button(onClick = {
            if (!store.isConfigured) {
                status = "Adicione google-services.json e habilite autenticação no Firebase Console."
            } else if (comment.isBlank()) {
                status = "Escreva um comentário antes de enviar."
            } else if (comment.length > 500) {
                status = "O comentário deve ter no máximo 500 caracteres."
            } else {
                sending = true
                status = "Enviando relato..."
                store.saveFeedback(line, category, comment) { error ->
                    Handler(Looper.getMainLooper()).post {
                        sending = false
                        status = error ?: "Relato enviado com sucesso."
                        if (error == null) comment = ""
                    }
                }
            }
        }, enabled = !sending, modifier = Modifier.fillMaxWidth()) {
            Text(if (sending) "Enviando..." else "Enviar relato")
        }
    }
}
