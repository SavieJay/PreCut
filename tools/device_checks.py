"""Measures DeviceChecksTest's files (see device_checks.sh). Prints one PASS/FAIL line per check."""
import json
import math
import os
import struct
import subprocess
import sys

from PIL import Image, ImageDraw

OUT = sys.argv[1]
FRAME = 1 / 30
results = []


def check(name, ok, detail):
    results.append(ok)
    print(f"{'PASS' if ok else 'FAIL'}  {name}: {detail}")


def path(name):
    return os.path.join(OUT, name)


def expected_ms(name):
    with open(path(name + ".json")) as f:
        return json.load(f)["totalDurationMs"]


def frame_times(video):
    out = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "frame=pts_time", "-of", "csv=p=0", video],
        capture_output=True, text=True, check=True).stdout
    return [float(t.split(",")[0]) for t in out.split()]


def small_frames(video, w=36, h=64):
    raw = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", video, "-vf", f"scale={w}:{h}", "-f", "rawvideo", "-pix_fmt", "rgb24", "-"],
        capture_output=True, check=True).stdout
    size = w * h * 3
    return [raw[i:i + size] for i in range(0, len(raw), size)]


def is_red(r, g, b):
    return r > 150 and g < 90 and b < 90


def has_red(rgb):
    return any(is_red(rgb[i], rgb[i + 1], rgb[i + 2]) for i in range(0, len(rgb), 3))


def envelope(media, window=0.02):
    """RMS per window of the mono mix, at 8 kHz."""
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", media, "-ac", "1", "-ar", "8000", "-f", "s16le", "-"],
                         capture_output=True, check=True).stdout
    samples = struct.unpack(f"<{len(raw) // 2}h", raw)
    n = int(8000 * window)
    return [math.sqrt(sum(s * s for s in samples[i:i + n]) / n) for i in range(0, len(samples) - n + 1, n)], window


def spans(env, window, threshold):
    """(start, end) seconds of runs above threshold."""
    out, start = [], None
    for i, v in enumerate(env + [0]):
        if v > threshold and start is None:
            start = i * window
        elif v <= threshold and start is not None:
            out.append((round(start, 3), round(i * window, 3)))
            start = None
    return out


def red_mask_stats(img):
    img = img.convert("RGB")
    w, h = img.size
    px = img.load()
    pts = [(x, y) for y in range(h) for x in range(w) if is_red(*px[x, y])]
    n = len(pts)
    if n == 0:
        return None
    cx = sum(p[0] for p in pts) / n
    cy = sum(p[1] for p in pts) / n
    mxx = sum((p[0] - cx) ** 2 for p in pts) / n
    myy = sum((p[1] - cy) ** 2 for p in pts) / n
    mxy = sum((p[0] - cx) * (p[1] - cy) for p in pts) / n
    angle = math.degrees(0.5 * math.atan2(2 * mxy, mxx - myy))  # clockwise on screen (y down)
    return {"n": n, "cx": cx, "cy": cy, "angle": angle, "set": set(pts)}


def frame_at(video, seconds):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-ss", f"{seconds:.3f}", "-i", video, "-frames:v", "1",
                          "-f", "image2pipe", "-vcodec", "png", "-"], capture_output=True, check=True).stdout
    import io
    return Image.open(io.BytesIO(raw))


# Text timing: text 4.0-5.0 s over the second clip (cut at 3.0 s).
v = path("text_timing.mp4")
times, frames = frame_times(v), small_frames(v)
red = [t for t, f in zip(times, frames) if has_red(f)]
first, last = (red[0], red[-1]) if red else (None, None)
ok = bool(red) and abs(first - 4.0) <= FRAME / 2 and 5.0 - FRAME * 1.5 <= last < 5.0
check("text timing (export)", ok, f"text frames {first}..{last} s, expected 4.000..~4.967")
want = {3966: False, 4000: True, 4966: True, 5000: False}
seen = {ms: has_red(Image.open(path(f"text_timing_preview_{ms}.png")).convert("RGB").resize((36, 64)).tobytes()) for ms in want}
check("text timing (preview)", seen == want, f"text shown at {seen}, expected {want}")

# Picture timing: video.mp4's brightness steps once per source frame (mod 50). Both clips show the
# source at its own time, so between two exported frames the picture must step as many source
# frames as their timestamps are apart. Sampled at the picture's left edge (the text is centred);
# the step size is measured, so range conversions don't matter.
def picture_steps(times, frames):
    edge = (32 * 36 + 3) * 3
    levels = [f[edge + 1] for f in frames]
    diffs = [b - a for a, b in zip(levels, levels[1:])]
    usual = [d for d in diffs if 2 <= d <= 8]
    step = sum(usual) / len(usual)
    moved = []
    for i, d in enumerate(diffs):
        want = round((times[i + 1] - times[i]) * 30) % 50
        got = round(d / step) % 50
        if got != want:
            moved.append((round(times[i + 1], 3), f"stepped {got} frames, timestamps {want}"))
    return step, moved


step, moved = picture_steps(times, frames)
check("picture timing across a cut (export)", not moved, f"step {step:.2f}; mismatches: {moved[:6]}")
want_frames = round(expected_ms("text_timing") / 1000 * 30)
check("no frames lost at cuts or the end", len(times) == want_frames, f"{len(times)} frames, expected {want_frames}")

v = path("text_timing_free.mp4")
if os.path.exists(v):
    t, f = frame_times(v), small_frames(v)
    step, moved = picture_steps(t, f)
    check("picture timing, no frame-rate pass (reference)", not moved, f"{len(t)} frames; mismatches: {moved[:6]}")

v = path("text_timing_one.mp4")
red = [t for t, f in zip(frame_times(v), small_frames(v)) if has_red(f)]
first, last = (red[0], red[-1]) if red else (None, None)
ok = bool(red) and abs(first - 4.0) <= FRAME / 2 and 5.0 - FRAME * 1.5 <= last < 5.0
check("text timing, one clip (export)", ok, f"text frames {first}..{last} s, expected 4.000..~4.967")

# Rotated text: preview and export frames at 1.0 s.
exp = red_mask_stats(frame_at(path("rotated_text.mp4"), 1.0))
pre = red_mask_stats(Image.open(path("rotated_text_preview_1000.png")))
if exp and pre:
    iou = len(exp["set"] & pre["set"]) / len(exp["set"] | pre["set"])
    dc = math.hypot(exp["cx"] - pre["cx"], exp["cy"] - pre["cy"])
    da = abs(exp["angle"] - pre["angle"])
    check("rotated text preview = export", iou > 0.85 and dc < 3 and da < 1.5,
          f"overlap {iou:.2f}, centre shift {dc:.1f}px, angle {exp['angle']:.1f} vs {pre['angle']:.1f} deg")
    check("rotated text angle", abs(exp["angle"] - 30) < 2, f"{exp['angle']:.1f} deg clockwise, expected 30")
else:
    check("rotated text preview = export", False, f"red box found in export: {bool(exp)}, preview: {bool(pre)}")

# Audio row with gaps: tone at 1-2 s and 3-5 s.
env, w = envelope(path("audio_gaps.mp4"))
found = spans(env, w, max(env) * 0.1)
want = [(1.0, 2.0), (3.0, 5.0)]
ok = len(found) == 2 and all(abs(a - c) <= 0.05 and abs(b - d) <= 0.05 for (a, b), (c, d) in zip(found, want))
check("audio row gaps", ok, f"sound at {found}, expected {want}")

# Fades at 2x: 1 s linear fades in timeline time on a 4 s item.
for name in ("clip_fades", "audio_fades"):
    env, w = envelope(path(name + ".mp4"))
    total = expected_ms(name) / 1000
    plateau = sorted(env[int(1.5 / w):int(2.5 / w)])[int(0.5 / w)]
    norm = [e / plateau for e in env]
    half_in = next(i for i, e in enumerate(norm) if e >= 0.5) * w
    full_in = next(i for i, e in enumerate(norm) if e >= 0.95) * w
    half_out = max(i for i, e in enumerate(norm) if e >= 0.5) * w + w
    ok = abs(half_in - 0.5) <= 0.06 and abs(full_in - 0.95) <= 0.08 and abs(half_out - (total - 0.5)) <= 0.06
    check(f"{name} at 2x", ok, f"half volume at {half_in:.2f} s, full at {full_in:.2f} s, half again at {half_out:.2f} s "
                               f"(expected 0.50, ~0.95, {total - 0.5:.2f})")

# High speed: 50x and 100x of 60 s, then 1 s plain.
v = path("high_speed.mp4")
total = expected_ms("high_speed") / 1000
times = frame_times(v)
video_len = times[-1] + FRAME
audio_len = float(subprocess.run(["ffprobe", "-v", "error", "-select_streams", "a", "-show_entries", "stream=duration",
                                  "-of", "csv=p=0", v], capture_output=True, text=True).stdout.strip() or 0)
centre = (32 * 36 + 18) * 3  # the middle of a 36x64 frame (the 16:9 clips are letterboxed)
grey = next((t for t, f in zip(times, small_frames(v)) if abs(f[centre + 2] - f[centre]) < 60), None)  # not blue
check("high speed length", abs(video_len - total) <= FRAME + 0.01 and abs(audio_len - total) <= 0.06,
      f"video {video_len:.3f} s, audio {audio_len:.3f} s, timeline {total:.3f} s")
check("high speed cut", grey is not None and abs(grey - (total - 1.0)) <= FRAME + 0.001,
      f"plain clip starts at {grey} s, expected {total - 1.0:.3f}")

# Reverse: 3 s (90 frames) of video.mp4 backwards, so the picture steps back one source frame each frame.
def display_size(video):
    out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-show_entries", "stream=width,height:stream_side_data=rotation",
                          "-of", "default=nw=1", video], capture_output=True, text=True).stdout
    values = dict(line.split("=", 1) for line in out.split() if "=" in line)
    w, h = int(values["width"]), int(values["height"])
    return (h, w) if abs(int(float(values.get("rotation", "0")))) % 180 == 90 else (w, h)


v = path("reverse.mp4")
times, frames = frame_times(v), small_frames(v, 32, 32)
middle = (16 * 32 + 16) * 3
levels = [f[middle + 1] for f in frames]
diffs = [b - a for a, b in zip(levels, levels[1:])]
usual = [-d for d in diffs if -8 <= d <= -2]
step = sum(usual) / len(usual) if usual else 0
wrong = [(round(times[i + 1], 3), round(d / step) % 50 if step else None) for i, d in enumerate(diffs) if not step or round(d / step) % 50 != 49]
check("reverse frame order", step > 0 and not wrong and 89 <= len(times) <= 91,
      f"{len(times)} frames, each one source frame back; wrong steps: {wrong[:6]}")
source, reversed_ = display_size(f"{OUT}/../in/rotated.mp4"), display_size(path("reverse_rotated.mp4"))
check("reverse keeps rotation", (source[0] < source[1]) == (reversed_[0] < reversed_[1]),
      f"source shows {source[0]}x{source[1]}, reversed shows {reversed_[0]}x{reversed_[1]}")

# Beat detection on the phone: a 120 BPM click track whose clicks start at 0.25 s.
with open(path("beats.json")) as f:
    found = json.load(f)
beats = found["beatsMs"]
truth = [250 + 500 * k for k in range(4, 39)]  # after the first 2 s, as the unit tests
hits = sum(1 for t in truth if any(abs(b - t) <= 30 for b in beats))
offsets = sorted(b - min(truth, key=lambda t: abs(t - b)) for b in beats)
check("beat detection", abs(found["bpm"] - 120) <= 2.4 and hits >= 0.9 * len(truth),
      f"{found['bpm']:.1f} BPM, {hits}/{len(truth)} clicks found within 30 ms, median offset {offsets[len(offsets) // 2] if offsets else 'n/a'} ms")

# AutoCut: four frame-coded clips cut to the clicks; each cut (a jump in the picture) should sit on a beat.
with open(path("autocut_beats.json")) as f:
    cut_beats = json.load(f)["beatsMs"]
v = path("autocut.mp4")
t, fr = frame_times(v), small_frames(v)
step, moved = picture_steps(t, fr)
cuts = [time for time, _ in moved]
off = [round(c * 1000) for c in cuts if not any(abs(c * 1000 - b) <= 1000 / 30 + 1 for b in cut_beats)]
check("AutoCut cuts land on beats", len(cuts) == 3 and not off, f"cuts at {cuts}; not on a beat: {off}; beats {cut_beats[:5]}")

# Template fill: slots of 1.0, 1.5 and 0.8 s, each filled from the start of the frame-coded video.
v = path("template_fill.mp4")
t, fr = frame_times(v), small_frames(v)
step, moved = picture_steps(t, fr)
cuts = [time for time, _ in moved]
on_time = len(cuts) == 2 and abs(cuts[0] - 1.0) <= 1 / 30 + 0.001 and abs(cuts[1] - 2.5) <= 1 / 30 + 0.001
check("template slots start on time", on_time and len(t) == 99, f"slots change at {cuts} (expected 1.0, 2.5), {len(t)} frames (expected 99)")

# Proxy keyframes: at most 0.2 s apart.
out = subprocess.run(["ffprobe", "-v", "error", "-select_streams", "v", "-skip_frame", "nokey", "-show_entries",
                      "frame=pts_time", "-of", "csv=p=0", path("proxy.mp4")], capture_output=True, text=True).stdout
keys = [float(t.split(",")[0]) for t in out.split()]
gap = max(b - a for a, b in zip(keys, keys[1:])) if len(keys) > 1 else float("inf")
check("proxy keyframes", gap <= 0.2 + FRAME / 2, f"{len(keys)} keyframes, largest gap {gap:.3f} s")

# Phase 19B: animated pack stickers move (two preview frames 0.6 s apart differ), and their export plays.
def pixels(name):
    return list(Image.open(path(name)).convert("L").resize((90, 160)).getdata())
still, later = pixels("stickers_noto_animated_preview_300.png"), pixels("stickers_noto_animated_preview_900.png")
moved = sum(abs(a - b) for a, b in zip(still, later)) / len(still)
t = frame_times(path("stickers_noto_animated.mp4"))
check("animated stickers play", moved > 2 and len(t) == 60, f"mean change {moved:.1f} between 0.3 s and 0.9 s; export {len(t)} frames (expected 60)")

# Phase 19 catalogue: a preview frame per effect, transition, text template, font and sticker pack
# (each shader compiled on this GPU, or the test fails), and one export with all effects and
# transitions; the frames go on a sheet to look over.
frames = sorted(f for f in os.listdir(OUT) if f.startswith(("effect_", "transition_", "text_", "font_", "stickers_")) and f.endswith(".png"))
t = frame_times(path("catalogue.mp4"))
expected = expected_ms("catalogue") / 1000
check("catalogue export", abs(len(t) / 30 - expected) <= 2 * FRAME,
      f"{len(t)} frames ({len(t) / 30:.2f} s, expected {expected:.2f} s); {len(frames)} preview frames")
if frames:
    tw, th, columns = 144, 256, 10
    sheet = Image.new("RGB", (columns * tw, -(-len(frames) // columns) * (th + 14)), (12, 12, 14))
    for i, name in enumerate(frames):
        tile = Image.open(path(name)).convert("RGB")
        tile.thumbnail((tw, th))
        x, y = (i % columns) * tw, (i // columns) * (th + 14)
        sheet.paste(tile, (x, y))
        ImageDraw.Draw(sheet).text((x + 2, y + th), name.split("_preview")[0], fill=(220, 220, 220), font_size=10)
    sheet.save(path("catalogue_sheet.png"))
    print(f"      look over {path('catalogue_sheet.png')}")

print(f"\n{sum(results)}/{len(results)} passed")
sys.exit(0 if all(results) else 1)
