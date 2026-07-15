package com.brainsherpa.neurogait

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brainsherpa.neurogait.databinding.ActivityGoogleSignInBinding
import kotlinx.coroutines.launch

class GoogleSignInActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGoogleSignInBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGoogleSignInBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnGoogleSignIn.setOnClickListener {
            lifecycleScope.launch {
                binding.btnGoogleSignIn.isEnabled = false
                binding.btnGoogleSignIn.text = "Signing in..."

                val credResult = GoogleSignInManager.signIn(this@GoogleSignInActivity)
                if (credResult.isFailure) {
                    binding.btnGoogleSignIn.isEnabled = true
                    binding.btnGoogleSignIn.text = "Sign in with Google"
                    Toast.makeText(this@GoogleSignInActivity, "Sign-in failed. Please try again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val (credential, rawNonce) = credResult.getOrThrow()
                val authResult = SupabaseIdentity.signInWithGoogle(
                    this@GoogleSignInActivity,
                    credential.idToken ?: "",
                    rawNonce
                )

                if (authResult.isFailure) {
                    binding.btnGoogleSignIn.isEnabled = true
                    binding.btnGoogleSignIn.text = "Sign in with Google"
                    Toast.makeText(this@GoogleSignInActivity, "Account link failed. Please try again.", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Success -> go to the scan screen (our launcher).
                startActivity(Intent(this@GoogleSignInActivity, ScanActivity::class.java))
                finish()
            }
        }
    }

    @Deprecated("Non-cancelable sign-in gate")
    override fun onBackPressed() {
        // do nothing (mandatory gate)
    }
}
