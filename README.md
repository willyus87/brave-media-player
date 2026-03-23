# 🎬 BraveMedia Player — APK para Android

Reproductor de medios basado en WebView con bloqueo de anuncios integrado, reproducción con pantalla bloqueada y soporte Picture-in-Picture.

---

## ✅ Características implementadas

| Función | Cómo funciona |
|---|---|
| **Bloqueo de anuncios** | Lista de dominios bloqueados + inyección JS para saltar anuncios de YouTube |
| **Pantalla bloqueada** | `Foreground Service` + `WakeLock (PARTIAL)` mantiene el audio activo |
| **App minimizada** | `Picture-in-Picture (PiP)` automático al presionar Home |
| **Controles en notificación** | Botones Play/Pause en la barra de notificaciones |
| **Pantalla de bloqueo** | `MediaSession` muestra controles en el lockscreen |
| **Interceptar YouTube** | La app se registra como handler de links youtube.com/youtu.be |
| **Auriculares** | Los botones físicos del headset controlan la reproducción |
| **Fullscreen** | Soporte completo para videos en pantalla completa |

---

## 📁 Estructura del proyecto

```
MediaPlayerApp/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml          # Permisos y componentes
│   │   ├── java/com/mediaplayer/brave/
│   │   │   ├── MainActivity.java        # WebView + AdBlocker + PiP
│   │   │   └── MediaPlaybackService.java # Servicio foreground
│   │   └── res/
│   │       ├── layout/activity_main.xml # UI con barra URL y navegación
│   │       ├── values/themes.xml        # Tema oscuro
│   │       ├── drawable/               # Recursos visuales
│   │       └── xml/file_paths.xml      # FileProvider config
│   ├── build.gradle
│   └── proguard-rules.pro
├── build.gradle
└── settings.gradle
```

---

## 🚀 Cómo compilar e instalar

### Requisitos previos
- **Android Studio** Hedgehog (2023.1.1) o superior
- **JDK 11** o superior
- **Android SDK 34**
- Dispositivo o emulador con **Android 8.0+ (API 26+)**

### Pasos

```bash
# 1. Abrir en Android Studio
File → Open → Seleccionar carpeta MediaPlayerApp/

# 2. Sincronizar Gradle
Tools → Sync Project with Gradle Files

# 3. Compilar APK de debug
Build → Build Bundle(s) / APK(s) → Build APK(s)
# El APK estará en: app/build/outputs/apk/debug/app-debug.apk

# 4. Instalar en dispositivo (con USB debugging)
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## ⚙️ Configuración recomendada en el dispositivo

### Para reproducción con pantalla bloqueada:

1. **Ajustes → Aplicaciones → BraveMedia Player**
   - ✅ Permitir ejecutar en segundo plano
   - ✅ No optimizar batería (o "Sin restricciones")
   
2. **Ajustes → Pantalla**
   - Configurar timeout de pantalla según preferencia (el audio seguirá aunque la pantalla se apague)

3. **Para PiP automático:**
   - ✅ Ajustes → Aplicaciones → BraveMedia Player → Picture-in-Picture → Permitir

### En MIUI (Xiaomi) / One UI (Samsung) / OxygenOS (OnePlus):

Estas ROMs tienen gestores agresivos de batería. Ir a:
- **Ajustes → Batería → Ahorro de energía avanzado** → Excluir BraveMedia Player
- O en MIUI: **Ajustes → Aplicaciones → BraveMedia Player → Ahorro de energía → Sin restricciones**

---

## 🔧 Personalización

### Agregar más dominios al adblocker
En `MainActivity.java`, agregar a `BLOCKED_DOMAINS`:
```java
private static final Set<String> BLOCKED_DOMAINS = new HashSet<>(Arrays.asList(
    "tu-dominio-a-bloquear.com",
    // ... más dominios
));
```

### Cambiar la URL de inicio
En `MainActivity.java`, línea del loadUrl inicial:
```java
webView.loadUrl("https://m.youtube.com"); // Cambiar por cualquier URL
```

### Habilitar modo escritorio
Para ver la versión desktop de YouTube:
```java
settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36...");
webView.loadUrl("https://www.youtube.com"); // Sin la "m." del principio
```

---

## 🔬 Arquitectura técnica

```
┌─────────────────────────────────────────────────────┐
│                    CAPA UI (MainActivity)            │
│  ┌──────────┐  ┌─────────────┐  ┌────────────────┐ │
│  │ Toolbar  │  │   WebView   │  │  Nav Buttons   │ │
│  │ URL Bar  │  │  + AdBlock  │  │  PiP / Back    │ │
│  └──────────┘  └──────┬──────┘  └────────────────┘ │
└──────────────────────┼──────────────────────────────┘
                       │ JavaScript Bridge
                       │ (AndroidBridge)
┌──────────────────────▼──────────────────────────────┐
│              CAPA SERVICIO (MediaPlaybackService)    │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │ Foreground   │  │ MediaSession │  │  WakeLock │ │
│  │ Service      │  │ (Lockscreen) │  │ (CPU on)  │ │
│  └──────────────┘  └──────────────┘  └───────────┘ │
│  ┌──────────────────────────────────────────────┐   │
│  │          Notification con controles          │   │
│  │     [▶ Play/Pause]  [⏹ Stop]               │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

---

## ⚠️ Limitaciones conocidas

1. **YouTube Premium**: El bloqueo de anuncios funciona bien en cuentas gratuitas. YouTube actualiza frecuentemente sus selectores CSS de anuncios, puede requerir actualizaciones del JS inyectado.

2. **Background play**: YouTube puede detectar la inyección de `visibilityState` en algunos casos. Si ocurre, considera usar `youtube-nocookie.com` o `invidious.io` como alternativa.

3. **PiP en MIUI**: Algunas versiones de MIUI tienen el PiP roto. Usar la opción de "ventana flotante" de MIUI en su lugar.

4. **Android 14+**: Puede requerir permisos adicionales para `FOREGROUND_SERVICE_MEDIA_PLAYBACK`. Ya está incluido en el manifest.

---

## 🌐 Alternativas de frontend para el WebView

En vez de YouTube, puedes cargar:
- `https://invidious.io` — Frontend alternativo de YouTube sin anuncios
- `https://piped.video` — Otro frontend de YouTube más liviano
- `https://music.youtube.com` — YouTube Music
- `https://open.spotify.com` — Spotify Web
- `https://soundcloud.com` — SoundCloud

---

## 📝 Licencia

MIT License — libre para uso personal y modificación.
