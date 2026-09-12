package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.example.data.ConnectivityRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val repository = ConnectivityRepository(context)

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkType = MutableStateFlow(getInitialNetworkType())
    val networkType: StateFlow<String> = _networkType.asStateFlow()

    private val defaultNetworkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            val typeName = resolveNetworkTypeName(network)
            _networkType.value = typeName
            val wasOffline = !_isOnline.value
            _isOnline.value = true

            repository.logStatus(
                status = if (wasOffline) "RECONNECTED" else "ONLINE",
                networkType = typeName,
                details = "Instant hardware default network established"
            )
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            // Default active network was dropped - trigger offline status instantly
            _isOnline.value = false
            _networkType.value = "NONE"
            repository.logStatus(
                status = "OFFLINE",
                networkType = "NONE",
                details = "Default network dropped immediately"
            )
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            if (hasInternet && isValidated) {
                _isOnline.value = true
                _networkType.value = resolveCapabilitiesType(networkCapabilities)
            } else if (!hasInternet) {
                _isOnline.value = false
                _networkType.value = "NONE"
            }
        }
    }

    fun startMonitoring() {
        connectivityManager?.let { cm ->
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    // Modern Android: registerDefaultNetworkCallback tracks the active default network with zero latency
                    cm.registerDefaultNetworkCallback(defaultNetworkCallback)
                } else {
                    @Suppress("DEPRECATION")
                    val request = android.net.NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()
                    cm.registerNetworkCallback(request, defaultNetworkCallback)
                }
            } catch (e: Exception) {
                _isOnline.value = checkInitialConnectivity()
            }
        }
    }

    fun stopMonitoring() {
        try {
            connectivityManager?.unregisterNetworkCallback(defaultNetworkCallback)
        } catch (ignored: Exception) {}
    }

    fun notifyNetworkError() {
        _isOnline.value = false
        _networkType.value = "NONE"
    }

    private fun checkInitialConnectivity(): Boolean {
        val cm = connectivityManager ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getInitialNetworkType(): String {
        val cm = connectivityManager ?: return "UNKNOWN"
        val activeNetwork = cm.activeNetwork ?: return "NONE"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "UNKNOWN"
        return resolveCapabilitiesType(caps)
    }

    private fun resolveNetworkTypeName(network: Network): String {
        val cm = connectivityManager ?: return "UNKNOWN"
        val caps = cm.getNetworkCapabilities(network) ?: return "UNKNOWN"
        return resolveCapabilitiesType(caps)
    }

    private fun resolveCapabilitiesType(caps: NetworkCapabilities): String {
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "WIFI"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "CELLULAR"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ETHERNET"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "BLUETOOTH"
            else -> "INTERNET"
        }
    }
}
