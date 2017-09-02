package com.likebobby.summerofweather


interface WeatherStationContract {

    interface Device {
        fun setImage(img: Int)


    }

    interface Actions {
        fun onKeyDown(keyCode: Int)
        fun onKeyUp(keyCode: Int)
        fun onDestroy()

    }
}