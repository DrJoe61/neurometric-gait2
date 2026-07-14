package com.brainsherpa.neurogait

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.view.WindowManager
import java.io.File
import java.io.FileWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

class StreamActivity : AppCompatActivity() {

    private var deviceAddress: String? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var isServicesDiscovered = false
    
    // Movesense Suunto GATT UUIDs
    private val MOVESENSE_SERVICE_UUID = UUID.fromString("61353090-8231-49cc-b57a-886370740041")
    private val NOTIFY_CHARACTERISTIC_UUID = UUID.fromString("34802252-7185-4d5d-b431-630e7050e8f0")
    private val WRITE_CHARACTERISTIC_UUID = UUID.fromString("17816557-5652-417f-909f-3aee61e5fa85")
    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private val dataList = mutableListOf<SensorData>()
    private var isStreaming = false
    private var stepCount = 0
    private var lastAccMag = 0f
    private var startTime = 0L
    private var peakImpact = 0f
    
    private val cadenceWindow = mutableListOf<Long>()
    private val TAG = "StreamActivity"

    private lateinit var tvAcc: TextView
    private lateinit var tvGyr: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvCadence: TextView
    private lateinit var tvPeak: TextView
    private lateinit var btnToggle: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stream)

        deviceAddress = intent.getStringExtra("device_address")
        tvAcc = findViewById(R.id.tv_acc)
        tvGyr = findViewById(R.id.tv_gyr)
        tvSteps = findViewById(R.id.tv_steps)
        tvTime = findViewById(R.id.tv_time)
        tvCadence = findViewById(R.id.tv_cadence)
        tvPeak = findViewById(R.id.tv_peak)
        btnToggle = findViewById(R.id.btn_toggle)

        btnToggle.setOnClickListener {
            if (isStreaming) {
                stopStreaming()
            } else {
                startStreaming()
            }
        }
        
        connectGatt()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @SuppressLint("MissingPermission")
    private fun connectGatt() {
        val bluetoothManager = getSystemService(BLUETOOTH_SERVICE) as BluetoothManager
        val device = bluetoothManager.adapter.getRemoteDevice(deviceAddress)
        Log.d(TAG, "Connecting to ${device.address}")
        bluetoothGatt = device.connectGatt(this, false, gattCallback)
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i(TAG, "Connected to GATT server. Discovering services...")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "Disconnected from GATT server.")
                isStreaming = false
                isServicesDiscovered = false
                runOnUiThread {
                    Toast.makeText(this@StreamActivity, "Disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered. Count: ${gatt.services.size}")
                
                gatt.services.forEach { service ->
                    Log.d(TAG, "Service: ${service.uuid}")
                    service.characteristics.forEach { characteristic ->
                        Log.d(TAG, "  Characteristic: ${characteristic.uuid}")
                    }
                }

                isServicesDiscovered = true
                runOnUiThread {
                    Toast.makeText(this@StreamActivity, "Found ${gatt.services.size} services. Device Ready", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == NOTIFY_CHARACTERISTIC_UUID) {
                val value = characteristic.value
                Log.d("IMU", "Raw bytes received (deprecated): ${value?.size ?: 0} bytes")
                processRawData(value)
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == NOTIFY_CHARACTERISTIC_UUID) {
                Log.d("IMU", "Raw bytes received: ${value.size} bytes")
                processRawData(value)
            }
        }
        
        override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
             Log.d(TAG, "onCharacteristicWrite: UUID=${characteristic.uuid}, status=$status")
        }

        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
             Log.d(TAG, "onDescriptorWrite: UUID=${descriptor.uuid}, status=$status")
             if (status == BluetoothGatt.GATT_SUCCESS && descriptor.characteristic.uuid == NOTIFY_CHARACTERISTIC_UUID) {
                 sendSubscribeCommand()
             }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startStreaming() {
        if (isStreaming || bluetoothGatt == null) return
        if (!isServicesDiscovered) {
            Toast.makeText(this, "Still discovering services...", Toast.LENGTH_SHORT).show()
            return
        }

        val service = bluetoothGatt?.getService(MOVESENSE_SERVICE_UUID)
        if (service == null) {
            Log.e(TAG, "Movesense service not found: $MOVESENSE_SERVICE_UUID")
            Toast.makeText(this, "Movesense service not found", Toast.LENGTH_SHORT).show()
            return
        }

        val writeChar = service.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
        val notifyChar = service.getCharacteristic(NOTIFY_CHARACTERISTIC_UUID)

        if (writeChar != null && notifyChar != null) {
            isStreaming = true
            dataList.clear()
            stepCount = 0
            peakImpact = 0f
            startTime = System.currentTimeMillis()
            cadenceWindow.clear()
            updateToggleButton()

            // 1. Enable notifications for Notify Characteristic
            bluetoothGatt?.setCharacteristicNotification(notifyChar, true)
            val descriptor = notifyChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
            if (descriptor != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    bluetoothGatt?.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt?.writeDescriptor(descriptor)
                }
            }
            // 2. Subscribe command will be sent in onDescriptorWrite
            
            runOnUiThread { Toast.makeText(this, "Streaming started", Toast.LENGTH_SHORT).show() }
        } else {
            val errorMsg = "Characteristics not found. Write: ${writeChar != null}, Notify: ${notifyChar != null}"
            Log.e(TAG, errorMsg)
            Toast.makeText(this, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    private fun processRawData(bytes: ByteArray?) {
        if (bytes == null || bytes.size < 26) return

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.get() // Skip 1st byte
        buffer.get() // Skip 2nd byte
        
        val axG = buffer.float
        val ayG = buffer.float
        val azG = buffer.float
        val gx = buffer.float
        val gy = buffer.float
        val gz = buffer.float

        // Log the parsed values to verify sensor data flow
        Log.d("IMU", "Parsed: Acc(%.2f, %.2f, %.2f) Gyr(%.2f, %.2f, %.2f)".format(axG, ayG, azG, gx, gy, gz))

        val currentTime = System.currentTimeMillis()
        val mag = sqrt(axG.pow(2) + ayG.pow(2) + azG.pow(2))
        
        if (lastAccMag > 1.2f && mag < 1.2f) {
            stepCount++
            cadenceWindow.add(currentTime)
            cadenceWindow.removeAll { it < currentTime - 10000 }
        }
        lastAccMag = mag
        if (mag > peakImpact) peakImpact = mag

        val cadence = if (cadenceWindow.size > 1) {
            (cadenceWindow.size.toFloat() / 10f) * 60f
        } else 0f

        dataList.add(SensorData(currentTime, axG, ayG, azG, gx, gy, gz, stepCount, cadence, peakImpact))

        runOnUiThread {
            // Update ALL text views to ensure real-time feedback
            tvAcc.text = String.format("%.2f, %.2f, %.2f", axG, ayG, azG)
            tvGyr.text = String.format("%.2f, %.2f, %.2f", gx, gy, gz)
            val elapsed = (System.currentTimeMillis() - startTime) / 1000
            tvSteps.text = stepCount.toString()
            tvTime.text = "${elapsed}s"
            tvCadence.text = cadence.toInt().toString()
            tvPeak.text = String.format("%.2fg", peakImpact)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopStreaming() {
        if (!isStreaming) return
        isStreaming = false
        updateToggleButton()
        
        val service = bluetoothGatt?.getService(MOVESENSE_SERVICE_UUID)
        val writeChar = service?.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
        if (writeChar != null) {
             // Unsubscribe: 0x02, RequestID 0x01
             val unsubscribeCommand = byteArrayOf(0x02, 0x01)
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                 bluetoothGatt?.writeCharacteristic(writeChar, unsubscribeCommand, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
             } else {
                 @Suppress("DEPRECATION")
                 writeChar.value = unsubscribeCommand
                 bluetoothGatt?.writeCharacteristic(writeChar)
             }
        }
        
        saveToCsv()
    }

    @SuppressLint("MissingPermission")
    private fun sendSubscribeCommand() {
        if (!isStreaming) return
        val service = bluetoothGatt?.getService(MOVESENSE_SERVICE_UUID)
        val writeChar = service?.getCharacteristic(WRITE_CHARACTERISTIC_UUID)
        if (writeChar != null) {
            val path = "/Meas/IMU6/104"
            val pathBytes = path.toByteArray(Charsets.UTF_8)
            val subscribeCommand = ByteArray(2 + pathBytes.size)
            subscribeCommand[0] = 0x01  // REQUEST_TYPE_SUBSCRIBE
            subscribeCommand[1] = 99    // request ID
            pathBytes.copyInto(subscribeCommand, 2)
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                bluetoothGatt?.writeCharacteristic(writeChar, subscribeCommand, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
            } else {
                @Suppress("DEPRECATION")
                writeChar.value = subscribeCommand
                bluetoothGatt?.writeCharacteristic(writeChar)
            }
        }
    }

    private fun updateToggleButton() {
        runOnUiThread {
            if (isStreaming) {
                btnToggle.text = "STOP"
                btnToggle.backgroundTintList = ColorStateList.valueOf(Color.RED)
            } else {
                btnToggle.text = "START"
                btnToggle.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        closeGatt()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        bluetoothGatt?.close()
        bluetoothGatt = null
    }

    private fun saveToCsv() {
        val fileName = "Session_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
        val file = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        
        try {
            FileWriter(file).use { writer ->
                writer.append("timestamp_ms,accX,accY,accZ,gyrX,gyrY,gyrZ,step_count,cadence_spm,peak_impact_g\n")
                dataList.forEach { d ->
                    writer.append("${d.ts},${d.ax},${d.ay},${d.az},${d.gx},${d.gy},${d.gz},${d.steps},${d.cadence},${d.peak}\n")
                }
            }
            Toast.makeText(this, "Saved to ${file.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving CSV", e)
        }
    }

    data class SensorData(val ts: Long, val ax: Float, val ay: Float, val az: Float, val gx: Float, val gy: Float, val gz: Float, val steps: Int, val cadence: Float, val peak: Float)
}
