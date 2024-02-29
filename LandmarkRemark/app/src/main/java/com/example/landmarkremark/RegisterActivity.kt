package com.example.landmarkremark

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.landmarkremark.databinding.ActivityRegisterBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private val TAG = this::class.java.simpleName
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.btnCancel.setOnClickListener {
            finish()
        }
        binding.btnRegister.setOnClickListener {
            register()
        }
    }

    private fun register(){
        val db = Firebase.firestore
        val username = binding.inputUsername.text.toString()
        val name = binding.inputName.text.toString()
        val password = binding.inputPassword.text.toString()
        val user = hashMapOf(
            "name" to name,
            "username" to username,
            "password" to password
        )
        db.collection("users")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { result  ->
                if(result.isEmpty){
                    db.collection("users")
                        .add(user)
                        .addOnSuccessListener { documentReference ->
                            Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
                            intent.putExtra("username",username);
                            intent.putExtra("password",password);
                            setResult(RESULT_OK,intent);
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Log.w(TAG, "Error adding document", e)
                        }
                }else{
                    Toast.makeText(this@RegisterActivity, "username existed", Toast.LENGTH_LONG)
                }
        }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error adding document", e)
            }


    }
}