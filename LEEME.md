# Prius Launcher

Pantalla de inicio para la pantalla Android del Prius 2011, con la foto del carro de fondo.

**Qué tiene**

- Tu Prius de fondo, a pantalla completa
- Reloj grande, fecha en español y saludo según la hora
- Tarjeta de música con título, artista, portada y botones ⏮ ⏯ ⏭ (funciona con Spotify, YouTube Music, Bluetooth, etc.)
- Barra de abajo: Mapas · Música · Teléfono · Radio · Ajustes · Apps
- Lista de todas tus apps
- Se adapta a pantallas de 1024×600 y 1280×720

---

## 1. Sacar el APK

Elige **una** de estas dos formas.

### Opción A: Android Studio (en tu computadora)

1. Instala [Android Studio](https://developer.android.com/studio) (gratis).
2. **File → Open** y elige la carpeta `PriusLauncher`. Espera a que termine de sincronizar (la primera vez tarda unos minutos).
3. Menú **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
4. Cuando termine, toca **locate** en el aviso. El archivo es `app-debug.apk`.

### Opción B: GitHub (sin instalar nada)

1. Crea un repositorio nuevo en github.com y sube todo el contenido de la carpeta `PriusLauncher` (incluida la carpeta oculta `.github`).
2. Ve a la pestaña **Actions**. La compilación arranca sola; tarda unos 3–5 minutos.
3. Cuando salga la palomita verde, entra a la compilación y descarga **PriusLauncher-apk** (viene en un .zip con el APK adentro).

## 2. Instalarlo en la pantalla del carro

1. Copia el `.apk` a una memoria USB.
2. Conéctala a la pantalla y ábrelo con el administrador de archivos.
3. Si te pide permiso para "instalar apps de fuentes desconocidas", actívalo para el administrador de archivos.

## 3. Ponerlo como pantalla de inicio

1. Presiona el botón de **Inicio** (casita).
2. Te preguntará qué app usar: elige **Prius Launcher** → **Siempre**.

Si no pregunta: **Ajustes → Apps → Apps predeterminadas → App de inicio** → Prius Launcher.

> En algunas pantallas chinas el launcher de fábrica está "bloqueado". Si es tu caso, busca en los ajustes de fábrica (a veces con la clave `8888` o `1234`) una opción como *Launcher* o *Selección de escritorio*.

## 4. Para ver la canción que está sonando

Toca la tarjeta de música una vez y activa **Prius Launcher** en "Acceso a notificaciones". Sin esto los botones siguen funcionando, solo no se ve el título.

## 5. Cambiar qué abre cada botón

**Mantén presionado** cualquier botón de abajo y elige la app. Ejemplo: mantén presionado **Radio** y elige la app de radio de tu pantalla. Ahí mismo está **Restablecer** para volver a la opción original.

---

## Personalizar

| Quiero cambiar… | Archivo |
|---|---|
| La foto de fondo | Reemplaza `app/src/main/res/drawable-nodpi/prius_bg.jpg` (horizontal, 1920×1080 ideal; el carro a la izquierda) |
| Mi nombre en el saludo | `app/src/main/res/values/strings.xml` → `driver_name` |
| Tamaños de letra e íconos | `app/src/main/res/values/dimens.xml` (pantallas bajas) y `values-h560dp/dimens.xml` (pantallas altas) |
| Colores | `app/src/main/res/values/colors.xml` |
| Apps predeterminadas de cada botón | `app/src/main/java/com/juandiego/priuslauncher/DockSlots.kt` |

Después de cualquier cambio vuelve a compilar (paso 1) e instala el APK nuevo encima del anterior.
