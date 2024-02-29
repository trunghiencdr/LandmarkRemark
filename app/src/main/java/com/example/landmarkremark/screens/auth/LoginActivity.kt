package com.example.landmarkremark.screens.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.landmarkremark.R
import com.example.landmarkremark.databinding.ActivityLoginBinding
import com.example.landmarkremark.screens.notes.UserLocation
import com.example.landmarkremark.utils.FirebaseHelper
import com.example.landmarkremark.utils.SharedReferencesHelper
import com.example.landmarkremark.utils.showAlert
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    companion object {
        const val IS_EXPIRED = "isExpired"
    }

    var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) { // get username and password when register success
                val data: Intent? = result.data
                val username = data?.getStringExtra(RegisterActivity.USERNAME)
                val password = data?.getStringExtra(RegisterActivity.PASSWORD)
                binding.inputUsername.setText(username)
                binding.inputPassword.setText(password)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        intent.extras?.getBoolean(IS_EXPIRED)?.let {// check user-session when launch app
            SharedReferencesHelper.removeUser()
            showAlert(
                this@LoginActivity,
                resources.getString(R.string.error),
                resources.getString(R.string.session_expired)
            )
        }
        // remember login info for re-open app
        SharedReferencesHelper.getUser()?.let {
            binding.inputUsername.setText(it.username)
            binding.inputPassword.setText(it.password)
        }
    }

    fun handleRegisterClick(view: View?) {
        // navigate to register and observe the data when register success
        val intent = Intent(this, RegisterActivity::class.java)
        resultLauncher.launch(intent)
    }

    fun handleLoginClick(view: View?) {
        val username = binding.inputUsername.text.toString()
        val password = binding.inputPassword.text.toString()

        lifecycleScope.launch {
            val newUser = FirebaseHelper.login(username, password)
            if (newUser != null) {
                SharedReferencesHelper.putUser(newUser)
                startActivity(Intent(this@LoginActivity, UserLocation::class.java))
                finish()
            } else {
                runOnUiThread {
                    showAlert(
                        this@LoginActivity,
                        resources.getString(R.string.error),
                        resources.getString(R.string.login_failed)
                    )
                }
            }
        }
    }
}