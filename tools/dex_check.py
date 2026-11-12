#!/usr/bin/env python3
"""Fails if R8 left the Android 10 crash pattern in a release APK (build 64, November 2026).

R8's class merging can widen a field to Object and cast it back to a class newer than minSdk on
every read; ART on older Android fails that cast's class resolution even when the value is null
(NoClassDefFoundError). app/proguard-rules.pro stops it for the known classes; this check catches a
new one. It dumps the APK's dex with dexdump and looks for `iget-object … :Ljava/lang/Object;`
followed by a `check-cast` to an android.* class added after [min_sdk].

Usage: python3 -I tools/dex_check.py APP.apk [MIN_SDK]   (needs ANDROID_HOME with build-tools and
the compile SDK platform, for dexdump and api-versions.xml). Exit 1 and a list when anything's found.
"""
import glob
import os
import re
import subprocess
import sys
import tempfile
import zipfile


def sdk_file(pattern):
    sdk = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Android/Sdk")
    found = sorted(glob.glob(os.path.join(sdk, pattern)))
    if not found:
        sys.exit(f"not found under the Android SDK: {pattern}")
    return found[-1]


def main():
    apk, min_sdk = sys.argv[1], int(sys.argv[2]) if len(sys.argv) > 2 else 26
    dexdump = sdk_file("build-tools/*/dexdump")
    since = {m.group(1): int(m.group(2)) for m in re.finditer(
        r'<class name="([^"]+)" since="(\d+)"', open(sdk_file("platforms/*/data/api-versions.xml")).read())}
    risky = {}
    with tempfile.TemporaryDirectory() as tmp, zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            if not re.fullmatch(r"classes\d*\.dex", name):
                continue
            path = z.extract(name, tmp)
            dump = subprocess.run([dexdump, "-d", path], capture_output=True, text=True, errors="replace").stdout
            method, previous = None, None
            for line in dump.splitlines():
                m = re.search(r"\|\[[0-9a-f]+\] ([^ ]+)\(", line)
                if m:
                    method, previous = m.group(1), None
                    continue
                m = re.search(r"\|[0-9a-f]{4}: (\S+) (.*)", line)
                if not m:
                    continue
                op, rest = m.groups()
                if op == "check-cast" and previous and previous[0].startswith("iget-object") and ":Ljava/lang/Object;" in previous[1]:
                    t = re.search(r"L(android/[^;]+);", rest)
                    if t and since.get(t.group(1), 0) > min_sdk:
                        risky.setdefault((since[t.group(1)], t.group(1)), set()).add(method)
                previous = (op, rest)
    for (level, cls), methods in sorted(risky.items(), reverse=True):
        print(f"API {level}  {cls}  in {sorted(methods)}")
    total = sum(len(m) for m in risky.values())
    print(f"risky casts: {total}")
    sys.exit(1 if total else 0)


if __name__ == "__main__":
    main()
