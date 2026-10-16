package com.savie.precut.media.effect

/**
 * GLSL for [EffectShaderProgram] (GLES 2): one branch per [com.savie.precut.model.effect.EffectType]
 * (`uType` is its shaderId), reading the four `uParams` that
 * [com.savie.precut.model.effect.EffectParams] documents, animated by `uTime` (seconds at the
 * effect's speed). Distances in short-side units go through `su()`, so effects look the same in
 * portrait and landscape.
 */
internal object EffectShaders {

    const val FRAGMENT = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform float uType;
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

vec3 blurAt(vec2 uv, float r) {
  vec3 sum = tex(uv).rgb;
  for (int i = 0; i < 24; i++) {
    float fi = float(i);
    float a = fi * 2.39996;
    float rr = r * sqrt((fi + 0.5) / 24.0);
    sum += tex(uv + su(vec2(cos(a), sin(a)) * rr)).rgb;
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

void main() {
  vec2 uv = vTexSamplingCoord;
  vec4 src = tex(uv);
  if (uActive < 0.5) { gl_FragColor = src; return; }
  vec3 c = src.rgb;
  vec4 p = uParams;
  float t = uTime;
  float type = uType;
  float d = distance(uv, vec2(0.5)) * 1.414;
  if (type < 1.5) {                     // Blur
    c = blurAt(uv, p.x);
  } else if (type < 2.5) {              // Flash
    c = mix(c, vec3(1.0), exp(-fract(t * p.y) * 7.0) * p.x);
  } else if (type < 3.5) {              // Black-and-white flash
    float beat = floor(t * p.y);
    c = mix(c, vec3(luma(c) * 1.08), mod(beat, 2.0) * p.x);
    c = mix(c, vec3(1.0), exp(-fract(t * p.y) * 10.0) * p.x * 0.8);
  } else if (type < 4.5) {              // Vignette pulse
    float pulse = 0.5 + 0.5 * sin(t * 2.0 * PI * p.y);
    c *= 1.0 - smoothstep(0.3, 1.0, d) * p.x * (0.55 + 0.45 * pulse);
  } else if (type < 5.5) {              // Wobble
    vec2 w = vec2(sin(uv.y * p.y + t * 5.0), cos(uv.x * p.y + t * 4.0)) * p.x;
    c = tex(uv + w).rgb;
  } else if (type < 6.5) {              // Edge
    c = mix(c, vec3(clamp(edgeAt(uv) * 2.0, 0.0, 1.0)), p.x);
  } else if (type < 7.5) {              // Old film
    float frame = floor(t * 24.0);
    c = mix(c, vec3(luma(c)) * vec3(1.07, 0.74, 0.43), p.x);
    c += (hash(uv / uTexel + frame) - 0.5) * p.z;
    float x = hash1(frame + 1.7);
    if (abs(uv.x - x) < 0.0012 && hash1(frame + 3.1) < p.y) c *= 0.55;
    c *= 1.0 - p.w * hash1(frame + 7.3);
    c *= 1.0 - 0.5 * smoothstep(0.45, 1.0, d);
  } else if (type < 8.5) {              // Film grain
    c += (hash(uv / uTexel + floor(t * 30.0) * 13.1) - 0.5) * p.x;
  } else if (type < 9.5) {              // Posterize
    c = floor(c * p.x + 0.5) / p.x;
  } else if (type < 10.5) {             // Glitch
    float tt = floor(t * 12.0);
    float row = floor(uv.y * 24.0);
    float shift = hash(vec2(row, tt)) < p.x ? (hash(vec2(row + 7.0, tt)) - 0.5) * 2.0 * p.y : 0.0;
    vec2 g = uv + vec2(shift, 0.0);
    float split = p.z * (hash1(tt) - 0.5) * 2.0;
    c = vec3(tex(g + vec2(split, 0.0)).r, tex(g).g, tex(g - vec2(split, 0.0)).b);
  } else if (type < 11.5) {             // RGB split
    vec2 o = su(vec2(p.x * (1.0 + 0.5 * sin(t * 3.0)), 0.0));
    c = vec3(tex(uv + o).r, tex(uv).g, tex(uv - o).b);
  } else if (type < 12.5) {             // VHS
    float band = step(0.97, hash(vec2(floor(uv.y * 60.0), floor(t * 15.0))));
    vec2 v = uv + vec2(sin(uv.y * 40.0 + t * 8.0) * p.y + band * 0.01, 0.0);
    c = vec3(tex(v + vec2(p.z, 0.0)).r, tex(v).g, tex(v - vec2(p.z, 0.0)).b);
    c *= 1.0 - p.x * 0.5 * (1.0 + sin(uv.y / uTexel.y * PI * 0.5));
    c += (hash(uv / uTexel + t) - 0.5) * p.w;
    c = mix(vec3(luma(c)), c, 0.85);
  } else if (type < 13.5) {             // Shake
    vec2 s = vec2(sin(t * 23.0) + 0.5 * sin(t * 37.0), cos(t * 19.0) + 0.5 * sin(t * 29.0)) / 1.5;
    c = tex((uv - 0.5) / p.y + 0.5 + su(s * p.x)).rgb;
  } else if (type < 14.5) {             // Strobe
    c *= 1.0 - step(0.5, fract(t * p.y)) * p.x;
  } else if (type < 15.5) {             // Pixelate
    vec2 cell = su(vec2(1.0 / p.x));
    c = tex((floor(uv / cell) + 0.5) * cell).rgb;
  } else if (type < 16.5) {             // Light leak
    vec2 c1 = vec2(0.2 + 0.3 * sin(t * 0.6), 0.3 + 0.2 * cos(t * 0.45));
    vec2 c2 = vec2(0.85 + 0.15 * sin(t * 0.35 + 1.0), 0.75 + 0.2 * sin(t * 0.5));
    vec3 leak = vec3(1.0, 0.45, 0.15) * smoothstep(0.75, 0.0, distance(uv, c1))
      + vec3(1.0, 0.2, 0.45) * smoothstep(0.65, 0.0, distance(uv, c2));
    c = screen(c, clamp(leak * p.x, 0.0, 1.0));
  } else if (type < 17.5) {             // Soft glow
    vec3 b = blurAt(uv, p.y);
    vec3 bright = max(b - p.z, 0.0) / (1.0 - p.z);
    c = screen(mix(c, b, p.x * 0.25), clamp(bright * p.x, 0.0, 1.0));
  } else if (type < 18.5) {             // Bokeh
    vec3 b = blurAt(uv, p.x);
    vec2 q = uv / su(vec2(0.14));
    vec2 id = floor(q);
    vec2 f = fract(q);
    vec2 ctr = vec2(hash(id), hash(id + 5.3));
    float rr = 0.18 + 0.25 * hash(id + 9.1);
    float disc = smoothstep(rr, rr - 0.06, length(f - ctr)) * step(0.55, hash(id + 2.7));
    float twinkle = 0.6 + 0.4 * sin(t * 1.5 + hash(id) * 6.28);
    c = screen(b, disc * p.y * twinkle * 0.6 * vec3(1.0, 0.92, 0.8));
  } else if (type < 19.5) {             // Sparkle
    vec2 q = uv / su(vec2(0.09));
    vec2 id = floor(q);
    vec2 f = fract(q) - vec2(hash(id), hash(id + 3.3)) * 0.6 - 0.2;
    float on = step(1.0 - p.x, hash(id + 1.9));
    float tw = max(0.0, sin(t * 4.0 + hash(id + 4.4) * 6.28));
    float core = smoothstep(0.08, 0.0, length(f));
    float rays = smoothstep(0.015, 0.0, abs(f.x)) * smoothstep(0.35, 0.0, abs(f.y))
      + smoothstep(0.015, 0.0, abs(f.y)) * smoothstep(0.35, 0.0, abs(f.x));
    c += (core + rays) * tw * on * p.y * (0.4 + luma(c));
  } else if (type < 20.5) {             // Neon
    vec3 glow = hsv(fract(uv.x * 0.5 + uv.y * 0.3 + t * 0.2)) * clamp(edgeAt(uv) * 3.0, 0.0, 1.0);
    c = c * (1.0 - p.y) + glow * p.x;
  } else if (type < 21.5) {             // Zoom lens
    float z = 1.0 + p.x * (0.5 + 0.5 * sin(t * 2.0 * PI * p.y - PI * 0.5));
    c = tex((uv - 0.5) / z + 0.5).rgb;
  } else if (type < 22.5) {             // Fisheye
    vec2 q = centred(uv);
    float halfSide = 0.5 * min(uAspect, 1.0);
    float r2 = min(dot(q, q) / (halfSide * halfSide), 1.0);
    c = tex(uncentred(q * (1.0 - p.x * 0.35 * (1.0 - r2)))).rgb;
  } else if (type < 23.5) {             // Mirror
    c = tex(vec2(uv.x > 0.5 ? 1.0 - uv.x : uv.x, uv.y)).rgb;
  } else if (type < 24.5) {             // Kaleidoscope
    vec2 q = centred(uv);
    float seg = 2.0 * PI / p.x;
    float a = mod(atan(q.y, q.x) + t * 2.0 * PI * p.y, seg);
    a = abs(a - seg * 0.5);
    c = tex(uncentred(vec2(cos(a), sin(a)) * length(q))).rgb;
  } else {                              // Split screen: columns, rows, gap
    vec2 grid = p.xy;
    vec2 cellUv = fract(uv * grid);
    float tileAspect = uAspect * grid.y / grid.x;
    vec2 s = tileAspect < uAspect
      ? vec2(0.5 + (cellUv.x - 0.5) * tileAspect / uAspect, cellUv.y)
      : vec2(cellUv.x, 0.5 + (cellUv.y - 0.5) * uAspect / tileAspect);
    vec2 gap = su(vec2(p.z)) * grid * 0.5;
    float inside = step(gap.x, cellUv.x) * step(cellUv.x, 1.0 - gap.x) * step(gap.y, cellUv.y) * step(cellUv.y, 1.0 - gap.y);
    c = tex(s).rgb * inside;
  }
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), src.a);
}
"""
}
