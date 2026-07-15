package com.brainsherpa.neurogait

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class ScanActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val devices = mutableListOf<ScanResult>()
    private lateinit var adapter: DeviceAdapter
    private lateinit var btnScan: Button
    private lateinit var tvStatus: TextView
    private var scanning = false
    private var connecting = false
    private val handler = Handler(Looper.getMainLooper())

    private val SCAN_PERIOD: Long = 15000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Mandatory Google sign-in gate (shared suite identity for Supabase).
        if (!SupabaseIdentity.isSignedInWithGoogle(this)) {
            startActivity(Intent(this, GoogleSignInActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_scan)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        btnScan = findViewById(R.id.btn_scan)
        tvStatus = findViewById(R.id.tv_status)

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = DeviceAdapter(devices) { result -> connectToDevice(result) }
        recyclerView.adapter = adapter

        btnScan.setOnClickListener { startScanFlow() }
    }

    override fun onResume() {
        super.onResume()
        // Coming back from Stream: reset so the button works again.
        connecting = false
        if (!scanning) {
            btnScan.isEnabled = true
            btnScan.text = "SCAN + CONNECT MOVESENSE"
        }
    }

    private fun startScanFlow() {
        if (scanning || connecting) return
        val needed = requiredPermissions().filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 1)
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            tvStatus.text = "Bluetooth is off — turn it on and tap again"
            return
        }
        scanLeDevice()
    }

    private fun requiredPermissions(): List<String> {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            p.add(Manifest.permission.BLUETOOTH_SCAN)
            p.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        return p
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            scanLeDevice()
        } else {
            tvStatus.text = "Permissions needed to scan — tap to try again"
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanLeDevice() {
        if (scanning) return
        devices.clear()
        adapter.notifyDataSetChanged()
        scanning = true
        btnScan.isEnabled = false
        btnScan.text = "SCANNING…"
        tvStatus.text = "🔎 Searching for your Movesense…"

        handler.postDelayed({
            if (scanning) {
                stopScan()
                if (!connecting && devices.isEmpty()) {
                    tvStatus.text = "No Movesense found. Make sure it's awake, then tap again."
                }
                btnScan.isEnabled = true
                btnScan.text = "SCAN + CONNECT MOVESENSE"
            }
        }, SCAN_PERIOD)

        bluetoothAdapter.bluetoothLeScanner.startScan(leScanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        try {
            bluetoothAdapter.bluetoothLeScanner.stopScan(leScanCallback)
        } catch (_: Exception) {
        }
    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val name = result.device.name ?: return
            if (!name.contains("Movesense")) return
            // ScanCallback runs on a binder thread — bounce all UI work to main.
            runOnUiThread {
                if (devices.none { it.device.address == result.device.address }) {
                    devices.add(result)
                    adapter.notifyItemInserted(devices.size - 1)
                }
                // Auto-connect to the first Movesense we see.
                if (!connecting) {
                    connecting = true
                    stopScan()
                    tvStatus.text = "✅ Found $name — connecting…"
                    btnScan.text = "CONNECTING…"
                    handler.postDelayed({ connectToDevice(result) }, 250)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            runOnUiThread {
                scanning = false
                btnScan.isEnabled = true
                btnScan.text = "SCAN + CONNECT MOVESENSE"
                tvStatus.text = "Scan failed (code $errorCode) — tap to retry"
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(result: ScanResult) {
        stopScan()
        val intent = Intent(this, StreamActivity::class.java)
        intent.putExtra("device_address", result.device.address)
        intent.putExtra("device_name", result.device.name ?: "Movesense")
        startActivity(intent)
    }

    class DeviceAdapter(private val devices: List<ScanResult>, private val onClick: (ScanResult) -> Unit) :
        RecyclerView.Adapter<DeviceAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(android.R.id.text1)
            val address: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        @SuppressLint("MissingPermission")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val device = devices[position].device
            holder.name.text = device.name ?: "Unknown"
            holder.address.text = device.address
            holder.itemView.setOnClickListener { onClick(devices[position]) }
        }

        override fun getItemCount() = devices.size
    }
}
