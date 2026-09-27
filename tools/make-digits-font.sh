#!/usr/bin/env bash
# Шрифт цифр показаний: Google Sans Flex из google/fonts, урезанный до цифр и знаков чисел.
# Результат лежит в git (app/src/main/res/font/readout_digits.ttf), скрипт нужен только для перегенерации.
# Использование: tools/make-digits-font.sh (нужны python3 и сеть; fontTools ставится во временный venv).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
COMMIT=3dc14e61f108f036db84188b9b405a67df9b7c88   # google/fonts, последняя правка ofl/googlesansflex на 2026-09-15
BASE="https://raw.githubusercontent.com/google/fonts/$COMMIT/ofl/googlesansflex"
WORK="$ROOT/build/dev/digits-font"
OUT="$ROOT/app/src/main/res/font/readout_digits.ttf"
LICENSE_DIRS=("$ROOT/tools/fonts" "$ROOT/app/src/main/assets/licenses")
mkdir -p "$WORK" "$(dirname "$OUT")" "${LICENSE_DIRS[@]}"

curl -sSfL -o "$WORK/source.ttf" "$BASE/GoogleSansFlex%5BGRAD,ROND,opsz,slnt,wdth,wght%5D.ttf"
curl -sSfL -o "$WORK/OFL.txt" "$BASE/OFL.txt"

[[ -x "$WORK/venv/bin/python" ]] || python3 -m venv "$WORK/venv"
"$WORK/venv/bin/pip" install --quiet fonttools
PY="$WORK/venv/bin/python"

# Оставляем только оптический размер и насыщенность, остальные оси — в значениях по умолчанию.
"$PY" -m fontTools.varLib.instancer "$WORK/source.ttf" wdth=100 GRAD=0 ROND=0 slnt=0 -q -o "$WORK/pinned.ttf"
# Цифры, точка, двоеточие, дефис, минус, тире, пробел; tnum — табличные цифры, zero — перечёркнутый ноль.
"$PY" -m fontTools.subset "$WORK/pinned.ttf" \
  --unicodes="U+0030-0039,U+002E,U+003A,U+002D,U+2212,U+2014,U+0020" \
  --layout-features="tnum,zero" --output-file="$WORK/subset.ttf"

# Изменённую версию нельзя называть «Google Sans Flex» (TRADEMARKS.md в google/fonts) — переименовываем.
"$PY" - "$WORK/subset.ttf" "$OUT" <<'EOF'
import sys
from fontTools.ttLib import TTFont
src, dst = sys.argv[1:]
f = TTFont(src)
family = "Open Scales Digits"
for rec in f["name"].names:
    if rec.nameID in (1, 16):
        rec.string = family
    elif rec.nameID == 4:
        rec.string = family
    elif rec.nameID == 6:
        rec.string = family.replace(" ", "")
    elif rec.nameID == 3:
        rec.string = family.replace(" ", "") + ";" + str(rec.toUnicode()).split(";")[0]
f.save(dst)
t = TTFont(dst)
print("axes:", ", ".join(a.axisTag for a in t["fvar"].axes))
print("features:", ", ".join(sorted({r.FeatureTag for r in t["GSUB"].table.FeatureList.FeatureRecord})))
print("family:", t["name"].getDebugName(1))
EOF

for d in "${LICENSE_DIRS[@]}"; do cp "$WORK/OFL.txt" "$d/readout_digits_OFL.txt"; done
echo "$(wc -c < "$OUT" | tr -d ' ') bytes → ${OUT#$ROOT/}"
