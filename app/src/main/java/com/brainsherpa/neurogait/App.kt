package com.brainsherpa.neurogait

import android.app.Application
import com.movesense.mds.Mds

class App : Application() {

    lateinit var mds: Mds
        private set

    override fun onCreate() {
        super.onCreate()
        // Single Mds instance for the whole app (Whiteboard-over-BLE transport).
        mds = Mds.builder().build(this)
        // Load any persisted Supabase session so the sign-in gate can check it.
        try {
            SupabaseClient.initBlocking(this)
        } catch (e: Exception) {
            android.util.Log.e("App", "Supabase init failed: ${e.message}")
        }
    }
}
