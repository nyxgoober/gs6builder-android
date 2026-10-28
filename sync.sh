#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

[ -d upstream ] || git clone --depth 1 https://github.com/UnknownSekai/GridlessSekai6.git upstream

SRC=upstream/mods/CUSTOM_CHARTS/builder

mkdir -p app/src/main/python
mkdir -p app/src/main/assets/bases

for f in charts encode deps untitledcharts ccarchive; do 
    cp "$SRC/$f.py" app/src/main/python/
done

cp -r "$SRC"/bases/. app/src/main/assets/bases/

echo "Upstream commit: $(git -C upstream rev-parse --short HEAD)"
