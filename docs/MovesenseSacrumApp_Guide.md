# Movesense Sacrum Biomechanics App
## Complete Android Studio Developer Guide
### Pace · Impact Force · Gyroscopic Output

---

## Overview

This guide walks you through building a native Android (Kotlin/Java) app that connects to a **Movesense** sensor strapped at the **sacrum** and streams three derived outputs in real-time:

| Output | Sensor Source | Algorithm |
|--------|--------------|-----------|
| **Pace** (steps/min, min/km) | Accelerometer | Step detection via vertical-axis peak detection |
| **Impact Force** (g-force, estimated N) | Accelerometer | Peak resultant vector on heel-strike |
| **Gyroscopic orientation** | Gyroscope | Real-time roll/pitch/yaw from `Meas/Gyro` |

---

## Key Resources

### Official Documentation
- **Android Developer Guide**: https://www.movesense.com/docs/mobile/android/main/
- **API Reference (sensor endpoints)**: https://www.movesense.com/docs/esw/api_reference/
- **MDS Mobile Library (AAR download)**: https://bitbucket.org/movesense/movesense-mobile-lib/downloads/
- **Axis Orientation**: https://www.movesense.com/docs/system/axis_orientation/

### GitHub Reference Projects
- **Sample Android App (ESSI_APP)**: https://github.com/juliaslocke/ESSI_APP — Full working app with subscribe/unsubscribe to all sensor endpoints
- **holmmi/Movesense**: https://github.com/holmmi/Movesense — Real-world study app with IMU streaming
- **RussellHii/Movesense-Android**: https://github.com/RussellHii/Movesense-Android — Minimal Android integration
- **yalchinAlv/MovesenseConnect**: https://github.com/yalchinAlv/MovesenseConnect — Subscription + real-time graph
- **mxandy/movesense**: https://github.com/mxandy/movesense — Web-based data analysis reference

---

## Part 1 — Project Setup in Android Studio

### Step 0: Create the Project

1. Open Android Studio → **New Project** → **Empty Activity**
2. Language: **Kotlin** (or Java)
3. Minimum SDK: **API 21** (Android 5.0 Lollipop) — required by MDS library
4. Package name: e.g. `com.yourorg.movesensesacrum`

### Step 1: Download & Add the MDS Library

1. Go to: https://bitbucket.org/movesense/movesense-mobile-lib/downloads/
2. Download the latest `mdslib-x.y.z-release.aar`
3. In Android Studio: **File → New → New Module → Import .JAR/.AAR Package**
4. Select the `.aar` file; the module will be named `mdslib`
5. In `app/build.gradle` add:

```groovy
dependencies {
    implementation project(':mdslib')
    // BLE scanning
    implementation 'com.polidea.rxandroidble2:rxandroidble:1.17.2'
    // JSON parsing
    implementation 'com.google.code.gson:gson:2.10.1'
    // Coroutines (for Kotlin)
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
}
```

6. In **root** `build.gradle` (or `settings.gradle` for newer AGP):

```groovy
allprojects {
    repositories {
        google()
        mavenCentral()
        flatDir { dirs 'libs' }   // if you place the AAR in app/libs instead
    }
}
```

### Step 2: Configure Permissions

In `AndroidManifest.xml`:

```xml
<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30"/>
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT"/>

<!-- Location needed for BLE scanning on API < 31 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION"/>
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION"/>

<!-- For saving CSV logs -->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE"
    android:maxSdkVersion="28"/>

<uses-feature android:name="android.hardware.bluetooth_le" android:required="true"/>
```

Add to your `<application>` tag:
```xml
<application
    android:name=".App"
    ...>
```

### Step 3: Create the Application Class

```kotlin
// App.kt
class App : Application() {
    lateinit var mds: Mds

    override fun onCreate() {
        super.onCreate()
        mds = Mds.builder().build(this)
    }
}
```

---

## Part 2 — Sensor API Endpoints

The Movesense uses a REST-style API over BLE. For a device with serial `174730000410`, all URIs are prefixed with the serial number.

### Relevant Endpoints for Sacrum Placement

```
GET  Meas/Acc/Info          → available sample rates [13,26,52,104,208,416,833,1666] Hz
                               and ranges [2,4,8,16] g

GET  Meas/Gyro/Info         → available sample rates, ranges [245,500,1000,2000] °/s

GET  Meas/IMU/Info          → combined info for Acc + Gyro + Magnetometer

SUB  Meas/Acc/52            → accelerometer at 52 Hz (recommended for gait)
SUB  Meas/Gyro/52           → gyroscope at 52 Hz
SUB  Meas/IMU6/52           → synchronized Acc + Gyro at 52 Hz (most efficient)
SUB  Meas/IMU9/52           → synchronized Acc + Gyro + Magnetometer at 52 Hz
```

**Recommendation for sacrum gait analysis:**
- Use **`Meas/IMU6/104`** — synchronized accelerometer + gyroscope at 104 Hz
- This is more efficient than subscribing separately and ensures timestamp synchronisation
- 104 Hz captures heel-strike transients accurately without excessive BLE bandwidth

---

## Part 3 — BLE Scanning & Connection

```kotlin
// ScanActivity.kt (simplified)
class ScanActivity : AppCompatActivity() {

    private val rxBleClient by lazy { RxBleClient.create(this) }
    private val foundDevices = mutableListOf<RxBleScanResult>()
    private var scanDisposable: Disposable? = null
    private val mds get() = (application as App).mds

    private fun startScan() {
        // Request runtime permissions first (BLUETOOTH_SCAN, ACCESS_FINE_LOCATION)
        scanDisposable = rxBleClient.scanBleDevices(
            ScanSettings.Builder().build()
        )
        .filter { it.bleDevice.name?.startsWith("Movesense") == true }
        .subscribe({ result ->
            // Add to list, update RecyclerView adapter
            if (foundDevices.none { it.bleDevice.macAddress == result.bleDevice.macAddress }) {
                foundDevices.add(result)
                adapter.notifyDataSetChanged()
            }
        }, { error ->
            Log.e("Scan", "Error: $error")
        })
    }

    fun connectToDevice(macAddress: String) {
        mds.connect(macAddress, object : MdsConnectionListener {
            override fun onConnect(address: String) {
                Log.d("MDS", "BLE connected: $address")
            }
            override fun onConnectionComplete(address: String, serial: String) {
                // serial is e.g. "174730000410"
                // Navigate to main monitoring screen, pass serial
                val intent = Intent(this@ScanActivity, MonitorActivity::class.java)
                intent.putExtra("SERIAL", serial)
                startActivity(intent)
            }
            override fun onError(e: MdsException) {
                Log.e("MDS", "Connection error: $e")
            }
            override fun onDisconnect(address: String) {
                Log.d("MDS", "Disconnected: $address")
            }
        })
    }

    override fun onDestroy() {
        super.onDestroy()
        scanDisposable?.dispose()
    }
}
```

---

## Part 4 — IMU Subscription & Data Parsing

### Data Models

```kotlin
// IMU6Sample.kt
data class IMU6Sample(
    val timestamp: Long,
    val accX: Float, val accY: Float, val accZ: Float,  // in g
    val gyrX: Float, val gyrY: Float, val gyrZ: Float   // in °/s
)

// BiomechanicsOutput.kt
data class BiomechanicsOutput(
    val timestamp: Long,
    val pace: Float,          // steps per minute
    val paceMinkm: Float,     // min/km (estimated)
    val impactForce: Float,   // peak g
    val impactNewtons: Float, // estimated Newtons (body mass dependent)
    val roll: Float,          // degrees
    val pitch: Float,         // degrees
    val yaw: Float            // degrees (integrated, drifts without magnetometer)
)
```

### JSON Response Structure

The IMU6 subscription delivers JSON like:

```json
{
  "Body": {
    "Timestamp": 12345678,
    "ArrayAcc": [
      {"x": 0.12, "y": -0.05, "z": 9.81}
    ],
    "ArrayGyro": [
      {"x": 1.2, "y": -0.3, "z": 0.05}
    ]
  }
}
```

### MonitorActivity — Subscription Core

```kotlin
// MonitorActivity.kt
class MonitorActivity : AppCompatActivity() {

    private val mds get() = (application as App).mds
    private var imuSubscription: MdsSubscription? = null
    private lateinit var serial: String

    // Algorithm state
    private val analyzer = SacrumBiomechanicsAnalyzer()

    // UI
    private lateinit var tvPace: TextView
    private lateinit var tvImpact: TextView
    private lateinit var tvRoll: TextView
    private lateinit var tvPitch: TextView
    private lateinit var tvYaw: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_monitor)
        serial = intent.getStringExtra("SERIAL") ?: return

        tvPace   = findViewById(R.id.tvPace)
        tvImpact = findViewById(R.id.tvImpact)
        tvRoll   = findViewById(R.id.tvRoll)
        tvPitch  = findViewById(R.id.tvPitch)
        tvYaw    = findViewById(R.id.tvYaw)

        subscribeIMU()
    }

    private fun subscribeIMU() {
        val sampleRate = 104  // Hz — captures heel-strike transients
        val uri = "suunto://MDS/EventListener"
        val contract = """{"Uri": "$serial/Meas/IMU6/$sampleRate"}"""

        imuSubscription = mds.subscribe(uri, contract, object : MdsNotificationListener {
            override fun onNotification(data: String) {
                parseAndProcess(data)
            }
            override fun onError(e: MdsException) {
                Log.e("IMU", "Subscription error: $e")
            }
        })
    }

    private fun parseAndProcess(json: String) {
        try {
            val body = JSONObject(json).getJSONObject("Body")
            val timestamp = body.getLong("Timestamp")
            val accArr = body.getJSONArray("ArrayAcc")
            val gyrArr = body.getJSONArray("ArrayGyro")

            // Process each sample in the batch (IMU delivers batches)
            for (i in 0 until accArr.length()) {
                val acc = accArr.getJSONObject(i)
                val gyr = gyrArr.getJSONObject(i)

                val sample = IMU6Sample(
                    timestamp = timestamp + (i * (1000L / 104)),
                    accX = acc.getDouble("x").toFloat(),
                    accY = acc.getDouble("y").toFloat(),
                    accZ = acc.getDouble("z").toFloat(),
                    gyrX = gyr.getDouble("x").toFloat(),
                    gyrY = gyr.getDouble("y").toFloat(),
                    gyrZ = gyr.getDouble("z").toFloat()
                )

                val output = analyzer.processSample(sample)
                if (output != null) {
                    runOnUiThread { updateUI(output) }
                }
            }
        } catch (e: JSONException) {
            Log.e("Parse", "JSON error: $e")
        }
    }

    private fun updateUI(out: BiomechanicsOutput) {
        tvPace.text   = "Pace: %.0f spm  (%.1f min/km)".format(out.pace, out.paceMinkm)
        tvImpact.text = "Impact: %.2f g  (~%.0f N".format(out.impactForce, out.impactNewtons)
        tvRoll.text   = "Roll:  %.1f°".format(out.roll)
        tvPitch.text  = "Pitch: %.1f°".format(out.pitch)
        tvYaw.text    = "Yaw:   %.1f°".format(out.yaw)
    }

    override fun onDestroy() {
        super.onDestroy()
        imuSubscription?.unsubscribe()
    }
}
```

---

## Part 5 — Biomechanics Algorithm

This is the core processing class. Sacrum placement is ideal for gait analysis because the sacrum is close to the body's centre of mass, minimising soft-tissue artefact.

```kotlin
// SacrumBiomechanicsAnalyzer.kt
class SacrumBiomechanicsAnalyzer(
    private val bodyMassKg: Float = 70f   // configurable per user
) {
    // ── Configuration ──────────────────────────────────────────────
    private val SAMPLE_RATE_HZ = 104f
    private val GRAVITY = 9.81f

    // Step detection: vertical axis (Z at sacrum, pointing superior)
    // Tune these thresholds for walking vs running
    private val STEP_THRESHOLD_G  = 1.3f   // minimum peak to count as step
    private val STEP_MIN_INTERVAL_MS = 250L // min 250ms between steps (~4 Hz max)
    private val IMPACT_WINDOW_SAMPLES = 10  // look 10 samples around peak for max

    // ── State ───────────────────────────────────────────────────────
    private val accBuffer = ArrayDeque<FloatArray>(300) // rolling 3s window at 104Hz
    private var lastStepTime = 0L
    private var stepCount = 0
    private val stepTimes = ArrayDeque<Long>(20)       // last 20 step timestamps

    // Gyro integration for yaw (simple Euler; use Madgwick for production)
    private var roll  = 0f
    private var pitch = 0f
    private var yaw   = 0f
    private var lastTimestamp = 0L

    // Impact
    private var peakImpactG = 0f
    private var outputEveryN = 26   // emit output ~4× per second at 104Hz

    private var sampleCount = 0

    fun processSample(s: IMU6Sample): BiomechanicsOutput? {
        val dt = if (lastTimestamp == 0L) (1f / SAMPLE_RATE_HZ)
                 else ((s.timestamp - lastTimestamp) / 1000f).coerceIn(0.001f, 0.1f)
        lastTimestamp = s.timestamp

        // 1. Compute resultant acceleration magnitude
        val resultant = Math.sqrt(
            (s.accX * s.accX + s.accY * s.accY + s.accZ * s.accZ).toDouble()
        ).toFloat()

        // Buffer for step detection
        accBuffer.addLast(floatArrayOf(s.accZ, resultant, s.timestamp.toFloat()))
        if (accBuffer.size > 300) accBuffer.removeFirst()

        // 2. Track peak impact in this window
        if (resultant > peakImpactG) peakImpactG = resultant

        // 3. Step detection on vertical axis (Z at sacrum)
        detectStep(s.accZ, s.timestamp)

        // 4. Integrate gyroscope → orientation (Euler, deg/s → deg)
        roll  += s.gyrX * dt
        pitch += s.gyrY * dt
        yaw   += s.gyrZ * dt

        // Wrap yaw to [-180, 180]
        if (yaw > 180f)  yaw -= 360f
        if (yaw < -180f) yaw += 360f

        // 5. Emit output at ~4 Hz
        sampleCount++
        if (sampleCount % outputEveryN == 0) {
            val (spm, minKm) = computePace()
            val impactN = peakImpactG * bodyMassKg * GRAVITY
            val out = BiomechanicsOutput(
                timestamp     = s.timestamp,
                pace          = spm,
                paceMinkm     = minKm,
                impactForce   = peakImpactG,
                impactNewtons = impactN,
                roll          = roll,
                pitch         = pitch,
                yaw           = yaw
            )
            peakImpactG = 0f  // reset impact window
            return out
        }
        return null
    }

    // ── Step Detection ────────────────────────────────────────────
    // Simple peak detection on Z-axis (superior-inferior at sacrum).
    // A "step" = positive peak above threshold separated by minimum interval.
    private var prevZ = 0f
    private var prevPrevZ = 0f
    private var rising = false

    private fun detectStep(zG: Float, timestamp: Long) {
        val isPeak = prevZ > prevPrevZ && prevZ > zG && prevZ > STEP_THRESHOLD_G
        if (isPeak) {
            val timeSinceLast = timestamp - lastStepTime
            if (timeSinceLast > STEP_MIN_INTERVAL_MS) {
                lastStepTime = timestamp
                stepTimes.addLast(timestamp)
                if (stepTimes.size > 20) stepTimes.removeFirst()
                stepCount++
            }
        }
        prevPrevZ = prevZ
        prevZ = zG
    }

    // ── Pace Computation ─────────────────────────────────────────
    // Cadence from recent step intervals → steps/min → min/km
    // Stride length estimated from cadence (Cavagna model, approximate)
    private fun computePace(): Pair<Float, Float> {
        if (stepTimes.size < 2) return Pair(0f, 0f)

        val intervals = (1 until stepTimes.size).map {
            (stepTimes[it] - stepTimes[it - 1]).toFloat()
        }
        val avgIntervalMs = intervals.average().toFloat()
        if (avgIntervalMs <= 0) return Pair(0f, 0f)

        val spm = 60000f / avgIntervalMs    // steps per minute

        // Estimate stride length (Grieve & Gear, 1966 approximation):
        // stride_length ≈ 0.0027 × cadence_in_steps_per_min + 0.43 (metres, for adult)
        // More accurate: use height * 0.413 * (cadence/120)^0.45 — requires user height
        val strideM = 0.0027f * spm + 0.43f   // metres per stride (2 steps)
        val speedMs = (spm / 2f) * strideM / 60f   // m/s
        val minKm = if (speedMs > 0.1f) (1000f / speedMs) / 60f else 0f

        return Pair(spm, minKm)
    }

    fun reset() {
        accBuffer.clear(); stepTimes.clear()
        roll = 0f; pitch = 0f; yaw = 0f
        stepCount = 0; peakImpactG = 0f; sampleCount = 0
        lastStepTime = 0; lastTimestamp = 0
    }
}
```

---

## Part 6 — Layout XML

### `res/layout/activity_monitor.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="24dp"
    android:gravity="center_horizontal"
    android:background="#121212">

    <!-- Connection status -->
    <TextView
        android:id="@+id/tvStatus"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Connected"
        android:textColor="#4CAF50"
        android:textSize="14sp"
        android:layout_marginBottom="24dp"/>

    <!-- PACE -->
    <TextView
        android:text="PACE"
        android:textColor="#888"
        android:textSize="12sp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
    <TextView
        android:id="@+id/tvPace"
        android:text="-- spm"
        android:textColor="#FFFFFF"
        android:textSize="36sp"
        android:textStyle="bold"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="24dp"/>

    <!-- IMPACT FORCE -->
    <TextView
        android:text="IMPACT FORCE"
        android:textColor="#888"
        android:textSize="12sp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
    <TextView
        android:id="@+id/tvImpact"
        android:text="-- g"
        android:textColor="#FF5722"
        android:textSize="36sp"
        android:textStyle="bold"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="24dp"/>

    <!-- GYROSCOPE -->
    <TextView
        android:text="GYROSCOPE (Roll / Pitch / Yaw)"
        android:textColor="#888"
        android:textSize="12sp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
    <TextView
        android:id="@+id/tvRoll"
        android:text="Roll:  --°"
        android:textColor="#03A9F4"
        android:textSize="24sp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
    <TextView
        android:id="@+id/tvPitch"
        android:text="Pitch: --°"
        android:textColor="#03A9F4"
        android:textSize="24sp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"/>
    <TextView
        android:id="@+id/tvYaw"
        android:text="Yaw:   --°"
        android:textColor="#03A9F4"
        android:textSize="24sp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginBottom="32dp"/>

    <!-- Controls -->
    <Button
        android:id="@+id/btnDisconnect"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="Disconnect"
        android:backgroundTint="#F44336"/>

</LinearLayout>
```

---

## Part 7 — Sacrum Placement & Axis Orientation

The Movesense uses a right-handed coordinate system. When strapped at the **sacrum** (posterior pelvis, midline, between PSIS landmarks):

```
Movesense orientation at sacrum (sensor face pointing posteriorly, top = superior):

  +Z = Superior (up, cranial)          ← main axis for step detection
  +Y = Left (medial-lateral)           ← pelvic obliquity
  +X = Anterior (anterior-posterior)   ← sagittal plane motion

Gyroscope at sacrum:
  GyrX = Roll  (sagittal rotation — lumbar flexion/extension rhythm)
  GyrY = Pitch (frontal rotation — pelvic drop, Trendelenburg)
  GyrZ = Yaw   (transverse rotation — pelvic rotation during gait)
```

**Practical mounting notes:**
- Use a wide elastic sacral belt or tape the sensor flat against the skin over S1-S2
- Mark the superior pole of the sensor (small dot/arrow) pointing toward the head
- Ensure the sensor doesn't rotate — any twist invalidates the axis interpretation
- The `accZ` channel (superior axis) gives the cleanest step detection signal at the sacrum, with a characteristic double-peak per stance phase at running speeds

---

## Part 8 — Recommended Sample Rates by Activity

| Activity | Acc Rate | Gyro Rate | Endpoint | Notes |
|----------|----------|-----------|----------|-------|
| Walking  | 52 Hz    | 52 Hz     | `IMU6/52`  | Sufficient; saves battery |
| Running  | 104 Hz   | 104 Hz    | `IMU6/104` | Captures heel-strike peak |
| Sprinting| 208 Hz   | 208 Hz    | `IMU6/208` | High BLE bandwidth; test range first |

The accelerometer range should be set to **±8g** for running (default ±2g clips during hard heel-strikes):
```
PUT  Meas/Acc/Config   body: {"GRange": 8}
```

---

## Part 9 — Exporting Data to CSV

Add this utility to log all samples for offline analysis (biomechanics research):

```kotlin
// CsvLogger.kt
class CsvLogger(context: Context, filename: String) {
    private val file = File(context.getExternalFilesDir(null), filename)
    private val writer = BufferedWriter(FileWriter(file, true))

    init {
        writer.write("timestamp,accX,accY,accZ,gyrX,gyrY,gyrZ,pace_spm,impact_g,roll,pitch,yaw\n")
    }

    fun log(s: IMU6Sample, out: BiomechanicsOutput?) {
        writer.write(
            "${s.timestamp},${s.accX},${s.accY},${s.accZ}," +
            "${s.gyrX},${s.gyrY},${s.gyrZ}," +
            "${out?.pace ?: ""},${out?.impactForce ?: ""}," +
            "${out?.roll ?: ""},${out?.pitch ?: ""},${out?.yaw ?: ""}\n"
        )
    }

    fun close() = writer.close()
}
```

---

## Part 10 — Production Improvements

### Replace Euler Integration with Madgwick Filter
Simple gyro integration drifts rapidly (yaw especially). For production, replace the Euler integration with the **Madgwick AHRS** algorithm:

```kotlin
// Add to build.gradle:
// No standard library — implement or copy from:
// https://github.com/xioTechnologies/Fusion (C, wrap via JNI)
// or: https://github.com/kriswiner/MPU6050 (Java port exists)

// Madgwick requires beta tuning parameter:
val madgwick = MadgwickAHRS(sampleFreq = 104f, beta = 0.1f)
madgwick.update(gyrX, gyrY, gyrZ, accX, accY, accZ)
val (roll, pitch, yaw) = madgwick.getEulerAngles()
```

### Step Detection Robustness
Replace the simple peak detector with a **bandpass filtered** approach:
- Bandpass 0.5–3 Hz to isolate step frequency
- Use autocorrelation to find the dominant period
- More robust to speed changes and artefacts

### Impact Force Calibration
The estimate `F = peak_g × body_mass × 9.81` is approximate. Validated values from literature for sacrum placement:
- Walking heel-strike: ~1.2–1.8g
- Running heel-strike: ~2.5–4.5g  
- Forefoot running: 1.5–2.5g (lower peak, higher rate of loading)

---

## Part 11 — Troubleshooting

| Problem | Likely Cause | Fix |
|---------|-------------|-----|
| Device not found in scan | BLE timeout (~15-20s) | Restart Movesense (tap button once) |
| `onError` on subscription | Wrong serial or URI | Log `serial` from `onConnectionComplete` |
| Impact reads ~1.0g at rest | Gravity — expected | Subtract 1.0 from Z when device is upright |
| Yaw drifts to 180° | Gyro integration drift | Implement Madgwick filter |
| No steps detected | Sensor mounted upside down | Check axis orientation; try `-accZ` |
| App crashes on BLE scan | Missing runtime permission | Add `ActivityCompat.requestPermissions` for `BLUETOOTH_SCAN` |

---

## Quick Reference: MDS Subscription URI Pattern

```
suunto://MDS/EventListener
Contract body: {"Uri": "<SERIAL>/<ENDPOINT>"}

Examples:
{"Uri": "174730000410/Meas/IMU6/104"}     ← IMU Acc+Gyro 104Hz
{"Uri": "174730000410/Meas/Acc/52"}       ← Accelerometer only 52Hz
{"Uri": "174730000410/Meas/Gyro/52"}      ← Gyroscope only 52Hz
{"Uri": "174730000410/Meas/IMU9/52"}      ← Acc+Gyro+Magnetometer 52Hz
```

---

*Sources: Movesense Developer Documentation, Bitbucket MDS library, ESSI_APP, holmmi/Movesense, peer-reviewed gait literature (sacrum accelerometry: Kavanagh & Menz 2008; impact force estimation: Milner et al. 2006)*
