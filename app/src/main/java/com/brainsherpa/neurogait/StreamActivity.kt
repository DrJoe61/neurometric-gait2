package com.brainsherpa.neurogait

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.movesense.mds.Mds
import com.movesense.mds.MdsConnectionListener
import com.movesense.mds.MdsException
import com.movesense.mds.MdsNotificationListener
import com.movesense.mds.MdsSubscription
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Streams IMU6 (accelerometer + gyroscope) from a Movesense sensor using the
 * official MDS (Movesense) library instead of raw BLE GATT.
 *
 * Flow:  MDS connect -> onConnectionComplete gives the serial -> tap START ->
 *        mds.subscribe("suunto://MDS/EventListener", {"Uri":"<serial>/Meas/IMU6/104"})
 *        -> onNotification delivers JSON with ArrayAcc / ArrayGyro.
 */
class StreamActivity : AppCompatActivity() {

    private val mds: Mds get() = (application as App).mds
    private var deviceAddress: String? = null
    private var serial: String? = null
    private var subscription: MdsSubscription? = null
    private var isConnected = false

    private val dataList = mutableListOf<SensorData>()
    private var isStreaming = false
    private var stepCount = 0
    private var lastAccMag = 0f
    private var startTime = 0L
    private var peakImpact = 0f
    private val cadenceWindow = mutableListOf<Long>()

    private val TAG = "StreamActivity"
    private val GRAVITY = 9.80665f   // Movesense accelerometer reports m/s^2; divide to get g

    private lateinit var tvAcc: TextView
    private lateinit var tvGyr: TextView
    private lateinit var tvSteps: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvCadence: TextView
    private lateinit var tvPeak: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnToggle: Button
    private var lastDataMs = 0L

    // Wall-clock timer so ELAPSED TIME ticks even before/without data.
    private val uiHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            if (isStreaming) {
                val elapsed = (System.currentTimeMillis() - startTime) / 1000
                tvTime.text = "${elapsed}s"
                // Watchdog: if no sensor packet for >4s, the stream stalled - say so instead of lying.
                val stalled = System.currentTimeMillis() - lastDataMs > 4000
                if (stalled) {
                    tvStatus.text = "⚠ Signal lost — hold still / move closer"
                    tvStatus.setTextColor(Color.parseColor("#FF9800"))
                } else {
                    tvStatus.text = "● Streaming (live)"
                    tvStatus.setTextColor(Color.parseColor("#4CAF50"))
                }
                uiHandler.postDelayed(this, 1000)
            }
        }
    }

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
        tvStatus = findViewById(R.id.tv_status)
        btnToggle = findViewById(R.id.btn_toggle)

        btnToggle.setOnClickListener {
            if (isStreaming) stopStreaming() else startStreaming()
        }
        findViewById<Button>(R.id.btn_exit).setOnClickListener {
            if (isStreaming) {
                stopStreaming()   // stops, then the Save-and-exit dialog takes over
            } else {
                finishAffinity()  // actually close the app, not just return to the scan screen
            }
        }
        // Keep header/buttons clear of the status bar and nav bar.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.stream_root)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        connectSensor()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun connectSensor() {
        val addr = deviceAddress
        if (addr == null) {
            Toast.makeText(this, "No device address", Toast.LENGTH_LONG).show()
            return
        }
        Log.i(TAG, "MDS connecting to $addr")
        mds.connect(addr, object : MdsConnectionListener {
            override fun onConnect(s: String?) {
                Log.i(TAG, "onConnect: $s")
            }

            override fun onConnectionComplete(macAddress: String?, serialNumber: String?) {
                serial = serialNumber
                isConnected = true
                Log.i(TAG, "onConnectionComplete mac=$macAddress serial=$serialNumber")
                runOnUiThread {
                    Toast.makeText(
                        this@StreamActivity,
                        "Device ready ($serialNumber). Tap START.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onError(e: MdsException?) {
                Log.e(TAG, "MDS connection error", e)
                runOnUiThread {
                    Toast.makeText(this@StreamActivity, "Connect error: ${e?.message}", Toast.LENGTH_LONG).show()
                }
            }

            override fun onDisconnect(s: String?) {
                isConnected = false
                Log.i(TAG, "onDisconnect: $s")
            }
        })
    }

    private fun startStreaming() {
        if (isStreaming) return
        val ser = serial
        if (!isConnected || ser == null) {
            Toast.makeText(this, "Still connecting to sensor…", Toast.LENGTH_SHORT).show()
            return
        }

        isStreaming = true
        dataList.clear()
        stepCount = 0
        peakImpact = 0f
        lastAccMag = 0f
        cadenceWindow.clear()
        startTime = System.currentTimeMillis()
        lastDataMs = System.currentTimeMillis()
        updateToggleButton()
        uiHandler.post(timerTick)

        val uri = "suunto://MDS/EventListener"
        val contract = "{\"Uri\":\"$ser/Meas/IMU6/104\"}"
        Log.i(TAG, "Subscribing: $contract")
        subscription = mds.subscribe(uri, contract, object : MdsNotificationListener {
            override fun onNotification(data: String?) {
                if (data != null) parseAndProcess(data)
            }

            override fun onError(e: MdsException?) {
                Log.e(TAG, "Subscription error", e)
                runOnUiThread {
                    Toast.makeText(this@StreamActivity, "Stream error: ${e?.message}", Toast.LENGTH_LONG).show()
                    isStreaming = false
                    updateToggleButton()
                }
            }
        })
        Toast.makeText(this, "Streaming started", Toast.LENGTH_SHORT).show()
    }

    private fun parseAndProcess(json: String) {
        try {
            val root = JSONObject(json)
            val body = root.optJSONObject("Body") ?: root
            val accArr = body.optJSONArray("ArrayAcc")
            val gyrArr = body.optJSONArray("ArrayGyro")

            if (accArr == null || accArr.length() == 0) {
                Log.d("IMU", "Notification w/o ArrayAcc: ${json.take(140)}")
                return
            }

            val n = accArr.length()
            var lastAxG = 0f; var lastAyG = 0f; var lastAzG = 0f
            var lastGx = 0f; var lastGy = 0f; var lastGz = 0f
            val now = System.currentTimeMillis()

            for (i in 0 until n) {
                val a = accArr.getJSONObject(i)
                val axG = a.getDouble("x").toFloat() / GRAVITY
                val ayG = a.getDouble("y").toFloat() / GRAVITY
                val azG = a.getDouble("z").toFloat() / GRAVITY
                var gx = 0f; var gy = 0f; var gz = 0f
                if (gyrArr != null && i < gyrArr.length()) {
                    val g = gyrArr.getJSONObject(i)
                    gx = g.getDouble("x").toFloat()
                    gy = g.getDouble("y").toFloat()
                    gz = g.getDouble("z").toFloat()
                }
                lastAxG = axG; lastAyG = ayG; lastAzG = azG
                lastGx = gx; lastGy = gy; lastGz = gz

                val mag = sqrt(axG.pow(2) + ayG.pow(2) + azG.pow(2))
                if (lastAccMag > 1.2f && mag < 1.2f) {
                    stepCount++
                    cadenceWindow.add(now)
                    cadenceWindow.removeAll { it < now - 10000 }
                }
                lastAccMag = mag
                if (mag > peakImpact) peakImpact = mag

                val cadenceSample = if (cadenceWindow.size > 1) (cadenceWindow.size.toFloat() / 10f) * 60f else 0f
                dataList.add(SensorData(now, axG, ayG, azG, gx, gy, gz, stepCount, cadenceSample, peakImpact))
            }

            Log.d("IMU", "Batch %d samples | Acc(%.2f,%.2f,%.2f)g Gyr(%.1f,%.1f,%.1f)"
                .format(n, lastAxG, lastAyG, lastAzG, lastGx, lastGy, lastGz))

            lastDataMs = System.currentTimeMillis()   // watchdog: mark that fresh data arrived
            val cadence = if (cadenceWindow.size > 1) (cadenceWindow.size.toFloat() / 10f) * 60f else 0f
            runOnUiThread {
                tvAcc.text = String.format("%.2f, %.2f, %.2f", lastAxG, lastAyG, lastAzG)
                tvGyr.text = String.format("%.2f, %.2f, %.2f", lastGx, lastGy, lastGz)
                tvSteps.text = stepCount.toString()
                tvCadence.text = cadence.toInt().toString()
                tvPeak.text = String.format("%.2f", peakImpact)
            }
        } catch (e: Exception) {
            Log.e("IMU", "Parse error: ${e.message} | ${json.take(160)}")
        }
    }

    /** STOP: halt the stream, then ask whether to save + exit. */
    private fun stopStreaming() {
        if (!isStreaming) return
        isStreaming = false
        updateToggleButton()
        uiHandler.removeCallbacks(timerTick)
        subscription?.unsubscribe()
        subscription = null
        promptSaveAndExit()
    }

    private fun promptSaveAndExit() {
        if (dataList.isEmpty()) {
            Toast.makeText(this, "No data captured — nothing to save", Toast.LENGTH_LONG).show()
            finish()   // back to the scan screen
            return
        }
        AlertDialog.Builder(this)
            .setTitle("Save and exit?")
            .setMessage("Upload this session to your NeuroMetric account and close the app?")
            .setCancelable(false)
            .setPositiveButton("YES") { _, _ -> saveAndExit() }
            .setNegativeButton("NO") { d, _ ->
                d.dismiss()
                Toast.makeText(this, "Session discarded", Toast.LENGTH_SHORT).show()
                finish()   // discard, back to the scan screen
            }
            .show()
    }

    /** Upload the session summary + full raw stream, then close the app. */
    @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
    private fun saveAndExit() {
        Toast.makeText(this, "Saving session…", Toast.LENGTH_SHORT).show()
        val metrics = buildMetrics()
        val recordedAt = metrics.recordedAt
        val samplesCsv = buildSamplesCsv()
        val sampleCount = dataList.size
        GlobalScope.launch(Dispatchers.IO) {
            withTimeoutOrNull(15000) {
                SuiteSyncRepository.pushSessionMetrics(metrics)
                SuiteSyncRepository.pushRawSession(recordedAt, sampleCount, samplesCsv)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(this@StreamActivity, "Session saved ✅", Toast.LENGTH_SHORT).show()
                finishAffinity()   // fully close the app
            }
        }
    }

    private fun buildMetrics(): PaceSessionMetrics {
        val durationS = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        val cadenceAvg = if (durationS > 0) stepCount * 60.0 / durationS else 0.0
        val cadencePeak = if (dataList.isEmpty()) 0.0 else dataList.maxOf { it.cadence }.toDouble()
        val meanImpact = if (dataList.isEmpty()) 0.0
            else dataList.map { sqrt(it.ax.pow(2) + it.ay.pow(2) + it.az.pow(2)) }.average()
        val recordedAt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        return PaceSessionMetrics(
            recordedAt = recordedAt,
            sessionDurationS = durationS,
            stepsTotal = stepCount,
            cadenceSpmAvg = cadenceAvg,
            cadenceSpmPeak = cadencePeak,
            peakImpactG = peakImpact.toDouble(),
            meanImpactG = meanImpact
        )
    }

    /** Same columns the phone CSV used — now carried straight to Supabase instead of a file. */
    private fun buildSamplesCsv(): String {
        val sb = StringBuilder()
        sb.append("timestamp_ms,accX_g,accY_g,accZ_g,gyrX,gyrY,gyrZ,step_count,cadence_spm,peak_impact_g\n")
        dataList.forEach { d ->
            sb.append("${d.ts},${d.ax},${d.ay},${d.az},${d.gx},${d.gy},${d.gz},${d.steps},${d.cadence},${d.peak}\n")
        }
        return sb.toString()
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
        uiHandler.removeCallbacks(timerTick)
        subscription?.unsubscribe()
        try {
            deviceAddress?.let { mds.disconnect(it) }
        } catch (_: Exception) {
        }
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    data class SensorData(
        val ts: Long,
        val ax: Float, val ay: Float, val az: Float,
        val gx: Float, val gy: Float, val gz: Float,
        val steps: Int, val cadence: Float, val peak: Float
    )
}
