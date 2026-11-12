#!/usr/bin/env bash
# Prueba CarGO v4 en el emulador: abre la pantalla, ubica el carro en Irvine a ~35 mph,
# y toma capturas de: inicio, Ajustes y modo editar. Deja en el log errores, capturas y el APK.
set +e
size="$1"; W=${size%x*}; H=${size#*x}
pkg=com.juandiego.priuslauncher; act=com.juandiego.priuslauncher.v4.V4Activity
adb shell wm density 160
sleep 3
adb shell wm size; adb shell getprop ro.build.version.sdk
apk=$(ls app/build/outputs/apk/prius/release/*.apk | head -1)
echo "===== $size: $apk"
adb install -r -g "$apk"
adb shell cmd notification allow_listener $pkg/$pkg.MediaListenerService
adb shell settings put global auto_time 1
adb logcat -c
adb shell am start -n "$pkg/$act"
for i in 1 2 3 4 5 6; do adb emu geo fix -117.8265 33.6846 20 8 30; sleep 2; done
sleep 12
shot() { adb exec-out screencap -p > "shot_$1.png"; echo "===== $size: SHOT $1 BEGIN"; base64 -w 3000 "shot_$1.png" | sed "s/^/B64:/"; echo "===== $size: SHOT $1 END"; }
echo "===== $size: proceso"; adb shell pidof "$pkg" || echo "PROCESO MUERTO"
shot inicio
# escala del diseño: alto / 600
s=$(awk "BEGIN{print $H/600}"); dw=$(awk "BEGIN{print $W/$s}")
gx=$(awk "BEGIN{printf \"%d\", (10+($dw-20-120)+16)*$s}"); gy=$(awk "BEGIN{printf \"%d\", 39*$s}")
adb shell input tap $gx $gy; sleep 3; shot ajustes
adb shell input keyevent KEYCODE_BACK; sleep 2
cx=$(awk "BEGIN{printf \"%d\", (306+($dw-612)/2)*$s}"); cy=$(awk "BEGIN{printf \"%d\", 334*$s}")
adb shell input swipe $cx $cy $cx $cy 1200; sleep 2; shot editar
adb shell input keyevent KEYCODE_BACK; sleep 1
echo "===== $size: proceso final"; adb shell pidof "$pkg" || echo "PROCESO MUERTO"
echo "===== $size: logcat (errores)"
adb logcat -d -v brief AndroidRuntime:E '*:S' | tail -n 80
adb logcat -d -v brief | grep -iE "priuslauncher|V4Activity|FATAL" | tail -n 40
echo "===== $size: APK_SHA256 $(sha256sum "$apk" | cut -d' ' -f1)"
exit 0
