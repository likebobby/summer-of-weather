package com.likebobby.summerofweather


interface WeatherStationContract {

    interface Device {


    }

    interface Actions {
        fun onKeyDown(keyCode: Int)
        fun onKeyUp(keyCode: Int)

    }
}