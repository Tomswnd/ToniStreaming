package com.toni.streaming.data.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.toni.streaming.data.repository.AnimeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID

/** A discovered peer running ToniStreaming on the local network. */
data class SyncPeer(
    val id: String,
    val name: String,
    val host: String,
    val port: Int
)

/**
 * Zero-config peer-to-peer sync over the local Wi-Fi.
 *
 * Discovery uses NSD (mDNS) so no IP address is ever typed. Transport is a plain TCP socket
 * exchanging a JSON snapshot of watch history + favorites in both directions; merging is done
 * by [AnimeRepository]. All APIs are pure Android (works on Fire TV, no Google Play Services).
 */
class SyncManager(
    private val context: Context,
    private val repository: AnimeRepository
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val SERVICE_TYPE = "_tonistream._tcp."
        private const val ATTR_ID = "id"
        private const val ATTR_NAME = "name"
    }

    private val nsdManager: NsdManager =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // STABLE per-install id (persisted), used to filter our own service out of discovery results.
    // A stable id also excludes stale mDNS advertisements from this same install (which a random
    // per-start id would mistake for another device).
    private val instanceId: String = run {
        val prefs = context.applicationContext
            .getSharedPreferences("toni_sync", Context.MODE_PRIVATE)
        prefs.getString("instance_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("instance_id", it).apply()
        }
    }
    private val deviceName: String = Build.MODEL ?: "Dispositivo"

    private var serverSocket: ServerSocket? = null
    private var localPort: Int = 0

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _peers = MutableStateFlow<List<SyncPeer>>(emptyList())
    val peers: StateFlow<List<SyncPeer>> = _peers.asStateFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private var running = false

    // Serialize NSD resolve calls (concurrent resolves fail on older Android).
    private val resolveQueue = Channel<NsdServiceInfo>(Channel.UNLIMITED)

    // Maps an advertised service name -> resolved peer id, so onServiceLost can remove the exact
    // peer we added. Peers are keyed by their stable id (not by service name), so removing by a
    // name suffix could evict the wrong device when two share the same Build.MODEL.
    private val serviceNameToPeerId = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun start() {
        if (running) return
        running = true
        _peers.value = emptyList()
        startServer()
        startResolveWorker()
        startDiscovery()
    }

    fun stop() {
        running = false
        try { discoveryListener?.let { nsdManager.stopServiceDiscovery(it) } } catch (_: Exception) {}
        try { registrationListener?.let { nsdManager.unregisterService(it) } } catch (_: Exception) {}
        discoveryListener = null
        registrationListener = null
        try { serverSocket?.close() } catch (_: Exception) {}
        serverSocket = null
        _peers.value = emptyList()
    }

    fun dispose() {
        stop()
        scope.cancel()
    }

    // ---------------- Server (incoming sync) ----------------

    private fun startServer() {
        scope.launch {
            try {
                val ss = ServerSocket(0)
                serverSocket = ss
                localPort = ss.localPort
                registerService(localPort)
                Log.d(TAG, "Sync server on port $localPort")
                while (running && !ss.isClosed) {
                    val socket = try { ss.accept() } catch (e: Exception) { break }
                    scope.launch { handleIncoming(socket) }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error: ${e.message}")
            }
        }
    }

    private suspend fun handleIncoming(socket: Socket) {
        try {
            socket.use { s ->
                val input = DataInputStream(s.getInputStream())
                val output = DataOutputStream(s.getOutputStream())
                val peerPayload = readFramed(input)
                val ours = repository.exportSyncPayload()
                writeFramed(output, ours)
                val changed = repository.importAndMerge(peerPayload)
                Log.d(TAG, "Incoming sync merged $changed rows")
                _status.value = "Sincronizzato ($changed aggiornati)"
            }
        } catch (e: Exception) {
            Log.e(TAG, "handleIncoming error: ${e.message}")
        }
    }

    // ---------------- Client (outgoing sync) ----------------

    /** Connects to [peer], exchanges snapshots both ways and merges. Returns rows changed locally. */
    suspend fun syncWith(peer: SyncPeer): Result<Int> = withContext(Dispatchers.IO) {
        try {
            Socket(peer.host, peer.port).use { s ->
                val output = DataOutputStream(s.getOutputStream())
                val input = DataInputStream(s.getInputStream())
                val ours = repository.exportSyncPayload()
                writeFramed(output, ours)
                val peerPayload = readFramed(input)
                val changed = repository.importAndMerge(peerPayload)
                Log.d(TAG, "Outgoing sync with ${peer.name} merged $changed rows")
                Result.success(changed)
            }
        } catch (e: Exception) {
            Log.e(TAG, "syncWith error: ${e.message}")
            Result.failure(e)
        }
    }

    // ---------------- NSD registration ----------------

    private fun registerService(port: Int) {
        val info = NsdServiceInfo().apply {
            serviceName = "ToniStreaming-${deviceName}"
            serviceType = SERVICE_TYPE
            setPort(port)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute(ATTR_ID, instanceId)
                setAttribute(ATTR_NAME, deviceName)
            }
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "Service registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "Registration failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    // ---------------- NSD discovery ----------------

    private fun startDiscovery() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery start failed: $errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType.trimEnd('.') == SERVICE_TYPE.trimEnd('.')) {
                    resolveQueue.trySend(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                val lostId = serviceNameToPeerId.remove(service.serviceName)
                if (lostId != null) {
                    _peers.value = _peers.value.filterNot { it.id == lostId }
                }
            }
        }
        discoveryListener = listener
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
    }

    private fun serviceSuffix(serviceName: String): String =
        serviceName.substringAfter("ToniStreaming-", serviceName)

    private fun startResolveWorker() {
        scope.launch {
            for (service in resolveQueue) {
                if (!running) break
                try {
                    resolveOne(service)
                } catch (e: Exception) {
                    Log.e(TAG, "resolve error: ${e.message}")
                }
            }
        }
    }

    // ---------------- framing helpers ----------------

    private fun writeFramed(out: DataOutputStream, text: String) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        out.write(bytes)
        out.flush()
    }

    private fun readFramed(input: DataInputStream): String {
        val len = input.readInt()
        require(len in 0..(64 * 1024 * 1024)) { "payload too large: $len" }
        val buf = ByteArray(len)
        input.readFully(buf)
        return String(buf, Charsets.UTF_8)
    }

    private suspend fun resolveOne(service: NsdServiceInfo) = withContext(Dispatchers.IO) {
        val done = Channel<Unit>(1)
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                done.trySend(Unit)
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                try {
                    val attrs = serviceInfo.attributes
                    val peerId = attrs[ATTR_ID]?.let { String(it) }
                    val peerName = attrs[ATTR_NAME]?.let { String(it) }
                        ?: serviceSuffix(serviceInfo.serviceName)
                    val host = serviceInfo.host?.hostAddress
                    val port = serviceInfo.port
                    // Skip our own advertised service.
                    if (peerId != null && peerId != instanceId && host != null && port > 0) {
                        val peer = SyncPeer(id = peerId, name = peerName, host = host, port = port)
                        serviceNameToPeerId[serviceInfo.serviceName] = peer.id
                        _peers.value = (_peers.value.filterNot { it.id == peer.id } + peer)
                    }
                } catch (_: Exception) {
                } finally {
                    done.trySend(Unit)
                }
            }
        }
        try {
            nsdManager.resolveService(service, listener)
            done.receive() // wait for this resolve before starting the next
        } catch (e: Exception) {
            Log.e(TAG, "resolveService threw: ${e.message}")
        }
    }
}
