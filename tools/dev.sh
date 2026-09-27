#!/usr/bin/env bash
# Харнес разработки Open Scales: сборка, установка, логи BLE, скриншоты, виртуальные весы.
# Использование: tools/dev.sh <команда> [аргументы]. Без аргументов — список команд.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/homebrew/share/android-commandlinetools}"
OUT="${OUT:-$ROOT/build/dev}"
# Без вотчера файлов: на этой машине он зависает в NativeFileWatcher.startWatching0, и сборка висит бесконечно.
GRADLE=("$ROOT/gradlew" -p "$ROOT" --no-watch-fs)
mkdir -p "$OUT"

# Устройство: $ANDROID_SERIAL, иначе первый телефон (не эмулятор), иначе эмулятор.
device() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then echo "$ANDROID_SERIAL"; return; fi
  local phone emu
  phone=$(adb devices | awk 'NR>1 && $2=="device" && $1 !~ /^emulator-/ {print $1; exit}')
  emu=$(adb devices | awk 'NR>1 && $2=="device" && $1 ~ /^emulator-/ {print $1; exit}')
  echo "${phone:-$emu}"
}
adbd() { adb -s "$(device)" "$@"; }

cmd="${1:-help}"; shift || true
case "$cmd" in
  test)      "${GRADLE[@]}" :app:testDebugUnitTest ${1+"$@"} ;;          # tools/dev.sh test --tests '*FrameCodecTest'
  check)     "${GRADLE[@]}" :app:verifyRoborazziDebug :app:assembleDebug :app:lintDebug   # тесты + сверка скриншотов
             tail -1 "$ROOT/app/build/reports/lint-results-debug.txt" ;;
  install)   variant="${1:-debug}"
             task="assemble$(echo "${variant:0:1}" | tr a-z A-Z)${variant:1}"
             "${GRADLE[@]}" ":app:$task"
             adbd install -r "$ROOT/app/build/outputs/apk/$variant/app-$variant.apk" ;;
  start)     adbd shell am start -S -W -n dev.openscales/.MainActivity ;;
  grant)     adbd shell pm grant dev.openscales android.permission.BLUETOOTH_SCAN
             adbd shell pm grant dev.openscales android.permission.BLUETOOTH_CONNECT ;;
  stayon)    adbd shell svc power stayon usb ;;                                   # экран не гаснет при USB
  sim)       # Виртуальные весы (debug): tools/dev.sh sim pour 250 30 | weight 15 | unit oz | drop | back | status …
             out=$(adbd shell am broadcast -n dev.openscales/.debug.SimControlReceiver -a dev.openscales.SIM \
               --es cmd "\"$*\"")
             answer=$(sed -n 's/.*data="\(.*\)"$/\1/p' <<<"$out")
             echo "${answer:-no answer: debug build installed and started?}" ;;
  shot)      f="$OUT/${1:-shot}.png"; adbd exec-out screencap -p > "$f"; echo "$f" ;;
  tap)       adbd shell input tap "$1" "$2" ;;                                     # координаты в пикселях экрана
  find)      adbd shell uiautomator dump /sdcard/ui.xml >/dev/null               # bounds элемента по тексту/описанию
             adbd shell cat /sdcard/ui.xml | grep -o "\(text\|content-desc\)=\"$1\"[^>]*bounds=\"[^\"]*\"" | grep -o 'bounds="[^"]*"' ;;
  ble)       # Поток BLE (debug-сборка): фазы, TX/RX без повторяющихся нулевых кадров веса.
             adbd logcat -v time -s ScaleSession:I BleTransport:D ScaleScanner:D \
               | grep --line-buffered -vE 'RX fff1 A5 5A 01 01 00 09( 00){11}$' ;;
  scan-log)  adbd logcat -d -s ScaleScanner:D | sed -E 's/.*seen //' | awk '!a[$1]++' ;;
  emu)       nohup "$ANDROID_HOME/emulator/emulator" -avd openscales_phone -no-window -no-audio \
               -no-boot-anim -no-snapshot >/dev/null 2>&1 &
             until [[ "$(adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do sleep 3; done
             echo "emulator-5554 ready" ;;
  emu-narrow) adb -s emulator-5554 shell wm density "${1:-540}" ;;               # 540 → ширина 320 dp; reset — сброс
  emu-kill)  adb -s emulator-5554 emu kill ;;
  digits-font) "$ROOT/tools/make-digits-font.sh" ;;                            # перегенерировать шрифт цифр показаний
  help|*)    sed -n 's/^  \([a-z-]*\))[^#]*\(#.*\)\{0,1\}$/  \1 \2/p' "$0" ;;
esac
