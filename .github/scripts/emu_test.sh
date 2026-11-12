#!/usr/bin/env bash
# Uso: emu_test.sh <sabor> <ancho>x<alto>
# Instala el APK en el emulador con la resolución real de la pantalla, lo abre como en el carro y deja
# en el log: si el proceso sigue vivo, los errores, una captura y el APK probado (base64) para entregarlo.
set +e
f="$1"; size="$2"
adb shell wm size "$size"
adb shell wm density 160
adb shell settings put system user_rotation 1
adb shell settings put system accelerometer_rotation 0
sleep 3
adb shell wm size
adb shell getprop ro.build.version.sdk

pkg="com.cargo.launcher.$f"
apk=$(ls cargo/build/outputs/apk/$f/debug/*.apk | head -1)
echo "===== $f: $apk"
adb install -r -g "$apk"
adb logcat -c
adb shell am start -n "$pkg/com.cargo.launcher.MainActivity"
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
