package com.example.landmarkremark

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.landmarkremark.databinding.ActivityLoginBinding
import com.example.landmarkremark.utils.FirebaseHelper
import com.example.landmarkremark.utils.SharedReferencesHelper
import com.example.landmarkremark.utils.showAlert
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                // There are no request codes
                val data: Intent? = result.data
                val username = data?.getStringExtra("username")
                val password = data?.getStringExtra("password")
                binding.inputUsername.setText(username)
                binding.inputPassword.setText(password)
            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        intent.extras?.getBoolean("isExpired")?.let {
            showAlert(this@LoginActivity, "Error", "Your session is expired")
        }
        val user = SharedReferencesHelper.getUser()
        user?.let {
            binding.inputUsername.setText(it.username)
            binding.inputPassword.setText(it.password)
        }
    }
    public fun handleRegisterClick(view: View?) {
        val intent = Intent(this, RegisterActivity::class.java)
        resultLauncher.launch(intent)
    }
    public fun handleLoginClick(view: View?) {
        val username = binding.inputUsername.text.toString()
        val password = binding.inputPassword.text.toString()

        GlobalScope.launch {
            val newUser = FirebaseHelper.login(username, password)
            if (newUser != null) {
                SharedReferencesHelper.putUser(newUser)
                startActivity(Intent(this@LoginActivity, UserLocation::class.java))
                finish()
            } else {
                runOnUiThread {
                    showAlert(this@LoginActivity,"Login failed", "Re-check your username and password")
                }
            }
        }
    }
}