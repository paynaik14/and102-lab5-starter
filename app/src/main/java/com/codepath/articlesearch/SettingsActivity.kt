package com.codepath.articlesearch

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.CompoundButton
import androidx.appcompat.app.AppCompatActivity
import com.codepath.articlesearch.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences("user_preferences", Context.MODE_PRIVATE)

        // Load the current value for the caching preference
        val shouldCacheData = sharedPreferences.getBoolean("cache_data", true)
        binding.switchCacheData.isChecked = shouldCacheData

        // Set a listener to save the value when the switch is toggled
        binding.switchCacheData.setOnCheckedChangeListener { _: CompoundButton, isChecked: Boolean ->
            sharedPreferences.edit().putBoolean("cache_data", isChecked).apply()
        }
    }
}
