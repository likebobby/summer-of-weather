package com.likebobby.summerofweather

import android.app.Activity
import android.hardware.SensorManager
import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import com.google.android.things.contrib.driver.apa102.Apa102
import com.google.android.things.contrib.driver.bmx280.Bmx280SensorDriver
import com.google.android.things.contrib.driver.button.Button
import com.google.android.things.contrib.driver.button.ButtonInputDriver
import com.google.android.things.contrib.driver.ht16k33.AlphanumericDisplay
import com.google.android.things.contrib.driver.pwmspeaker.Speaker
import com.google.android.things.pio.PeripheralManagerService


class WeatherStationActivity : Activity(), WeatherStationContract.Device {

    private lateinit var sensorManager: SensorManager


    private lateinit var imageView: ImageView

    private lateinit var presenter: WeatherStationContract.Actions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weather_station)

        imageView = findViewById(R.id.imageView)
        sensorManager = getSystemService(SensorManager::class.java)

        val credentialId = getResources().getIdentifier("credentials", "raw", getPackageName())
        val pubsubPublisher = PubsubPublisher(this, "weatherstation",
                BuildConfig.PROJECT_ID, BuildConfig.PUBSUB_TOPIC, credentialId)

        presenter = WeatherStationPresenter(this,
                sensorManager,
                ButtonInputDriver(BoardDefaults.buttonGpioPin, Button.LogicState.PRESSED_WHEN_LOW, KeyEvent.KEYCODE_A),
                Bmx280SensorDriver(BoardDefaults.i2cBus),
                AlphanumericDisplay(BoardDefaults.i2cBus),
                Apa102(BoardDefaults.spiBus, Apa102.Mode.BGR),
                PeripheralManagerService(),
                Speaker(BoardDefaults.speakerPwmPin),
                pubsubPublisher
        )

    }

    override fun onDestroy() {
        super.onDestroy()
        presenter.onDestroy()
    }

    private var barometerImageResource: Int = R.drawable.ic_cloudy

    override fun setImage(img: Int) {
        if (img != barometerImageResource) {
            imageView.setImageResource(img)
            barometerImageResource = img
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        presenter.onKeyDown(keyCode)

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        presenter.onKeyUp(keyCode)
        return super.onKeyUp(keyCode, event)
    }
}
