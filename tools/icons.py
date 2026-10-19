"""Material Symbols Rounded SVGs -> Android vector drawables (weight 400, as the app's icons).

Usage: python3 tools/icons.py SVG_DIR app/src/main/res/drawable ic_name=symbol[:1] ...
  SVG_DIR holds <symbol>[_fill1]_24px.svg files from
  https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web/<symbol>/materialsymbolsrounded/<symbol>[_fill1]_24px.svg
  ":1" picks the filled variant.
"""
import os, re, sys

sym, res, specs = sys.argv[1], sys.argv[2], sys.argv[3:]
for spec in specs:
    res_name, symbol = spec.split('=')
    name, _, fill = symbol.partition(':')
    svg = open(os.path.join(sym, f"{name}{'_fill1' if fill else ''}_24px.svg")).read()
    paths = re.findall(r'<path d="([^"]+)"', svg)
    assert paths and 'viewBox="0 -960 960 960"' in svg, spec
    body = ''.join(f'''        <path
            android:fillColor="#FF000000"
            android:pathData="{d}" />
''' for d in paths)
    open(os.path.join(res, res_name + '.xml'), 'w').write(f'''<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "{name}"{" (filled)" if fill else ""}, weight 400 (Apache 2.0). -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
{body}    </group>
</vector>
''')
print(len(specs), 'icons')
