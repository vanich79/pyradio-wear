package com.pyradio.wear.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService

/**
 * Есть ли у часов интернет.
 *
 * Нужен ровно для одного: отличить «эта станция умерла» от «мы в лифте».
 * Резолвер такого различия не делает и делать не может — неразрешившееся имя
 * выглядит одинаково в обоих случаях, — а здесь ответ даёт система.
 */
class Connectivity(context: Context) {

    private val manager = context.applicationContext.getSystemService<ConnectivityManager>()

    fun isOnline(): Boolean {
        val network = manager?.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            // NET_CAPABILITY_INTERNET означает «сеть обещает интернет»,
            // VALIDATED — «система сходила и убедилась». На часах, которые
            // цепляются за Wi-Fi с captive-порталом, разница существенная.
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
