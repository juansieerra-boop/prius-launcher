#!/usr/bin/env bash
# Instala cada APK en el emulador, la abre como en el carro y deja en el log el error (si lo hay)
# y una captura en base64 para revisarla.
set +e
adb shell wm size 1024x600
adb shell wm density 160
sleep 3
for f in prius jeep; do
  pkg="com.cargo.launcher.$f"
  apk=$(ls cargo/build/outputs/apk/$f/debug/*.apk | head -1)
  echo "===== $f: $apk"
  adb install -r -g "$apk"
  adb logcat -c
  adb shell am start -n "$pkg/com.cargo.launcher.MainActivity"
  sleep 20
  echo "===== $f: proceso"
  adb shell pidof "$pkg" || echo "PROCESO MUERTO"
  echo "===== $f: logcat (errores)"
  adb logcat -d -v brief AndroidRuntime:E ActivityManager:W CarGo:V System.err:W '*:F' | tail -n 120
  echo "===== $f: logcat de la app"
  pid=$(adb shell pidof "$pkg" | tr -d '\r')
  [ -n "$pid" ] && adb logcat -d -v brief --pid="$pid" | grep -E " [EW]/" | tail -n 60
  adb exec-out screencap -p > shot_$f.png
  echo "===== $f: SHOT_BEGIN"
  base64 -w 0 shot_$f.png
  echo
  echo "===== $f: SHOT_END"
  adb shell am force-stop "$pkg"
done
exit 0
