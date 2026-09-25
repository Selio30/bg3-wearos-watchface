package com.bg3.watchface.model

/**
 * Atmospheric weather conditions with Faerûn / BG3 lore descriptions and icons.
 */
enum class WeatherCondition(
    val glyph: String,
    val standardName: String,
    val faerunLoreName: String,
    val shortName: String = standardName
) {
    SUNNY("☀️", "Despejado", "Sol de Lathander", "Despejado"),
    PARTLY_CLOUDY("⛅", "Parcialmente Nublado", "Vientos de Selûne", "Nublado"),
    CLOUDY("☁️", "Nublado", "Brumas de la Costa", "Nublado"),
    RAINY("🌧️", "Lluvia", "Lágrimas de Ilmater", "Lluvia"),
    THUNDERSTORM("⛈️", "Tormenta", "Furia de Talos", "Tormenta"),
    SNOWY("❄️", "Nieve", "Aliento de Auril", "Nieve"),
    MISTY("🌫️", "Niebla", "Niebla de la Infraoscuridad", "Niebla");

    fun next(): WeatherCondition {
        val vals = values()
        return vals[(ordinal + 1) % vals.size]
    }
}

enum class TempUnit {
    CELSIUS,
    FAHRENHEIT
}

/**
 * Data class representing current weather conditions on the watch face.
 */
data class WeatherInfo(
    val tempCelsius: Int = 22,
    val condition: WeatherCondition = WeatherCondition.SUNNY,
    val unit: TempUnit = TempUnit.CELSIUS
) {
    val displayTemp: String
        get() = when (unit) {
            TempUnit.CELSIUS -> "$tempCelsius°C"
            TempUnit.FAHRENHEIT -> "${(tempCelsius * 9 / 5) + 32}°F"
        }
}
