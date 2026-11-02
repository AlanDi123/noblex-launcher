package io.github.alandi123.noblexlauncher

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Real, permission-free status read from Android. Unknown is reported as unknown, never guessed. */
object ConnectivityStatus {
    enum class Link { CONNECTED, DISCONNECTED, UNKNOWN }

    class Network(val link: Link, val transport: String?, val hasInternet: Boolean)

    fun network(context: Context): Network {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return Network(Link.UNKNOWN, null, false)
        return try {
            val active = manager.activeNetwork ?: return Network(Link.DISCONNECTED, null, false)
            val caps = manager.getNetworkCapabilities(active) ?: return Network(Link.DISCONNECTED, null, false)
            val transport = when {
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
                caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
                else -> "Otra red"
            }
            Network(Link.CONNECTED, transport, caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED))
        } catch (_: SecurityException) {
            Network(Link.UNKNOWN, null, false)
        }
    }

    enum class Bluetooth { ON, OFF, TURNING, UNAVAILABLE, UNKNOWN }

    @Suppress("DEPRECATION")
    fun bluetooth(): Bluetooth = try {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        when (adapter?.state) {
            null -> Bluetooth.UNAVAILABLE
            BluetoothAdapter.STATE_ON -> Bluetooth.ON
            BluetoothAdapter.STATE_OFF -> Bluetooth.OFF
            else -> Bluetooth.TURNING
        }
    } catch (_: SecurityException) {
        Bluetooth.UNKNOWN
    }

    fun wifiText(network: Network): String = when {
        network.link == Link.UNKNOWN -> "Estado no disponible"
        network.link == Link.DISCONNECTED -> "Sin conexión"
        network.transport != "Wi-Fi" -> "Conectado por ${network.transport}"
        network.hasInternet -> "Conectado · con internet"
        else -> "Conectado · sin internet verificado"
    }

    fun bluetoothText(state: Bluetooth): String = when (state) {
        Bluetooth.ON -> "Activado"
        Bluetooth.OFF -> "Desactivado"
        Bluetooth.TURNING -> "Cambiando de estado"
        Bluetooth.UNAVAILABLE -> "No disponible"
        Bluetooth.UNKNOWN -> "Estado no disponible"
    }
}
