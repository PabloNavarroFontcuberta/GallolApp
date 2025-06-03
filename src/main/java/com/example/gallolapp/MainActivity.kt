package com.example.gallolapp

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.sqrt


class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private lateinit var mediaPlayer: MediaPlayer

    private var onShake: (() -> Unit)? = null
    private var shakeThreshold = 12f
    private var cooldownTime = 300L
    private var lastShakeTime = 0L

    //private var startTime = 0L
    //private var onFinish: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var sharedPreferences = getSharedPreferences("shake_prefs", Context.MODE_PRIVATE)
        mediaPlayer = MediaPlayer.create(this, R.raw.finish_sound)

        setContent {
            var shakeCount by remember { mutableStateOf(0) }
            var started by remember { mutableStateOf(false) }
            var countdown by remember { mutableStateOf(20) }
            var buttonEnabled by remember { mutableStateOf(true) }
            var showUI by remember { mutableStateOf(false) }
            var gallolaPerSecond: Float by remember { mutableStateOf(0f) }
            var bestScore by remember { mutableStateOf(sharedPreferences.getInt("best_score", 0)) }
            var bestScorePPS: Float by remember { mutableStateOf(0f) }
            var cooldownPhase by remember { mutableStateOf(false) }

            LaunchedEffect(Unit) {
                delay(500) // Optional delay before animation starts
                showUI = true
            }

            onShake = {
                shakeCount++
                gallolaPerSecond = shakeCount / 20f
            }
            val backgroundColor = if (cooldownPhase) Color(0xFEF1BF89) else MaterialTheme.colorScheme.background

           // Surface(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = backgroundColor
            ) {
                AnimatedVisibility(
                    visible = showUI,
                    enter = fadeIn(animationSpec = tween(durationMillis = 1500)),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "GallolApp",
                            style = MaterialTheme.typography.displayLarge
                        )
                        Spacer(modifier = Modifier.height(26.dp))
                        Image(
                            painter = painterResource(id = R.drawable.icon),
                            contentDescription = "Shake Icon",
                            modifier = Modifier.size(120.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        AnimatedVisibility(visible = started) {
                            Text(
                                text = "Sacudidas: $shakeCount",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        AnimatedVisibility(visible = started) {
                            Text(
                                text = "Te haces $gallolaPerSecond pajas por segundo",
                                style = MaterialTheme.typography.headlineSmall
                            )
                        }
                        if (!started) {
                            bestScorePPS = bestScore / 20f
                            Text("Mejor puntuación: $bestScorePPS pajas por segundo", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    shakeCount = 0
                                    started = true
                                    countdown = 20
                                    buttonEnabled = false

                                    lifecycleScope.launch {
                                        while (countdown > 0) {
                                            delay(1000L)
                                            countdown--
                                        }
                                        if (shakeCount > bestScore) {
                                            sharedPreferences.edit().putInt("best_score", shakeCount).apply()
                                            bestScore = shakeCount
                                        }
                                        mediaPlayer.start()
                                        if (shakeCount >= 30) {
                                            Toast.makeText(this@MainActivity, "¡Eres un máquina! $gallolaPerSecond pajas por segundo", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(this@MainActivity, "Hay que practicar ehh $gallolaPerSecond pajas por segundo", Toast.LENGTH_LONG).show()
                                        }

                                        started = false
                                        cooldownPhase = true
                                        // Espera 5 segundos antes de habilitar el botón nuevamente
                                        delay(5000L)
                                        cooldownPhase = false
                                        buttonEnabled = true
                                    }
                                },
                                enabled = buttonEnabled
                            ) {
                                Text("Comenzar")
                            }
                        } else {
                            Text("Ánimo crack", style = MaterialTheme.typography.headlineSmall)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Tiempo restante: $countdown segundos")
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        val acceleration = sqrt(x * x + y * y + z * z)
        val currentTime = System.currentTimeMillis()

        if (acceleration > shakeThreshold && currentTime - lastShakeTime > cooldownTime) {
            lastShakeTime = currentTime
            onShake?.invoke()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}