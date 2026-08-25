package com.example.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AmberGolden
import kotlin.math.abs

@Composable
fun HorizonLevel(
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val context = LocalContext.current
    var roll by remember { mutableStateOf(0f) }
    var isLevel by remember { mutableStateOf(false) }

    DisposableEffect(enabled) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        val gravity = FloatArray(3)
        val geomag = FloatArray(3)
        var hasGravity = false
        var hasMag = false

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                    System.arraycopy(event.values, 0, gravity, 0, 3)
                    hasGravity = true
                } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
                    System.arraycopy(event.values, 0, geomag, 0, 3)
                    hasMag = true
                }
                if (hasGravity && hasMag) {
                    val R = FloatArray(9)
                    val I = FloatArray(9)
                    if (SensorManager.getRotationMatrix(R, I, gravity, geomag)) {
                        val orientation = FloatArray(3)
                        SensorManager.getOrientation(R, orientation)
                        val rollDeg = Math.toDegrees(orientation[2].toDouble()).toFloat() // -180..180
                        roll = rollDeg
                        isLevel = abs(rollDeg) < 2.5f
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, if (isLevel) AmberGolden else Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(32.dp)) {
            val centerX = size.width / 2f
            val centerY = size.height / 2f
            // Horizon line tilted by roll
            val angleRad = Math.toRadians(roll.toDouble())
            val len = size.width * 0.35f
            val dx = (Math.cos(angleRad) * len).toFloat()
            val dy = (Math.sin(angleRad) * len).toFloat()
            drawLine(
                color = if (isLevel) AmberGolden else Color.White,
                start = Offset(centerX - dx, centerY - dy),
                end = Offset(centerX + dx, centerY + dy),
                strokeWidth = 2.dp.toPx()
            )
            // Center dot
            drawCircle(color = if (isLevel) AmberGolden else Color.White.copy(alpha = 0.9f), radius = 4.dp.toPx(), center = Offset(centerX, centerY))
        }
        Text(
            text = if (isLevel) "مستوي ✓" else String.format("%.1f°", roll),
            color = if (isLevel) AmberGolden else Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp)
        )
    }
}
