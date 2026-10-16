package com.savie.precut.media.frame

/** GLSL for [CanvasShaderProgram] (GLES 2, SDR). */
internal object CanvasShaders {

    /** Media3's vertex_shader_transformation_es2.glsl. */
    const val VERTEX = """#version 100
attribute vec4 aFramePosition;
uniform mat4 uTransformationMatrix;
uniform mat4 uTexTransformationMatrix;
varying vec2 vTexSamplingCoord;
void main() {
  gl_Position = uTransformationMatrix * aFramePosition;
  vec4 texturePosition = vec4(aFramePosition.x * 0.5 + 0.5, aFramePosition.y * 0.5 + 0.5, 0.0, 1.0);
  vTexSamplingCoord = (uTexTransformationMatrix * texturePosition).xy;
}
"""

    /** Samples a texture filling the target ([uFillScale] crops the overflowing axis). */
    const val FILL = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uFillScale;
varying vec2 vTexSamplingCoord;
void main() {
  gl_FragColor = texture2D(uTexSampler, (vTexSamplingCoord - 0.5) * uFillScale + 0.5);
}
"""

    /** One direction of a 25-tap Gaussian; [uStep] is the tap spacing in texture units. */
    const val BLUR = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec2 uStep;
varying vec2 vTexSamplingCoord;
void main() {
  vec4 sum = vec4(0.0);
  float total = 0.0;
  for (int i = -12; i <= 12; i++) {
    float x = float(i);
    float w = exp(-x * x / 32.0); // sigma = 4 taps
    sum += texture2D(uTexSampler, vTexSamplingCoord + uStep * x) * w;
    total += w;
  }
  gl_FragColor = sum / total;
}
"""

    /**
     * The clip placed in the frame (centre, half size and clockwise turn in output pixels, y up),
     * with its opacity, over the background: a colour or a texture filling the frame.
     */
    const val COMPOSE = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uBackground;
uniform vec4 uColor;       // rgb, and a = 1 when the background is a texture
uniform vec2 uBackgroundScale; // fills the frame with the texture; y < 0 flips a bitmap upright
uniform vec2 uOutSize;
uniform vec2 uCenter;
uniform vec2 uHalfSize;
uniform vec2 uCosSin;
uniform float uOpacity;
uniform float uTransparent; // 1: an overlay, written with straight alpha over nothing
uniform vec4 uMaskA;       // shape (0 none, 1 linear, 2 mirror, 3 circle, 4 rectangle, 5 heart, 6 star), feather px, corner radius 0..1, inverted
uniform vec4 uMaskB;       // centre from the picture's centre and half size, in pixels (y up)
uniform vec2 uMaskCosSin;  // the mask's clockwise turn
varying vec2 vTexSamplingCoord;

float dot2(vec2 v) { return dot(v, v); }

// Heart with its tip at the origin, lobes up to y ~ 1.1 (Inigo Quilez, MIT).
float sdHeart(vec2 p) {
  p.x = abs(p.x);
  if (p.y + p.x > 1.0) return sqrt(dot2(p - vec2(0.25, 0.75))) - sqrt(2.0) / 4.0;
  return sqrt(min(dot2(p - vec2(0.0, 1.0)), dot2(p - 0.5 * max(p.x + p.y, 0.0)))) * sign(p.x - p.y);
}

// Five-pointed star of outer radius r, inner ratio rf (Inigo Quilez, MIT).
float sdStar5(vec2 p, float r, float rf) {
  vec2 k1 = vec2(0.809016994375, -0.587785252292);
  vec2 k2 = vec2(-k1.x, k1.y);
  p.x = abs(p.x);
  p -= 2.0 * max(dot(k1, p), 0.0) * k1;
  p -= 2.0 * max(dot(k2, p), 0.0) * k2;
  p.x = abs(p.x);
  p.y -= r;
  vec2 ba = rf * vec2(-k1.y, k1.x) - vec2(0.0, 1.0);
  float h = clamp(dot(p, ba) / dot(ba, ba), 0.0, r);
  return length(p - ba * h) * sign(p.y * ba.x - p.x * ba.y);
}

// How much of the picture shows at [local] (pixels from its centre, y up).
float maskAlpha(vec2 local) {
  float shape = uMaskA.x;
  if (shape < 0.5) return 1.0;
  vec2 m = local - uMaskB.xy;
  vec2 q = vec2(uMaskCosSin.x * m.x - uMaskCosSin.y * m.y, uMaskCosSin.y * m.x + uMaskCosSin.x * m.y);
  vec2 b = max(uMaskB.zw, vec2(1.0));
  float side = min(b.x, b.y);
  float d;
  if (shape < 1.5) {
    d = -q.y;
  } else if (shape < 2.5) {
    d = abs(q.y) - b.y;
  } else if (shape < 3.5) {
    d = (length(q / b) - 1.0) * side;
  } else if (shape < 4.5) {
    float r = uMaskA.z * side;
    vec2 e = abs(q) - b + r;
    d = length(max(e, 0.0)) + min(max(e.x, e.y), 0.0) - r;
  } else if (shape < 5.5) {
    vec2 n = q / b;
    d = sdHeart(vec2(n.x * 0.604, (n.y + 1.0) * 0.552)) * side / 0.6;
  } else {
    d = sdStar5(q / b, 1.0, 0.45) * side;
  }
  float f = max(uMaskA.y, 0.75);
  float a = 1.0 - smoothstep(-f, f, d);
  return uMaskA.w > 0.5 ? 1.0 - a : a;
}

void main() {
  vec3 background = uColor.a > 0.5 ? texture2D(uBackground, (vTexSamplingCoord - 0.5) * uBackgroundScale + 0.5).rgb : uColor.rgb;
  vec2 p = vTexSamplingCoord * uOutSize - uCenter;
  // Undo the clockwise turn (counter-clockwise in y-up space).
  vec2 local = vec2(uCosSin.x * p.x - uCosSin.y * p.y, uCosSin.y * p.x + uCosSin.x * p.y);
  vec2 uv = local / (2.0 * uHalfSize) + 0.5;
  // Distance inside the picture's edge in pixels, for a 1 px anti-aliased border.
  vec2 inside = (0.5 - abs(uv - 0.5)) * 2.0 * uHalfSize;
  float coverage = clamp(min(inside.x, inside.y) + 0.5, 0.0, 1.0);
  vec4 clip = texture2D(uTexSampler, clamp(uv, 0.0, 1.0));
  float alpha = clip.a * coverage * uOpacity * maskAlpha(local);
  gl_FragColor = uTransparent > 0.5 ? vec4(clip.rgb, alpha) : vec4(mix(background, clip.rgb, alpha), 1.0);
}
"""
}
