# 🎲 Baldur's Gate 3 — Esfera de Reloj Oficial Wear OS 5 (Watch Face Format)
### Optimizada para Wear OS 5+ (OnePlus Watch 2 & 3, Galaxy Watch 7/Ultra, Pixel Watch 3)

Esfera de reloj declarativa de alta fidelidad inspirada en el universo y la interfaz de **Baldur's Gate 3 (BG3)**. Diseñada específicamente bajo el estándar oficial **Watch Face Format (WFF)** requerido obligatoriamente para **Wear OS 5**.

> [!IMPORTANT]
> **Sin código ejecutable (`android:hasCode="false"`)**: A diferencia de versiones anteriores basadas en Activity, Compose o Kotlin/Java (`WallpaperService` / `CanvasRenderer`), esta esfera utiliza exclusivamente la especificación declarativa **Watch Face Format (WFF)** de Google y Samsung. El sistema operativo Wear OS 5 se encarga del renderizado directo en el co-procesador (MCU) de bajo consumo, garantizando un rendimiento óptimo de batería (< 3% AOD) y compatibilidad total con Google Play.

---

## 📸 Galería y Elementos Visuales

| Tav (Oro Arcano) | Astarion (Carmesí) | Shadowheart (Engaño) | Karlach (Infernal) |
| :---: | :---: | :---: | :---: |
| Oro bruñido & runas | Plata lunar & sangre | Ocaso crepuscular & cian | Bronce & magma ardiente |

---

## ⚔️ Características de la Esfera (WFF)

### 1. Reloj Digital & Anillo Rúnico Arcano
- **Tipografía Heroica**: Reloj digital en formato 24h/12h con sincronización horaria de alta precisión.
- **Anillo de Runas Faerûn**: Anillo exterior concéntrico dorado con 12 runas que rotan suavemente con el transcurso de los segundos.
- **Optimización de AOD (Ambient Mode)**: Ocultación automática de segundos y rotaciones en modo Always-On para ahorrar energía y evitar desgaste OLED.

### 2. D20 Emblemático Central
- **Emblema Sagrado D20**: Círculo concéntrico ornamental con numeración '20' grabada en oro arcano (`#FFF5D77F`) y filigrana de fondo.
- **Sutil Animación de Respiración**: Variación senoidal suave en reposo durante el modo interactivo.

### 3. Estadísticas de Aventurero Integradas
- **♥ HP (Salud)**: Monitor de batería en tiempo real vinculado a `[BATTERY_PERCENT]`.
- **✦ XP (Experiencia)**: Contador de pasos diario en tiempo real vinculado a `[STEP_COUNT]`.
- **♦ Ritmo Cardíaco (BPM)**: Sensor de pulso cardíaco en tiempo real vinculado a `round([HEART_RATE])`.
- **Calendario Faerûn**: Visualización dinámica de día de la semana y fecha.

---

## 📁 Estructura del Proyecto (WFF Puro)

```
bg3/
├── build.gradle.kts                            # Configuración de compilación raíz
├── settings.gradle.kts                         # Módulos del proyecto
├── gradle/
│   ├── libs.versions.toml                      # Catálogo de versiones
│   └── wrapper/                                # Gradle Wrapper 8.7
├── app/
│   ├── build.gradle.kts                        # AGP 8.4.2 (sin plugins de Kotlin ni dependencias pesadas)
│   └── src/main/
│       ├── AndroidManifest.xml                 # Declaración WFF: hasCode=false, WFF versión 1/2
│       └── res/
│           ├── raw/
│           │   └── watchface.xml               # Especificación completa WFF (Escena, Reloj, Runas, D20, Stats)
│           ├── xml/
│           │   ├── watch_face_info.xml         # Metadatos del selector de esferas y previsualización
│           │   └── watch_face_shapes.xml       # Mapeo de pantallas circulares (466x466)
│           ├── values/
│           │   ├── strings.xml                 # Nombres y textos de localización
│           │   └── colors.xml                  # Paletas de color BG3
│           └── drawable/                       # Iconos y previews oficiales
└── preview/                                    # Previsualización e imágenes de muestra
```

---

## ⚡ Instalación en Wear OS 5 (OnePlus Watch, Galaxy Watch, Pixel Watch)

```powershell
# 1. Conectar por ADB Wi-Fi
adb connect <IP_DEL_RELOJ>:<PUERTO>

# 2. Compilar el paquete WFF
.\gradlew.bat assembleDebug

# 3. Instalar en el dispositivo
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
