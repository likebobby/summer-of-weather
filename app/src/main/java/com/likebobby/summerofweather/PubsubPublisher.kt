package com.likebobby.summerofweather

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.net.ConnectivityManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import android.util.Log
import com.google.api.client.extensions.android.http.AndroidHttp
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.services.pubsub.Pubsub
import com.google.api.services.pubsub.PubsubScopes
import com.google.api.services.pubsub.model.PublishRequest
import com.google.api.services.pubsub.model.PubsubMessage
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class PubsubPublisher @Throws(IOException::class)
constructor(private val mContext: Context, private val mAppname: String, project: String, topic: String,
            credentialResourceId: Int) {
    private val mTopic: String

    private var mPubsub: Pubsub? = null
    private var mHttpTransport: HttpTransport? = null

    private val mHandler: Handler
    private val mHandlerThread: HandlerThread

    private var mLastTemperature = java.lang.Float.NaN
    private var mLastPressure = java.lang.Float.NaN

    init {
        mTopic = "projects/$project/topics/$topic"

        mHandlerThread = HandlerThread("pubsubPublisherThread")
        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper)

        val jsonCredentials = mContext.resources.openRawResource(credentialResourceId)
        val credentials: GoogleCredential
        try {
            credentials = GoogleCredential.fromStream(jsonCredentials).createScoped(
                    setOf(PubsubScopes.PUBSUB))
        } finally {
            try {
                jsonCredentials.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error closing input stream", e)
            }

        }
        mHandler.post {
            mHttpTransport = AndroidHttp.newCompatibleTransport()
            val jsonFactory = JacksonFactory.getDefaultInstance()
            mPubsub = Pubsub.Builder(mHttpTransport!!, jsonFactory, credentials)
                    .setApplicationName(mAppname).build()
        }
    }

    fun start() {
        mHandler.post(mPublishRunnable)
    }

    fun stop() {
        mHandler.removeCallbacks(mPublishRunnable)
    }

    fun close() {
        mHandler.removeCallbacks(mPublishRunnable)
        mHandler.post {
            try {
                mHttpTransport!!.shutdown()
            } catch (e: IOException) {
                Log.d(TAG, "error destroying http transport")
            } finally {
                mHttpTransport = null
                mPubsub = null
            }
        }
        mHandlerThread.quitSafely()
    }

    private val mPublishRunnable = object : Runnable {
        override fun run() {
            val connectivityManager = mContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = connectivityManager.activeNetworkInfo
            if (activeNetwork == null || !activeNetwork.isConnectedOrConnecting) {
                Log.e(TAG, "no active network")
                return
            }

            try {
                val messagePayload = createMessagePayload(mLastTemperature, mLastPressure)
                if (!messagePayload.has("data")) {
                    Log.d(TAG, "no sensor measurement to publish")
                    return
                }
                Log.d(TAG, "publishing message: " + messagePayload)
                val m = PubsubMessage()
                m.data = Base64.encodeToString(messagePayload.toString().toByteArray(),
                        Base64.NO_WRAP)
                val request = PublishRequest()
                request.messages = listOf(m)
                mPubsub!!.projects().topics().publish(mTopic, request).execute()
            } catch (e: JSONException) {
                Log.e(TAG, "Error publishing message", e)
            } catch (e: IOException) {
                Log.e(TAG, "Error publishing message", e)
            } finally {
                mHandler.postDelayed(this, PUBLISH_INTERVAL_MS)
            }
        }

        @Throws(JSONException::class)
        private fun createMessagePayload(temperature: Float, pressure: Float): JSONObject {
            val sensorData = JSONObject()
            if (!java.lang.Float.isNaN(temperature)) {
                sensorData.put("temperature", temperature.toString())
            }
            if (!java.lang.Float.isNaN(pressure)) {
                sensorData.put("pressure", pressure.toString())
            }
            val messagePayload = JSONObject()
            messagePayload.put("deviceId", Build.DEVICE)
            messagePayload.put("channel", "pubsub")
            messagePayload.put("timestamp", System.currentTimeMillis())
            if (sensorData.has("temperature") || sensorData.has("pressure")) {
                messagePayload.put("data", sensorData)
            }
            return messagePayload
        }
    }

    val temperatureListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            mLastTemperature = event.values[0]
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    val pressureListener: SensorEventListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            mLastPressure = event.values[0]
        }

        override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
    }

    companion object {
        private val TAG = PubsubPublisher::class.java.simpleName

        private val PUBLISH_INTERVAL_MS = TimeUnit.MINUTES.toMillis(1)
    }
}