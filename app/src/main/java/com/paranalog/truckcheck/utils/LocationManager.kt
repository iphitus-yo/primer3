package com.paranalog.truckcheck.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager as AndroidLocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

class LocationManager(private val context: Context) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val TAG = "LocationManager"

    /**
     * Obtém a localização atual com fallback para última localização conhecida
     * Funciona mesmo offline usando GPS
     */
    suspend fun getCurrentLocation(timeoutMillis: Long = 10000): String {
        return withContext(Dispatchers.IO) {
            try {
                // Verificar permissões
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    Log.e(TAG, "Sem permissão de localização")
                    return@withContext "Localização não disponível - Sem permissão"
                }

                // 1. Primeiro tenta obter a última localização conhecida (rápido e funciona offline)
                val lastLocation = getLastKnownLocation()
                if (lastLocation != null) {
                    // Se a última localização tem menos de 5 minutos, usa ela
                    val tempoDecorrido = System.currentTimeMillis() - lastLocation.time
                    if (tempoDecorrido < 5 * 60 * 1000) { // 5 minutos
                        Log.d(TAG, "Usando última localização conhecida (${tempoDecorrido/1000}s atrás)")
                        return@withContext formatLocationWithAddress(lastLocation)
                    }
                }

                // 2. Se não tem localização recente, tenta obter nova
                Log.d(TAG, "Tentando obter nova localização...")

                // Usar timeout maior se estiver offline
                val timeoutReal = if (isLocationServiceEnabled()) timeoutMillis else timeoutMillis * 2

                val novaLocalizacao = withTimeoutOrNull(timeoutReal) {
                    getCurrentLocationAsync()
                }

                if (novaLocalizacao != null) {
                    Log.d(TAG, "Nova localização obtida com sucesso")
                    formatLocationWithAddress(novaLocalizacao)
                } else if (lastLocation != null) {
                    // 3. Se falhou mas tem uma última localização (mesmo antiga), usa ela
                    Log.d(TAG, "Timeout na nova localização, usando última conhecida")
                    val formatted = formatLocationWithAddress(lastLocation)
                    if (formatted.contains("Lat:")) {
                        "$formatted (última conhecida)"
                    } else {
                        formatted
                    }
                } else {
                    // 4. Última tentativa - tentar pegar localização só do GPS sem internet
                    Log.d(TAG, "Tentando GPS puro como última alternativa...")
                    val gpsLocation = withTimeoutOrNull(5000) {
                        getGPSOnlyLocation()
                    }
                    if (gpsLocation != null) {
                        formatLocation(gpsLocation)
                    } else {
                        Log.e(TAG, "Nenhuma localização disponível")
                        "GPS sem sinal"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao obter localização: ${e.message}", e)
                "Localização não disponível - Erro: ${e.message}"
            }
        }
    }

    /**
     * Obtém a última localização conhecida do dispositivo
     */
    private suspend fun getLastKnownLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                fusedLocationClient.lastLocation
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            Log.d(TAG, "Última localização encontrada: ${location.latitude}, ${location.longitude}")
                        } else {
                            Log.d(TAG, "Nenhuma última localização disponível")
                        }
                        continuation.resume(location)
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Erro ao obter última localização: ${e.message}")
                        continuation.resume(null)
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exceção ao obter última localização: ${e.message}")
                continuation.resume(null)
            }
        }
    }

    /**
     * Obtém a localização atual com alta precisão
     */
    private suspend fun getCurrentLocationAsync(): Location? {
        return suspendCancellableCoroutine { continuation ->
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val cancellationTokenSource = CancellationTokenSource()

                // Configurar para alta precisão e funcionar offline
                val locationRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                        .setWaitForAccurateLocation(false) // Não esperar por localização muito precisa
                        .setMinUpdateIntervalMillis(500)
                        .setMaxUpdateDelayMillis(2000)
                        .build()
                } else {
                    LocationRequest.create().apply {
                        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
                        interval = 1000
                        fastestInterval = 500
                        maxWaitTime = 2000
                    }
                }

                // Método 1: Tentar getCurrentLocation (Android 11+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    fusedLocationClient.getCurrentLocation(
                        Priority.PRIORITY_HIGH_ACCURACY,
                        cancellationTokenSource.token
                    ).addOnSuccessListener { location ->
                        Log.d(TAG, "getCurrentLocation sucesso: $location")
                        continuation.resume(location)
                    }.addOnFailureListener { e ->
                        Log.e(TAG, "getCurrentLocation falhou: ${e.message}")
                        // Fallback para requestLocationUpdates
                        requestLocationUpdatesFallback(locationRequest, continuation)
                    }
                } else {
                    // Para Android < 11, usar requestLocationUpdates
                    requestLocationUpdatesFallback(locationRequest, continuation)
                }

                continuation.invokeOnCancellation {
                    cancellationTokenSource.cancel()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao configurar localização: ${e.message}")
                continuation.resume(null)
            }
        }
    }

    /**
     * Fallback usando requestLocationUpdates para dispositivos mais antigos
     */
    private fun requestLocationUpdatesFallback(
        locationRequest: LocationRequest,
        continuation: kotlinx.coroutines.CancellableContinuation<Location?>
    ) {
        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                continuation.resume(null)
                return
            }

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    Log.d(TAG, "LocationCallback recebeu: $location")
                    fusedLocationClient.removeLocationUpdates(this)
                    continuation.resume(location)
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!availability.isLocationAvailable) {
                        Log.w(TAG, "Localização não disponível no momento")
                        fusedLocationClient.removeLocationUpdates(this)
                        continuation.resume(null)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            continuation.invokeOnCancellation {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro no fallback: ${e.message}")
            continuation.resume(null)
        }
    }

    /**
     * Verifica se o serviço de localização está habilitado
     */
    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as AndroidLocationManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(AndroidLocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(AndroidLocationManager.NETWORK_PROVIDER)
        }
    }

    /**
     * Formata a localização para exibição (apenas coordenadas)
     */
    private fun formatLocation(location: Location): String {
        return try {
            val lat = String.format("%.6f", location.latitude)
            val lng = String.format("%.6f", location.longitude)
            "Lat: $lat, Lng: $lng"
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao formatar localização: ${e.message}")
            "Localização obtida"
        }
    }

    /**
     * Formata localização tentando obter endereço primeiro
     */
    private suspend fun formatLocationWithAddress(location: Location): String {
        return try {
            // Tentar obter endereço se tiver conexão
            if (isNetworkAvailable()) {
                val address = withTimeoutOrNull(3000) {
                    getAddressFromLocation(location)
                }

                if (!address.isNullOrEmpty() && !address.contains("Lat:")) {
                    address
                } else {
                    formatLocation(location)
                }
            } else {
                // Sem internet, retorna só coordenadas
                formatLocation(location)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao formatar com endereço: ${e.message}")
            formatLocation(location)
        }
    }

    /**
     * Obtém endereço a partir das coordenadas
     */
    private suspend fun getAddressFromLocation(location: Location): String {
        return withContext(Dispatchers.IO) {
            try {
                val geocoder = Geocoder(context, Locale("pt", "BR"))

                val addresses = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(
                            location.latitude,
                            location.longitude,
                            1
                        ) { addressList ->
                            continuation.resume(addressList)
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(location.latitude, location.longitude, 1)
                }

                if (!addresses.isNullOrEmpty()) {
                    val address = addresses[0]
                    buildString {
                        // Rua e número
                        address.thoroughfare?.let {
                            append(it)
                            address.subThoroughfare?.let { num ->
                                append(", ").append(num)
                            }
                        }

                        // Bairro
                        address.subLocality?.let {
                            if (isNotEmpty()) append(", ")
                            append(it)
                        }

                        // Cidade
                        address.locality?.let {
                            if (isNotEmpty()) append(", ")
                            append(it)
                        }

                        // Estado
                        address.adminArea?.let {
                            if (isNotEmpty()) append(" - ")
                            append(it)
                        }

                        // Se não conseguiu nenhuma parte, usar coordenadas
                        if (isEmpty()) {
                            append(formatLocation(location))
                        }
                    }
                } else {
                    formatLocation(location)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro no geocoding: ${e.message}")
                formatLocation(location)
            }
        }
    }

    /**
     * Tenta obter localização apenas do GPS (funciona offline)
     */
    private suspend fun getGPSOnlyLocation(): Location? {
        return suspendCancellableCoroutine { continuation ->
            try {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager

                // Verificar se GPS está ativado
                if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
                    continuation.resume(null)
                    return@suspendCancellableCoroutine
                }

                // Tentar pegar última localização do GPS
                @Suppress("DEPRECATION")
                val lastGPSLocation = locationManager.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)

                if (lastGPSLocation != null) {
                    continuation.resume(lastGPSLocation)
                } else {
                    // Pedir uma única atualização do GPS
                    val listener = object : android.location.LocationListener {
                        override fun onLocationChanged(location: Location) {
                            locationManager.removeUpdates(this)
                            continuation.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                        override fun onProviderEnabled(provider: String) {}
                        override fun onProviderDisabled(provider: String) {
                            locationManager.removeUpdates(this)
                            continuation.resume(null)
                        }
                    }

                    @Suppress("DEPRECATION")
                    locationManager.requestSingleUpdate(
                        android.location.LocationManager.GPS_PROVIDER,
                        listener,
                        Looper.getMainLooper()
                    )

                    continuation.invokeOnCancellation {
                        locationManager.removeUpdates(listener)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Erro ao obter GPS: ${e.message}")
                continuation.resume(null)
            }
        }
    }

    /**
     * Verifica se tem conexão de rede disponível
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            capabilities != null && (
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
                    )
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }
}