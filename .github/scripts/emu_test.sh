#!/usr/bin/env bash
# Uso: emu_test.sh <módulo> <sabor> <ancho>x<alto> <paquete> <actividad>
# Instala el APK en el emulador con la resolución real de la pantalla, lo abre como en el carro y deja
# en el log: si el proceso sigue vivo, los errores, una captura y el APK probado (base64) para entregarlo.
set +e
mod="$1"; flv="$2"; size="$3"; pkg="$4"; act="$5"; f="$mod-$flv"
adb shell wm density 160   # la resolución la fija el emulador (-skin); ambas pantallas son 160 dpi
sleep 3
adb shell wm size
adb shell getprop ro.build.version.sdk

apk=$(ls $mod/build/outputs/apk/$flv/*/*.apk | head -1)
echo "===== $f: $apk"
adb install -r -g "$apk"
adb logcat -c
adb shell am start -n "$pkg/$act"
sleep 25
echo "===== $f: proceso"
adb shell pidof "$pkg" || echo "PROCESO MUERTO"
echo "===== $f: logcat (errores)"
adb logcat -d -v brief AndroidRuntime:E '*:S' | tail -n 80
adb exec-out screencap -p > shot_$f.png
echo "===== $f: SHOT_BEGIN"; base64 -w 3000 shot_$f.png | sed "s/^/B64:/"; echo "===== $f: SHOT_END"
echo "===== $f: APK_SHA256 $(sha256sum "$apk" | cut -d' ' -f1)"
echo "===== $f: APK_BEGIN"; base64 -w 3000 "$apk" | sed "s/^/APK:/"; echo "===== $f: APK_END"
exit 0
