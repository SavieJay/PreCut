package com.savie.precut.media.effect

import com.savie.precut.model.effect.EffectType

/**
 * GLSL for [EffectShaderProgram] (GLES 2): one program per [EffectType], reading the four
 * `uParams` that [com.savie.precut.model.effect.EffectParams] documents, animated by `uTime`
 * (seconds at the effect's speed). Distances in short-side units go through `su()`, so effects
 * look the same in portrait and landscape. Separate programs keep each one small, and a shader
 * that one GPU can't compile only loses that effect.
 */
internal object EffectShaders {

    /** The fragment shader for [type]: the shared helpers, then its body, which turns `c` into the result. */
    fun fragment(type: EffectType): String = HEAD + MAIN_START + body(type).trimIndent().prependIndent("    ") + MAIN_END

    private const val HEAD = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform vec4 uParams;
uniform float uTime;
uniform vec2 uTexel;
uniform float uAspect;
uniform float uActive;
varying vec2 vTexSamplingCoord;

const float PI = 3.14159265;

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float hash1(float n) { return fract(sin(n) * 43758.5453); }
vec4 tex(vec2 uv) { return texture2D(uTexSampler, clamp(uv, 0.0, 1.0)); }
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
// A distance in short-side units as uv.
vec2 su(vec2 d) { return uAspect >= 1.0 ? vec2(d.x / uAspect, d.y) : vec2(d.x, d.y * uAspect); }

// A golden-angle spiral; each tap turns the last direction by the golden angle (no trig per tap).
vec3 blurAt(vec2 uv, float r) {
  vec3 sum = tex(uv).rgb;
  vec2 dir = vec2(1.0, 0.0);
  for (int i = 0; i < 24; i++) {
    float rr = r * sqrt((float(i) + 0.5) / 24.0);
    sum += tex(uv + su(dir * rr)).rgb;
    dir = vec2(-0.737366696 * dir.x - 0.675492676 * dir.y, 0.675492676 * dir.x - 0.737366696 * dir.y);
  }
  return sum / 25.0;
}

float edgeAt(vec2 uv) {
  vec2 t = uTexel * 1.5;
  float tl = luma(tex(uv + vec2(-t.x, -t.y)).rgb);
  float tc = luma(tex(uv + vec2(0.0, -t.y)).rgb);
  float tr = luma(tex(uv + vec2(t.x, -t.y)).rgb);
  float ml = luma(tex(uv + vec2(-t.x, 0.0)).rgb);
  float mr = luma(tex(uv + vec2(t.x, 0.0)).rgb);
  float bl = luma(tex(uv + vec2(-t.x, t.y)).rgb);
  float bc = luma(tex(uv + vec2(0.0, t.y)).rgb);
  float br = luma(tex(uv + vec2(t.x, t.y)).rgb);
  float gx = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
  float gy = -tl - 2.0 * tc - tr + bl + 2.0 * bc + br;
  return length(vec2(gx, gy));
}

vec3 hsv(float h) { return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0); }
vec3 screen(vec3 a, vec3 b) { return 1.0 - (1.0 - a) * (1.0 - b); }
// Aspect-corrected offset from the centre (x scaled by the aspect) and back.
vec2 centred(vec2 uv) { return vec2((uv.x - 0.5) * uAspect, uv.y - 0.5); }
vec2 uncentred(vec2 p) { return vec2(p.x / uAspect + 0.5, p.y + 0.5); }
// Turns a colour about the grey axis (greys stay grey).
vec3 hueRotate(vec3 col, float a) {
  vec3 k = vec3(0.57735);
  float ca = cos(a);
  return col * ca + cross(k, col) * sin(a) + k * dot(k, col) * (1.0 - ca);
}
// A thermal camera's palette: cold blue through purple, red and yellow to hot white.
vec3 thermal(float x) {
  x = clamp(x, 0.0, 1.0) * 4.0;
  if (x < 1.0) return mix(vec3(0.0, 0.0, 0.25), vec3(0.45, 0.0, 0.6), x);
  if (x < 2.0) return mix(vec3(0.45, 0.0, 0.6), vec3(0.95, 0.1, 0.15), x - 1.0);
  if (x < 3.0) return mix(vec3(0.95, 0.1, 0.15), vec3(1.0, 0.8, 0.0), x - 2.0);
  return mix(vec3(1.0, 0.8, 0.0), vec3(1.0), x - 3.0);
}
// Frame-based noise seed that stays small, so hash() keeps its precision on long clips.
float frameSeed(float fps) { return mod(floor(uTime * fps), 61.0); }

"""

    // The body runs in its own block, so its names can't clash with these.
    private const val MAIN_START = """void main() {
  vec2 uv = vTexSamplingCoord;
  vec4 src = tex(uv);
  if (uActive < 0.5) { gl_FragColor = src; return; }
  vec3 c = src.rgb;
  vec4 p = uParams;
  float t = uTime;
  float d = distance(uv, vec2(0.5)) * 1.414;
  {
"""

    private const val MAIN_END = """
  }
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), src.a);
}
"""

    private fun body(type: EffectType): String = when (type) {
        EffectType.BLUR -> "c = blurAt(uv, p.x);"
        EffectType.FLASH -> "c = mix(c, vec3(1.0), exp(-fract(t * p.y) * 7.0) * p.x);"
        EffectType.BW_FLASH -> """
            float beat = floor(t * p.y);
            c = mix(c, vec3(luma(c) * 1.08), mod(beat, 2.0) * p.x);
            c = mix(c, vec3(1.0), exp(-fract(t * p.y) * 10.0) * p.x * 0.8);
        """
        EffectType.VIGNETTE_PULSE -> """
            float pulse = 0.5 + 0.5 * sin(t * 2.0 * PI * p.y);
            c *= 1.0 - smoothstep(0.3, 1.0, d) * p.x * (0.55 + 0.45 * pulse);
        """
        EffectType.WOBBLE -> """
            vec2 w = vec2(sin(uv.y * p.y + t * 5.0), cos(uv.x * p.y + t * 4.0)) * p.x;
            c = tex(uv + w).rgb;
        """
        EffectType.EDGE -> "c = mix(c, vec3(clamp(edgeAt(uv) * 2.0, 0.0, 1.0)), p.x);"
        EffectType.OLD_FILM -> """
            float frame = floor(t * 24.0);
            c = mix(c, vec3(luma(c)) * vec3(1.07, 0.74, 0.43), p.x);
            c += (hash(uv / uTexel + frame) - 0.5) * p.z;
            float x = hash1(frame + 1.7);
            if (abs(uv.x - x) < 0.0012 && hash1(frame + 3.1) < p.y) c *= 0.55;
            c *= 1.0 - p.w * hash1(frame + 7.3);
            c *= 1.0 - 0.5 * smoothstep(0.45, 1.0, d);
        """
        EffectType.FILM_GRAIN -> "c += (hash(uv / uTexel + floor(t * 30.0) * 13.1) - 0.5) * p.x;"
        EffectType.POSTERIZE -> "c = floor(c * p.x + 0.5) / p.x;"
        EffectType.GLITCH -> """
            float tt = floor(t * 12.0);
            float row = floor(uv.y * 24.0);
            float shift = hash(vec2(row, tt)) < p.x ? (hash(vec2(row + 7.0, tt)) - 0.5) * 2.0 * p.y : 0.0;
            vec2 g = uv + vec2(shift, 0.0);
            float split = p.z * (hash1(tt) - 0.5) * 2.0;
            c = vec3(tex(g + vec2(split, 0.0)).r, tex(g).g, tex(g - vec2(split, 0.0)).b);
        """
        EffectType.RGB_SPLIT -> """
            vec2 o = su(vec2(p.x * (1.0 + 0.5 * sin(t * 3.0)), 0.0));
            c = vec3(tex(uv + o).r, tex(uv).g, tex(uv - o).b);
        """
        EffectType.VHS -> """
            float band = step(0.97, hash(vec2(floor(uv.y * 60.0), floor(t * 15.0))));
            vec2 v = uv + vec2(sin(uv.y * 40.0 + t * 8.0) * p.y + band * 0.01, 0.0);
            c = vec3(tex(v + vec2(p.z, 0.0)).r, tex(v).g, tex(v - vec2(p.z, 0.0)).b);
            c *= 1.0 - p.x * 0.5 * (1.0 + sin(uv.y / uTexel.y * PI * 0.5));
            c += (hash(uv / uTexel + t) - 0.5) * p.w;
            c = mix(vec3(luma(c)), c, 0.85);
        """
        EffectType.SHAKE -> """
            vec2 s = vec2(sin(t * 23.0) + 0.5 * sin(t * 37.0), cos(t * 19.0) + 0.5 * sin(t * 29.0)) / 1.5;
            c = tex((uv - 0.5) / p.y + 0.5 + su(s * p.x)).rgb;
        """
        EffectType.STROBE -> "c *= 1.0 - step(0.5, fract(t * p.y)) * p.x;"
        EffectType.PIXELATE -> """
            vec2 cell = su(vec2(1.0 / p.x));
            c = tex((floor(uv / cell) + 0.5) * cell).rgb;
        """
        EffectType.LIGHT_LEAK -> """
            vec2 c1 = vec2(0.2 + 0.3 * sin(t * 0.6), 0.3 + 0.2 * cos(t * 0.45));
            vec2 c2 = vec2(0.85 + 0.15 * sin(t * 0.35 + 1.0), 0.75 + 0.2 * sin(t * 0.5));
            vec3 leak = vec3(1.0, 0.45, 0.15) * smoothstep(0.75, 0.0, distance(uv, c1))
              + vec3(1.0, 0.2, 0.45) * smoothstep(0.65, 0.0, distance(uv, c2));
            c = screen(c, clamp(leak * p.x, 0.0, 1.0));
        """
        EffectType.SOFT_GLOW -> """
            vec3 b = blurAt(uv, p.y);
            vec3 bright = max(b - p.z, 0.0) / (1.0 - p.z);
            c = screen(mix(c, b, p.x * 0.25), clamp(bright * p.x, 0.0, 1.0));
        """
        EffectType.BOKEH -> """
            vec3 b = blurAt(uv, p.x);
            vec2 q = uv / su(vec2(0.14));
            vec2 id = floor(q);
            vec2 f = fract(q);
            vec2 ctr = vec2(hash(id), hash(id + 5.3));
            float rr = 0.18 + 0.25 * hash(id + 9.1);
            float disc = smoothstep(rr, rr - 0.06, length(f - ctr)) * step(0.55, hash(id + 2.7));
            float twinkle = 0.6 + 0.4 * sin(t * 1.5 + hash(id) * 6.28);
            c = screen(b, disc * p.y * twinkle * 0.6 * vec3(1.0, 0.92, 0.8));
        """
        EffectType.SPARKLE -> """
            vec2 q = uv / su(vec2(0.09));
            vec2 id = floor(q);
            vec2 f = fract(q) - vec2(hash(id), hash(id + 3.3)) * 0.6 - 0.2;
            float on = step(1.0 - p.x, hash(id + 1.9));
            float tw = max(0.0, sin(t * 4.0 + hash(id + 4.4) * 6.28));
            float core = smoothstep(0.08, 0.0, length(f));
            float rays = smoothstep(0.015, 0.0, abs(f.x)) * smoothstep(0.35, 0.0, abs(f.y))
              + smoothstep(0.015, 0.0, abs(f.y)) * smoothstep(0.35, 0.0, abs(f.x));
            c += (core + rays) * tw * on * p.y * (0.4 + luma(c));
        """
        EffectType.NEON -> """
            vec3 glow = hsv(fract(uv.x * 0.5 + uv.y * 0.3 + t * 0.2)) * clamp(edgeAt(uv) * 3.0, 0.0, 1.0);
            c = c * (1.0 - p.y) + glow * p.x;
        """
        EffectType.ZOOM_LENS -> """
            float z = 1.0 + p.x * (0.5 + 0.5 * sin(t * 2.0 * PI * p.y - PI * 0.5));
            c = tex((uv - 0.5) / z + 0.5).rgb;
        """
        EffectType.FISHEYE -> """
            vec2 q = centred(uv);
            float halfSide = 0.5 * min(uAspect, 1.0);
            float r2 = min(dot(q, q) / (halfSide * halfSide), 1.0);
            c = tex(uncentred(q * (1.0 - p.x * 0.35 * (1.0 - r2)))).rgb;
        """
        EffectType.MIRROR -> "c = tex(vec2(uv.x > 0.5 ? 1.0 - uv.x : uv.x, uv.y)).rgb;"
        EffectType.KALEIDOSCOPE -> """
            vec2 q = centred(uv);
            float seg = 2.0 * PI / p.x;
            float a = mod(atan(q.y, q.x) + t * 2.0 * PI * p.y, seg);
            a = abs(a - seg * 0.5);
            c = tex(uncentred(vec2(cos(a), sin(a)) * length(q))).rgb;
        """
        // Columns, rows, gap.
        EffectType.SPLIT_2, EffectType.SPLIT_3, EffectType.SPLIT_4, EffectType.SPLIT_9 -> """
            vec2 grid = p.xy;
            vec2 cellUv = fract(uv * grid);
            float tileAspect = uAspect * grid.y / grid.x;
            vec2 s = tileAspect < uAspect
              ? vec2(0.5 + (cellUv.x - 0.5) * tileAspect / uAspect, cellUv.y)
              : vec2(cellUv.x, 0.5 + (cellUv.y - 0.5) * uAspect / tileAspect);
            vec2 gap = su(vec2(p.z)) * grid * 0.5;
            float inside = step(gap.x, cellUv.x) * step(cellUv.x, 1.0 - gap.x) * step(gap.y, cellUv.y) * step(cellUv.y, 1.0 - gap.y);
            c = tex(s).rgb * inside;
        """
        EffectType.HEARTBEAT -> """
            float f = fract(t * p.y);
            float thump = max(exp(-f * 14.0), 0.6 * exp(-abs(f - 0.24) * 18.0));
            c = tex((uv - 0.5) / (1.0 + p.x * thump) + 0.5).rgb;
            c *= 1.0 + p.x * thump;
        """
        EffectType.CONFETTI -> """
            // Three layers of tumbling paper, nearer layers bigger and faster.
            vec2 q = vec2(uv.x * uAspect, uv.y);
            for (int k = 0; k < 3; k++) {
              float fk = float(k);
              float scale = 7.0 + fk * 5.0;
              vec2 g = q * scale + vec2(0.0, t * (1.8 + 0.4 * fk));
              g.x += 0.25 * sin(t * 2.0 + floor(g.y) * 1.7 + fk);
              vec2 id = floor(g);
              vec2 f = fract(g) - 0.5 - (vec2(hash(id + fk * 13.0), hash(id + 3.7)) - 0.5) * 0.4;
              float on = step(1.0 - p.x, hash(id + 7.1 + fk));
              float a = t * (2.0 + 4.0 * hash(id + 1.3)) + hash(id + 2.9) * 6.2832;
              vec2 r = vec2(cos(a) * f.x - sin(a) * f.y, sin(a) * f.x + cos(a) * f.y);
              float flutter = 0.03 + 0.07 * abs(sin(t * 5.0 + hash(id + 4.1) * 6.2832));
              float piece = step(abs(r.x), 0.13) * step(abs(r.y), flutter);
              c = mix(c, hsv(hash(id + 5.5)) * 0.9 + 0.1, piece * on);
            }
        """
        EffectType.DISCO -> """
            // Five coloured lights circling over a darkened frame.
            vec2 q = centred(uv);
            vec3 light = vec3(0.0);
            float r = 0.24 * min(uAspect, 1.0);
            for (int k = 0; k < 5; k++) {
              float fk = float(k);
              float a = t * (0.5 + 0.17 * fk) + fk * 1.2566;
              vec2 at = vec2(cos(a) * 0.32 * uAspect, sin(a * 1.31 + fk) * 0.32);
              light += hsv(fract(fk * 0.2 + t * 0.07)) * smoothstep(r, r * 0.2, length(q - at));
            }
            c = c * (1.0 - 0.55 * p.x) + c * light * 1.6 * p.x + light * 0.12 * p.x;
        """
        EffectType.RAINBOW -> """
            float a = 6.2831853 * (t * p.y + (uv.x + uv.y) * 0.5);
            c = mix(c, hueRotate(c, a), p.x);
            c = mix(c, c * (0.6 + 0.8 * hsv(fract(a / 6.2831853))), p.x * 0.35);
        """
        EffectType.INVERT_FLASH -> "c = mix(c, 1.0 - c, mod(floor(t * p.y), 2.0) * p.x);"
        EffectType.LENS_FLARE -> """
            // A light near the top left, a streak through it, and ghosts on the line through the centre.
            vec2 q = centred(uv);
            vec2 l = centred(vec2(0.22 + 0.08 * sin(t * 0.5), 0.78 + 0.05 * cos(t * 0.4)));
            float s = min(uAspect, 1.0);
            vec2 dl = q - l;
            vec3 flare = vec3(1.0, 0.9, 0.75) * 1.3 * exp(-length(dl) / (0.14 * s))
              + vec3(1.0, 0.85, 0.7) * 0.8 * exp(-abs(dl.y) / (0.008 * s)) * exp(-abs(dl.x) / (0.7 * s));
            for (int k = 1; k < 4; k++) {
              float fk = float(k);
              float rr = (0.04 + 0.035 * fk) * s;
              flare += hsv(0.1 + 0.25 * fk) * 0.3 * smoothstep(rr, rr * 0.5, length(q - l * (0.5 - 0.6 * fk)));
            }
            c = screen(c, clamp(flare * p.x, 0.0, 1.0));
        """
        EffectType.DREAMY_GLOW -> """
            vec3 b = blurAt(uv, p.y);
            c = mix(c, b, 0.55 * p.x);
            c = screen(c, b * vec3(1.0, 0.86, 0.95) * 0.65 * p.x * (0.85 + 0.15 * sin(t * 1.5)));
            c = mix(c, c * vec3(1.04, 0.97, 1.06) + 0.03, p.x);
        """
        EffectType.SPOTLIGHT -> """
            vec2 q = centred(uv);
            vec2 at = vec2(0.28 * uAspect * sin(t * 0.7), 0.22 * sin(t * 0.53 + 1.0));
            float r = p.y * min(uAspect, 1.0);
            c *= mix(1.0 - p.x, 1.12, smoothstep(r, r * 0.55, length(q - at)));
        """
        EffectType.CRT -> """
            // A curved screen (black past its edges), scan lines, darker corners and a faint flicker.
            vec2 q = uv - 0.5;
            q *= 1.0 + p.x * dot(q, q) * 2.0;
            vec2 w = q + 0.5;
            float inside = step(0.0, w.x) * step(w.x, 1.0) * step(0.0, w.y) * step(w.y, 1.0);
            c = tex(w).rgb * (1.0 + 0.5 * p.z);
            c *= 1.0 - p.z * (0.5 + 0.5 * sin(w.y * p.y * 6.2831853));
            c *= 1.0 - 0.45 * smoothstep(0.3, 0.75, length(q));
            c *= (0.97 + 0.03 * sin(t * 50.0)) * inside;
        """
        EffectType.TV_STATIC -> """
            float n = hash(floor(uv / uTexel / 2.0) + frameSeed(24.0) * 17.0);
            c = mix(c, vec3(n), p.x);
            c *= 1.0 - p.y * (1.0 - smoothstep(0.0, 0.06, abs(fract(uv.y - t * 0.3) - 0.5)));
            c = mix(vec3(luma(c)), c, 0.8);
        """
        EffectType.CHROMATIC_PULSE -> """
            vec2 o = (uv - 0.5) * p.x * 2.0 * (0.3 + 0.7 * exp(-fract(t * p.y) * 6.0));
            c = vec3(tex(uv + o).r, c.g, tex(uv - o).b);
        """
        EffectType.DUOTONE -> "c = mix(c, mix(vec3(0.13, 0.05, 0.36), vec3(1.0, 0.56, 0.42), smoothstep(0.05, 0.95, luma(c))), p.x);"
        EffectType.HALFTONE -> """
            // Dots on a 45° grid, bigger where the picture is darker, on paper.
            vec2 q = centred(uv);
            float size = min(uAspect, 1.0) / p.x;
            mat2 turn = mat2(0.7071, 0.7071, -0.7071, 0.7071);
            vec2 g = turn * q / size;
            vec2 id = floor(g) + 0.5;
            vec3 ink = tex(uncentred(size * (id * turn))).rgb;
            float r = 0.55 * sqrt(1.0 - luma(ink));
            c = mix(vec3(0.97, 0.95, 0.9), ink * 0.55, smoothstep(r + 0.08, r - 0.08, length(g - id)));
        """
        EffectType.SKETCH -> """
            // Pencil edges on paper, hatched where the picture is dark.
            vec2 q = centred(uv) / min(uAspect, 1.0);
            float edge = clamp(edgeAt(uv) * p.y, 0.0, 1.0);
            float shade = (1.0 - smoothstep(0.2, 0.55, luma(c))) * step(0.55, fract((q.x + q.y) / 0.012)) * 0.35;
            c = mix(c, vec3(0.96, 0.95, 0.92) * (1.0 - edge) * (1.0 - shade), p.x);
        """
        EffectType.THERMAL -> "c = mix(c, thermal(luma(blurAt(uv, 0.006))), p.x);"
        EffectType.NIGHT_VISION -> """
            float l = luma(c) * 1.6 + (hash(uv / uTexel + frameSeed(24.0) * 13.1) - 0.5) * p.y;
            vec3 nv = vec3(0.15, 1.0, 0.25) * clamp(l, 0.0, 1.0);
            nv *= (0.9 + 0.1 * sin(uv.y * 400.0 + t * 20.0)) * smoothstep(0.95, 0.6, d);
            c = mix(c, nv, p.x);
        """
        EffectType.SNOW -> """
            // Three layers of flakes drifting down, nearer ones bigger; the frame cools a little.
            vec2 q = vec2(uv.x * uAspect, uv.y);
            float snow = 0.0;
            for (int k = 0; k < 3; k++) {
              float fk = float(k);
              float scale = 6.0 + fk * 6.0;
              vec2 g = q * scale + vec2(0.4 * sin(t * 0.6 + fk * 2.0), t * (1.2 + 0.5 * fk));
              vec2 id = floor(g);
              vec2 f = fract(g) - 0.5 - (vec2(hash(id + fk), hash(id + fk + 3.1)) - 0.5) * 0.6;
              float r = 0.07 + 0.07 * hash(id + 5.0);
              snow += step(1.0 - p.x, hash(id + 9.0 + fk)) * smoothstep(r, r * 0.3, length(f)) * (1.0 - 0.25 * fk);
            }
            c = mix(c, c * vec3(0.9, 0.96, 1.06) + 0.05, p.y);
            c = screen(c, vec3(clamp(snow, 0.0, 1.0)));
        """
        EffectType.RAIN -> """
            // Two layers of slanting streaks over a darker frame.
            vec2 q = vec2(uv.x * uAspect + uv.y * 0.12, uv.y);
            float rain = 0.0;
            for (int k = 0; k < 2; k++) {
              float fk = float(k);
              vec2 g = q * vec2(36.0 + 24.0 * fk, 2.5 + fk) + vec2(0.0, t * (5.0 + 2.0 * fk));
              vec2 id = floor(g);
              vec2 f = fract(g);
              float y = fract(f.y + hash(id + 3.3));
              float streak = smoothstep(0.1, 0.0, abs(f.x - 0.5)) * smoothstep(0.55, 0.0, y);
              rain += step(1.0 - p.x, hash(id + 7.0 * fk)) * streak * (0.7 - 0.25 * fk);
            }
            c *= 1.0 - p.y;
            c = screen(c, vec3(0.75, 0.8, 0.88) * clamp(rain, 0.0, 1.0) * 0.6);
        """
    }
}
