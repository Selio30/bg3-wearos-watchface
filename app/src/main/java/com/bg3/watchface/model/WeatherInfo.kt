package com.bg3.watchface.model

/**
 * Atmospheric weather conditions with Faerûn / BG3 lore descriptions and icons.
 */
enum class WeatherCondition(
    val glyph: String,
    val standardName: String,
    val faerunLoreName: String
) {
    SUNNY("☀️", "Despejado", "Sol de Lathander"),
    PARTLY_CLOUDY("⛅", "Parcialmente Nublado", "Vientos de Selûne"),
    CLOUDY("☁️", "Nublado", "Brumas de la Costa"),
    RAINY("🌧️", "Lluvia", "Lágrimas de Ilmater"),
    THUNDERSTORM("⛈️", "Tormenta", "Furia de Talos"),
    SNOWY("❄️", "Nieve", "Aliento de Auril"),
    MISTY("🌫️", "Niebla", "Niebla de la Infraoscuridad");

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
