package com.likebobby.summerofweather

import android.app.Activity
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import com.google.android.things.contrib.driver.apa102.Apa102
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay
import java.io.IOException
import java.util.*

class MainActivity : Activity() {

    private val TAG = "Weather"

    private lateinit var display: AlphanumericDisplay
    private lateinit var ledstrip: Apa102
    private lateinit var environmentalSensorDriver: Bmx280SensorDriver
    private lateinit var sensorManager: SensorManager

    private val LEDSTRIP_BRIGHTNESS = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sensorManager = getSystemService(SensorManager::class.java)

        // Initialize 7-segment display
        try {
            display = AlphanumericDisplay(BoardDefaults.i2cBus)
            display.setEnabled(true)
            Log.d(TAG, "Initialized I2C Display")
        } catch (e: IOException) {
            throw RuntimeException("Error initializing display", e)
        }

        try {
            ledstrip = Apa102(BoardDefaults.spiBus, Apa102.Mode.BGR)
            ledstrip.setBrightness(LEDSTRIP_BRIGHTNESS)
            val colors = IntArray(7)
            Arrays.fill(colors, Color.RED)
            ledstrip.write(colors)
            // Because of a known APA102 issue, write the initial value twice.
            ledstrip.write(colors)

            Log.d(TAG, "Initialized SPI LED strip")
        } catch (e: IOException) {
            throw RuntimeException("Error initializing LED strip", e)
        }



        try {
            environmentalSensorDriver = Bmx280SensorDriver(BoardDefaults.i2cBus)
            environmentalSensorDriver.registerTemperatureSensor()
            environmentalSensorDriver.registerPressureSensor()
        } catch (e: IOException) {
            throw RuntimeException("Error initializing BMP280", e)
        }
    }

    override fun onStart() {
        super.onStart()

        val temperature = sensorManager.getDynamicSensorList(Sensor.TYPE_AMBIENT_TEMPERATURE)[0]
        sensorManager.registerListener(sensorEventListener, temperature, SensorManager.SENSOR_DELAY_NORMAL)
        val pressure = sensorManager.getDynamicSensorList(Sensor.TYPE_PRESSURE)[0]
        sensorManager.registerListener(sensorEventListener, pressure, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onStop() {
        super.onStop()

        sensorManager.unregisterListener(sensorEventListener)
    }

    override fun onDestroy() {
        super.onDestroy()

        environmentalSensorDriver.close()

        try {
            display.clear();
            display.setEnabled(false);
            display.close();
        } catch (e: IOException) {
            Log.e(TAG, "Error closing display", e);
        }

        try {
            ledstrip.write(kotlin.IntArray(7));
            ledstrip.brightness = 0;
            ledstrip.close();
        } catch (e: IOException) {
            Log.e(TAG, "Error closing LED strip", e);
        }

    }

    fun updateTemperatureDisplay(temperature: Float) {
        try {
            display.display(temperature.toDouble())
        } catch(e: IOException) {
            Log.e(TAG, "Error updating display", e);
        }
    }

    fun updateBarometerDisplay(pressure: Float) {
        try {
            var colors = RainbowUtil.getWeatherStripColors(pressure)
            ledstrip.write(colors)
        } catch(e: IOException) {
            Log.e(TAG, "Error updating ledstrip", e);
        }
    }

    val sensorEventListener = object : SensorEventListener {
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        }

        override fun onSensorChanged(event: SensorEvent?) {
            val value = event!!.values[0]

            if (event.sensor.type == Sensor.TYPE_AMBIENT_TEMPERATURE) {
                updateTemperatureDisplay(value)
            }
            if (event.sensor.type == Sensor.TYPE_PRESSURE) {
                updateBarometerDisplay(value)
            }
        }

    }
}
