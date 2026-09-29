package com.hackerai.mybudget.web

import java.net.Inet4Address
import java.net.NetworkInterface

object NetworkUtils {

    fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            val priorityCandidates = mutableListOf<String>()
            val fallbackCandidates = mutableListOf<String>()

            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (!networkInterface.isUp || networkInterface.isLoopback) continue

                val ifName = networkInterface.name.lowercase()
                val addresses = networkInterface.inetAddresses

                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (ip.startsWith("127.")) continue

                        if (ifName.startsWith("wlan") || ifName.startsWith("ap") || ifName.startsWith("swlan") || ifName.startsWith("rndis") || ifName.startsWith("eth") || ifName.startsWith("p2p")) {
                            priorityCandidates.add(ip)
                        } else {
                            fallbackCandidates.add(ip)
                        }
                    }
                }
            }

            return priorityCandidates.firstOrNull() ?: fallbackCandidates.firstOrNull()
        } catch (_: Exception) {
            return null
        }
    }
}
