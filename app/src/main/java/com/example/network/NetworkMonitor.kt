package com.example.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.example.data.ConnectivityRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NetworkMonitor(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val repository = ConnectivityRepository(context)

    private val _isOnline = MutableStateFlow(checkInitialConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _networkType = MutableStateFlow(getInitialNetworkType())
    val networkType: StateFlow<String> = _networkType.asStateFlow()

    private val activeNetworks = mutableSetOf<Network>()

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            activeNetworks.add(network)
            val typeName = resolveNetworkTypeName(network)
            _networkType.value = typeName
            val wasOffline = !_isOnline.value
            _isOnline.value = true

            repository.logStatus(
                status = if (wasOffline) "RECONNECTED" else "ONLINE",
                networkType = typeName,
                details = "Hardware path established via onAvailable"
            )
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            activeNetworks.remove(network)
            val hasRemaining = activeNetworks.isNotEmpty() || checkInitialConnectivity()
            if (!hasRemaining) {
                _isOnline.value = false
                _networkType.value = "NONE"
                repository.logStatus(
                    status = "OFFLINE",
                    networkType = "NONE",
                    details = "Hardware signal dropped via onLost"
                )
            }
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val typeName = resolveCapabilitiesType(networkCapabilities)

            if (hasInternet) {
                activeNetworks.add(network)
                _isOnline.value = true
                _networkType.value = typeName
            }
        }
    }

    fun startMonitoring() {
        connectivityManager?.let { cm ->
            try {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                cm.registerNetworkCallback(request, networkCallback)
            } catch (e: Exception) {
                // Fallback to registerDefaultNetworkCallback if supported
                try {
                    cm.registerDefaultNetworkCallback(networkCallback)
                } catch (ignored: Exception) {}
            }
        }
    }

    fun stopMonitoring() {
        try {
            connectivityManager?.unregisterNetworkCallback(networkCallback)
        } catch (ignored: Exception) {}
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
