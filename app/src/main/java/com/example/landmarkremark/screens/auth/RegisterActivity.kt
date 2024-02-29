package com.example.landmarkremark.screens.auth

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.landmarkremark.R
import com.example.landmarkremark.databinding.ActivityRegisterBinding
import com.example.landmarkremark.utils.FirebaseHelper
import com.example.landmarkremark.utils.showAlert
import kotlinx.coroutines.launch

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding

    companion object {
        const val USERNAME = "username"
        const val PASSWORD = "password"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            btnCancel.setOnClickListener {
                finish()
            }
            btnRegister.setOnClickListener {
                register()
            }
        }
    }

    private fun register() {
        val name = binding.inputName.text.toString()
        if (name.isEmpty()) {
            showAlert(
                this, resources.getString(R.string.error),
                resources.getString(R.string.name_required)
            )
        } else {
            val username = binding.inputUsername.text.toString()
            val password = binding.inputPassword.text.toString()
            lifecycleScope.launch {
                FirebaseHelper.register(name, username, password, onSuccess = {
                    intent.putExtra(USERNAME, username);
                    intent.putExtra(PASSWORD, password);
                    setResult(RESULT_OK, intent);
                    finish()
                }, onFailed = {
                    runOnUiThread {
                        showAlert(
                            this@RegisterActivity,
                            resources.getString(R.string.error),
                            resources.getString(R.string.username_existed)
                        )
                    }
                })
            }
        }

    }
}