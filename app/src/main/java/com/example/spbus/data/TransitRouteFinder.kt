package com.example.spbus.data

data class TransitRoute(
    val line: SpTransLine,
    val boardingStop: SpTransStop,
    val alightingStop: SpTransStop,
    val stops: List<SpTransStop>,
    val walkingMeters: Int,
    val busMeters: Int,
    val estimatedMinutes: Int
)

class TransitRouteFinder(private val spTrans: SpTransService) {

    fun findDirectRoutes(
        originQuery: String,
        destinationQuery: String,
        callback: (List<TransitRoute>, String?) -> Unit
    ) {
        if (!spTrans.isConfigured) {
            callback(emptyList(), "Adicione o token Olho Vivo em local.properties para buscar linhas oficiais.")
            return
        }

        spTrans.searchStops(originQuery) { origins, originError ->
            if (origins.isNullOrEmpty()) {
                callback(emptyList(), originError ?: "Nao encontrei paradas perto da origem. Tente informar o nome da rua ou do ponto.")
                return@searchStops
            }
            spTrans.searchStops(destinationQuery) { destinations, destinationError ->
                if (destinations.isNullOrEmpty()) {
                    callback(emptyList(), destinationError ?: "Nao encontrei paradas perto do destino. Tente informar o nome da rua ou do ponto.")
                    return@searchStops
                }
                searchCandidates(origins.take(MAX_CANDIDATES), destinations.take(MAX_CANDIDATES), callback)
            }
        }
    }

    private fun searchCandidates(
        origins: List<SpTransStop>,
        destinations: List<SpTransStop>,
        callback: (List<TransitRoute>, String?) -> Unit
    ) {
        val routes = mutableListOf<TransitRoute>()
        lateinit var scanOrigin: (Int) -> Unit
        scanOrigin = originScan@{ originIndex ->
            if (originIndex >= origins.size || routes.size >= MAX_ROUTES) {
                val ranked = routes.distinctBy { it.line.code }
                    .sortedBy { it.estimatedMinutes }
                callback(ranked, if (ranked.isEmpty()) "Nao encontrei uma linha direta entre essas paradas. Tente pontos mais proximos ou outro endereco." else null)
                return@originScan
            }

            val origin = origins[originIndex]
            spTrans.linesAtStop(origin.code) { lines, _ ->
                val linesByCode = lines.orEmpty().associateBy { it.code }
                var destinationIndex = 0
                lateinit var scanDestination: (Int) -> Unit
                scanDestination = destinationScan@{ index ->
                    if (index >= destinations.size || routes.size >= MAX_ROUTES) {
                        scanOrigin(originIndex + 1)
                        return@destinationScan
                    }
                    val destination = destinations[index]
                    spTrans.lineCodesAtStop(destination.code) { destinationCodes, _ ->
                        val matchingLines = destinationCodes.orEmpty()
                            .mapNotNull(linesByCode::get)
                            .distinctBy { it.code }
                        var lineIndex = 0
                        lateinit var scanLine: (Int) -> Unit
                        scanLine = lineScan@{ matchIndex ->
                            if (matchIndex >= matchingLines.size || routes.size >= MAX_ROUTES) {
                                destinationIndex = index + 1
                                scanDestination(destinationIndex)
                                return@lineScan
                            }
                            val line = matchingLines[matchIndex]
                            spTrans.stopsForLine(line.code) { allStops, _ ->
                                val stops = allStops.orEmpty()
                                val startIndex = stops.indexOfFirst { it.code == origin.code }
                                val endIndex = stops.indexOfFirst { it.code == destination.code }
                                if (startIndex >= 0 && endIndex > startIndex) {
                                    val segment = stops.subList(startIndex, endIndex + 1)
                                    val busMeters = segment.zipWithNext().sumOf { (first, second) ->
                                        distanceMeters(first, second)
                                    }
                                    val walkingMeters = distanceMeters(origin, stops[startIndex]) +
                                        distanceMeters(stops[endIndex], destination)
                                    val walkingMinutes = kotlin.math.ceil(walkingMeters / WALKING_METERS_PER_MINUTE).toInt()
                                    val busMinutes = kotlin.math.ceil(busMeters / BUS_METERS_PER_MINUTE).toInt()
                                    routes.add(
                                        TransitRoute(
                                            line = line,
                                            boardingStop = segment.first(),
                                            alightingStop = segment.last(),
                                            stops = segment,
                                            walkingMeters = walkingMeters,
                                            busMeters = busMeters,
                                            estimatedMinutes = walkingMinutes + busMinutes + AVERAGE_WAIT_MINUTES
                                        )
                                    )
                                }
                                lineIndex = matchIndex + 1
                                scanLine(lineIndex)
                            }
                        }
                        scanLine(0)
                    }
                }
                scanDestination(0)
            }
        }
        scanOrigin(0)
    }

    private fun distanceMeters(first: SpTransStop, second: SpTransStop): Int {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            first.latitude, first.longitude, second.latitude, second.longitude, results
        )
        return results[0].toInt().coerceAtLeast(0)
    }

    companion object {
        private const val MAX_CANDIDATES = 4
        private const val MAX_ROUTES = 6
        private const val WALKING_METERS_PER_MINUTE = 75.0
        private const val BUS_METERS_PER_MINUTE = 250.0
        private const val AVERAGE_WAIT_MINUTES = 5
    }
}