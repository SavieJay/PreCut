"""Drives the app on a connected emulator/device for manual checks (needs adb).

Usage: python3 tools/ui.py list | find TEXT [i] | tap TEXT [i] | scrollto TEXT ROW_Y
  TEXT matches an element's text or content description (substring, any case); [i] picks the
  i-th match. scrollto swipes the horizontal row at screen y=ROW_Y left until TEXT shows, then taps it.
"""
import re, subprocess, sys, xml.etree.ElementTree as ET


def dump():
    subprocess.run(['adb', 'shell', 'uiautomator', 'dump', '/sdcard/ui.xml'], capture_output=True)
    xml = subprocess.run(['adb', 'exec-out', 'cat', '/sdcard/ui.xml'], capture_output=True).stdout
    return ET.fromstring(xml)


def nodes(root):
    for n in root.iter('node'):
        label = (n.get('text') or '') + ' | ' + (n.get('content-desc') or '')
        b = [int(v) for v in re.findall(r'\d+', n.get('bounds', '[0,0][0,0]'))]
        yield n, label, b


def matches(root, needle):
    needle = needle.lower()
    return [(l, b) for _, l, b in nodes(root) if needle in l.lower()]


def tap(label, b):
    x, y = (b[0] + b[2]) // 2, (b[1] + b[3]) // 2
    print(f'tap {label!r} at {x},{y}')
    subprocess.run(['adb', 'shell', 'input', 'tap', str(x), str(y)])


cmd = sys.argv[1]
if cmd == 'list':
    for n, l, b in nodes(dump()):
        if l.strip(' |') or n.get('clickable') == 'true':
            print(f"{l.strip(' |')!r:60} {b} {'click' if n.get('clickable') == 'true' else ''}")
elif cmd in ('find', 'tap'):
    found = matches(dump(), sys.argv[2])
    idx = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    if len(found) <= idx:
        print('NOT FOUND:', sys.argv[2])
        sys.exit(1)
    label, b = found[idx]
    if cmd == 'tap':
        tap(label, b)
    else:
        print(f'found {label!r} at {b}')
elif cmd == 'scrollto':
    row_y = sys.argv[3]
    for _ in range(12):
        found = matches(dump(), sys.argv[2])
        if found:
            tap(*found[0])
            sys.exit(0)
        subprocess.run(['adb', 'shell', 'input', 'swipe', '900', row_y, '300', row_y, '300'])
    print('NOT FOUND after scrolling:', sys.argv[2])
    sys.exit(1)
