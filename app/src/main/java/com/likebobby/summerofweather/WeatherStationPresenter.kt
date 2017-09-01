package com.likebobby.summerofweather

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.ContentValues.TAG
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import android.view.KeyEvent
import android.view.animation.LinearInterpolator
import com.google.android.things.contrib.driver.apa102.Apa102
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay
import com.google.android.things.contrib.driver.pwmspeaker.Speaker
import com.google.android.things.pio.Gpio
import com.google.android.things.pio.PeripheralManagerService
import java.io.IOException


class WeatherStationPresenter(var device: WeatherStationContract.Device,
                              var sensorManager: SensorManager,
                              buttonInputDriver: ButtonInputDriver,
                              environmentalSensorDriver: Bmx280SensorDriver,
                              var alphanumericDisplay: AlphanumericDisplay,
                              var ledStrip: Apa102,
                              peripheralManagerService: PeripheralManagerService,
                              speaker: Speaker) : WeatherStationContract.Actions {

    enum class DisplayMode {
        PRESSURE,
        TEMPERATURE
    }

    private val BAROMETER_RANGE_LOW = 965f
    private val BAROMETER_RANGE_HIGH = 1035f
    private val BAROMETER_RANGE_SUNNY = 1010f
    private val BAROMETER_RANGE_RAINY = 990f

    private var displayMode = DisplayMode.TEMPERATURE

    private var lastPressure: Float = 0.0f
    private var lastTemperature: Float = 0.0f

    private val rainbow: IntArray = kotlin.IntArray(7)

    private var led: Gpio

    init {
        buttonInputDriver.register()
        sensorManager.registerDynamicSensorCallback(getDynamicSensorCallback())
        environmentalSensorDriver.registerPressureSensor()
        environmentalSensorDriver.registerTemperatureSensor()
        alphanumericDisplay.setEnabled(true)
        alphanumericDisplay.clear()

        ledStrip.brightness = 1
        for (i in 0 until rainbow.size) {
            val hsv = floatArrayOf(i * 360f / rainbow.size, 1.0f, 1.0f)
            rainbow[i] = Color.HSVToColor(255, hsv)
        }

        led = peripheralManagerService.openGpio(BoardDefaults.ledGpioPin)
        led.setEdgeTriggerType(Gpio.EDGE_NONE)
        led.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW)
        led.setActiveType(Gpio.ACTIVE_HIGH)

        playSound(speaker)
    }

    private fun playSound(speaker: Speaker) {
        val slide = ValueAnimator.ofFloat(440f, 440 * 4f)
        slide.startDelay = 300
        slide.duration = 50
        slide.repeatCount = 5
        slide.interpolator = LinearInterpolator()
        slide.addUpdateListener { animation ->
            val v = animation.animatedValue as Double
            speaker.play(v)
        }
        slide.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                speaker.stop()
            }
        })
        slide.start()
    }

    private fun getDynamicSensorCallback(): SensorManager.DynamicSensorCallback {
        return object : SensorManager.DynamicSensorCallback() {
            override fun onDynamicSensorConnected(sensor: Sensor) {
                if (sensor.getType() === Sensor.TYPE_AMBIENT_TEMPERATURE) {
                    // Our sensor is connected. Start receiving temperature data.
                    sensorManager.registerListener(getTemperatureListener(), sensor,
                            SensorManager.SENSOR_DELAY_NORMAL)
    //                if (mPubsubPublisher != null) {
    //                    mSensorManager.registerListener(mPubsubPublisher.getTemperatureListener(), sensor,
    //                            SensorManager.SENSOR_DELAY_NORMAL)
    //                }
                } else if (sensor.getType() === Sensor.TYPE_PRESSURE) {
                    // Our sensor is connected. Start receiving pressure data.
                    sensorManager.registerListener(getPressureListener(), sensor,
                            SensorManager.SENSOR_DELAY_NORMAL)
    //                if (mPubsubPublisher != null) {
    //                    mSensorManager.registerListener(mPubsubPublisher.getPressureListener(), sensor,
    //                            SensorManager.SENSOR_DELAY_NORMAL)
    //                }
                }
            }

            override fun onDynamicSensorDisconnected(sensor: Sensor) {
                super.onDynamicSensorDisconnected(sensor)
            }
        }
    }

    private fun getTemperatureListener() : SensorEventListener
            = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lastTemperature = event.values[0]
            Log.d(TAG, "sensor changed: " + lastTemperature)
            if (displayMode === DisplayMode.TEMPERATURE) {
                updateDisplay(lastTemperature)
            }
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Log.d(TAG, "accuracy changed: " + accuracy)
        }
    }

    private fun getPressureListener() : SensorEventListener
            = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            lastPressure = event.values[0]
            Log.d(TAG, "sensor changed: " + lastPressure)
            if (displayMode === DisplayMode.PRESSURE) {
                updateDisplay(lastPressure)
            }
            updateBarometer(lastPressure)
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
            Log.d(TAG, "accuracy changed: " + accuracy)
        }
    }

    private fun updateBarometer(lastPressure: Float) {
        val t = (lastPressure - BAROMETER_RANGE_LOW) / (BAROMETER_RANGE_HIGH - BAROMETER_RANGE_LOW)
        var n = Math.ceil(rainbow.size * t.toDouble()).toInt()
        n = Math.max(0, Math.min(n, rainbow.size))
        val colors = IntArray(rainbow.size)
        for (i in 0..n - 1) {
            val ri = rainbow.size - 1 - i
            colors[ri] = rainbow[ri]
        }
        try {
            ledStrip.write(colors)
        } catch (e: IOException) {
            Log.e(TAG, "Error setting ledstrip", e)
        }

    }


    override fun onKeyDown(keyCode: Int) {
        if (keyCode == KeyEvent.KEYCODE_A) {
            displayMode = DisplayMode.PRESSURE
            updateDisplay(lastPressure)
            try {
                led.value = true
            } catch (e: IOException) {
                Log.e(TAG, "error updating LED", e)
            }
        }
    }

    private fun updateDisplay(value: Float) {
        alphanumericDisplay.display(value.toDouble())
    }

    override fun onKeyUp(keyCode: Int) {
        if (keyCode == KeyEvent.KEYCODE_A) {
            displayMode = WeatherStationPresenter.DisplayMode.TEMPERATURE
            updateDisplay(lastTemperature)
            try {
                led.value = false
            } catch (e: IOException) {
                Log.e(TAG, "error updating LED", e)
            }
        }
    }

}



