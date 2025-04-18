package io.github.wasu_code.nearipedia

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var mapUrlEditText: EditText
    private lateinit var saveButton: Button
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("prefs", Context.MODE_PRIVATE)
        mapUrlEditText = findViewById(R.id.editTextMapUrl)
        saveButton = findViewById(R.id.buttonSaveUrl)

        // Load the current map URL from shared preferences (if any)
        val currentMapUrl = sharedPreferences.getString("customMapUrl", "")
        mapUrlEditText.setText(currentMapUrl)

        saveButton.setOnClickListener {
            val customMapUrl = mapUrlEditText.text.toString()
            if (customMapUrl.isNotEmpty()) {
                // Save the custom map URL to shared preferences
                val editor = sharedPreferences.edit()
                editor.putString("customMapUrl", customMapUrl)
                editor.apply()
                Toast.makeText(this, "Map URL saved!", Toast.LENGTH_SHORT).show()
                finish() // Close the SettingsActivity
            }
        }
    }

}
