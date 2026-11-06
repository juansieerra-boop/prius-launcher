# Prius Launcher

Pantalla de inicio deportiva para la pantalla Android del Prius 2011 (1024×600, Android 12).

- Reloj grande, fecha y tu Prius con entrada animada
- Velocidad por GPS (MPH o KM/H) con barras tipo tacómetro
- Mapa, 6 botones de apps configurables, "Todas las apps" y control de música
- **Personalizar** (ícono de ajustes arriba a la derecha): color de acento, MPH/KM/H y foto del carro

## Descargar el APK

En GitHub, pestaña **Actions** → la compilación más reciente con ✅ → abajo en **Artifacts** → **PriusLauncher-apk**. Adentro viene `app-debug.apk`.

## Instalar por ADB (Wi-Fi)

1. Conecta la computadora a la **misma red** que la pantalla (si la IP es 172.20.10.x, es el hotspot de tu iPhone).
2. En la pantalla, activa la depuración: *Ajustes → Acerca de → toca 7 veces "Número de compilación"*, y luego *Opciones de desarrollador → Depuración USB / ADB por red*.
3. En la computadora:

```bash
adb connect 172.20.10.10:5555
adb install -r app-debug.apk
adb shell cmd package set-home-activity com.juandiego.priuslauncher/.MainActivity
```

La última línea lo deja como pantalla de inicio. Si tu pantalla no la acepta, presiona el botón de Inicio y elige **Prius Launcher → Siempre**.

## Primer uso

- Acepta el permiso de **ubicación** (para el velocímetro).
- Toca la tarjeta de música y activa **acceso a notificaciones** para ver la canción.
- **Mantén presionado** cualquier botón de apps para elegir qué app abre (por ejemplo, la radio de tu pantalla).
- **Mantén presionada la foto** del carro para elegir otra de la galería.

## Personalizar el código

| Quiero cambiar… | Archivo |
|---|---|
| Foto original | `app/src/main/res/drawable-nodpi/mi_carro.png` |
| Texto "TOYOTA PRIUS / HYBRID · 2011" | `app/src/main/res/values/strings.xml` |
| Colores de la lista | `Prefs.kt` → `PRESETS` |
| Apps predeterminadas de los botones | `DockSlots.kt` |
| Tamaños | `res/values/dimens.xml` y `res/values-h520dp/dimens.xml` |
