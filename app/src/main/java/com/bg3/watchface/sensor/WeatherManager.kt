package com.bg3.watchface.sensor

import com.bg3.watchface.model.TempUnit
import com.bg3.watchface.model.WeatherCondition
import com.bg3.watchface.model.WeatherInfo

/**
 * Manager handling real-time or complication-driven weather conditions.
 */
class WeatherManager(
    initialTempCelsius: Int = 21,
    initialCondition: WeatherCondition = WeatherCondition.SUNNY
) {

    var weatherInfo: WeatherInfo = WeatherInfo(
        tempCelsius = initialTempCelsius,
        condition = initialCondition,
        unit = TempUnit.CELSIUS
    )
        private set

    fun toggleUnit(): TempUnit {
        val newUnit = if (weatherInfo.unit == TempUnit.CELSIUS) TempUnit.FAHRENHEIT else TempUnit.CELSIUS
        weatherInfo = weatherInfo.copy(unit = newUnit)
        return newUnit
    }

    fun cycleCondition(): WeatherCondition {
        val nextCond = weatherInfo.condition.next()
        weatherInfo = weatherInfo.copy(condition = nextCond)
        return nextCond
    }

    fun updateWeather(tempCelsius: Int, condition: WeatherCondition) {
        weatherInfo = weatherInfo.copy(tempCelsius = tempCelsius, condition = condition)
    }
}
