#!/usr/bin/env bash
# Prueba CarGO v4 en el emulador. Uso: emu_v4.sh <ancho>x<alto> <sabor: prius|jeep>
# Abre la pantalla, ubica el carro en Irvine y toma capturas: inicio, toque largo (no debe editar),
# Ajustes, modo editar (desde Ajustes) y cambio de tema. Deja errores, capturas y el APK probado en el log.
set +e
size="$1"; flv="$2"; W=${size%x*}; H=${size#*x}
if [ "$flv" = jeep ]; then pkg=com.juandiego.jeeplauncher; else pkg=com.juandiego.priuslauncher; fi
act=com.juandiego.priuslauncher.v4.V4Activity
adb shell wm density 160
sleep 3
adb shell wm size; adb shell getprop ro.build.version.sdk
apk=$(ls app/build/outputs/apk/$flv/release/*.apk | head -1)
echo "===== $size $flv: $apk"
adb install -r -g "$apk"
adb shell cmd notification allow_listener $pkg/com.juandiego.priuslauncher.MediaListenerService
adb logcat -c
adb shell am start -n "$pkg/$act"
for i in 1 2 3 4 5 6; do adb emu geo fix -117.8265 33.6846 20 8 30; sleep 2; done
sleep 12
shot() { adb exec-out screencap -p > "shot_$1.png"; echo "===== $size: SHOT $1 BEGIN"; base64 -w 3000 "shot_$1.png" | sed "s/^/B64:/"; echo "===== $size: SHOT $1 END"; }
echo "===== $size: proceso"; adb shell pidof "$pkg" || echo "PROCESO MUERTO"
shot inicio
s=$(awk "BEGIN{print $H/600}"); dw=$(awk "BEGIN{print $W/$s}"); ox=$(awk "BEGIN{print ($dw-820)/2}")
P() { awk "BEGIN{printf \"%d %d\", ($1)*$s, ($2)*$s}"; }
# toque largo sobre el carro: ya NO debe abrir el modo editar
cx=$(awk "BEGIN{print 306+($dw-612)/2}")
adb shell input swipe $(P $cx 334) $(P $cx 334) 1200; sleep 2; shot toque_largo
# Ajustes (⚙)
gear="10+($dw-20-120)+16"
adb shell input tap $(P "$gear" 39); sleep 3; shot ajustes
# Editar widgets (desde Ajustes)
adb shell input tap $(P "$ox+428+184" "30+322+23"); sleep 2; shot editar
adb shell input tap $(P "10+$dw-20-12-50" 39); sleep 2
# Tema: Prius → Claro; Jeep (tiene Navidad) → Auto (oro rosa)
adb shell input tap $(P "$gear" 39); sleep 2
if [ "$flv" = jeep ]; then tx="$ox+24+4+45"; else tx="$ox+208"; fi
adb shell input tap $(P "$tx" "30+346+22"); sleep 1; shot ajustes_tema
adb shell input tap $(P "$ox+706" "30+540-24-23"); sleep 5
for i in 1 2 3; do adb emu geo fix -117.8265 33.6846 20 8 30; sleep 1; done
sleep 4; shot tema
adb shell input tap $(P "$dw-10-24" "336+24"); sleep 2; shot apps
echo "===== $size: proceso final"; adb shell pidof "$pkg" || echo "PROCESO MUERTO"
echo "===== $size: logcat (errores)"
adb logcat -d -v brief AndroidRuntime:E '*:S' | tail -n 80
echo "===== $size: APK_SHA256 $(sha256sum "$apk" | cut -d' ' -f1)"
echo "===== $size: APK_BEGIN"; base64 -w 3000 "$apk" | sed "s/^/APK:/"; echo "===== $size: APK_END"
exit 0
