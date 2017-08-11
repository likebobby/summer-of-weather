/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.likebobby.summerofweather

import android.graphics.Color

/**
 * Helper methods for computing outputs on the Rainbow HAT
 */
object RainbowUtil {
    /* Barometer Range Constants */
    private val BAROMETER_RANGE_LOW = 965f
    private val BAROMETER_RANGE_HIGH = 1035f

    /* LED Strip Color Constants*/
    private var sRainbowColors: IntArray = IntArray(7)

    init {
        for (i in sRainbowColors!!.indices) {
            val hsv = floatArrayOf(i * 360f / sRainbowColors!!.size, 1.0f, 1.0f)
            sRainbowColors[i] = Color.HSVToColor(255, hsv)
        }
    }

    /**
     * Return an array of colors for the LED strip based on the given pressure.
     * @param pressure Pressure reading to compare.
     * *
     * @return Array of colors to set on the LED strip.
     */
    fun getWeatherStripColors(pressure: Float): IntArray {
        val t = (pressure - BAROMETER_RANGE_LOW) / (BAROMETER_RANGE_HIGH - BAROMETER_RANGE_LOW)
        var n = Math.ceil((sRainbowColors!!.size * t).toDouble()).toInt()
        n = Math.max(0, Math.min(n, sRainbowColors!!.size))

        val colors = IntArray(sRainbowColors!!.size)
        for (i in 0..n - 1) {
            val ri = sRainbowColors!!.size - 1 - i
            colors[ri] = sRainbowColors!![ri]
        }

        return colors
    }
}
