package com.savie.precut.media.look

/** GLSL for [LookShaderProgram] (GLES 2, SDR in the source's gamma-encoded colour space). */
internal object LookShaders {

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

    /**
     * One pass: sharpen → LUT (mixed by intensity) → exposure, brightness, contrast →
     * temperature/tint → shadows/highlights → hue, HSL bands, saturation → curves → fade →
     * vignette → grain. Uniforms packed as in [LookUniforms].
     */
    const val FRAGMENT = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform sampler2D uLut;
uniform sampler2D uCurves;
uniform vec4 uAdjustA;   // exposure stops, brightness, contrast, saturation
uniform vec4 uAdjustB;   // temperature, tint, hue degrees, highlights
uniform vec4 uAdjustC;   // shadows, fade, vignette, grain
uniform vec4 uMisc;      // sharpen, lut mix, lut size, grain seed
uniform vec4 uFlags;     // enabled, hsl enabled, curves enabled, aspect (w / h)
uniform vec2 uTexelSize;
uniform vec4 uHslHueA;
uniform vec4 uHslHueB;
uniform vec4 uHslSatA;
uniform vec4 uHslSatB;
uniform vec4 uHslLightA;
uniform vec4 uHslLightB;
varying vec2 vTexSamplingCoord;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
const vec4 CENTRES_A = vec4(0.0, 30.0, 60.0, 120.0);
const vec4 CENTRES_B = vec4(180.0, 240.0, 270.0, 310.0);

vec3 lut(vec3 c) {
  float n = uMisc.z;
  float b = c.b * (n - 1.0);
  float b0 = floor(b);
  float b1 = min(b0 + 1.0, n - 1.0);
  float x = (c.r * (n - 1.0) + 0.5) / (n * n);
  float y = (c.g * (n - 1.0) + 0.5) / n;
  vec3 s0 = texture2D(uLut, vec2(x + b0 / n, y)).rgb;
  vec3 s1 = texture2D(uLut, vec2(x + b1 / n, y)).rgb;
  return mix(s0, s1, b - b0);
}

vec3 rgbToHsl(vec3 c) {
  float maxC = max(c.r, max(c.g, c.b));
  float minC = min(c.r, min(c.g, c.b));
  float l = (maxC + minC) * 0.5;
  float d = maxC - minC;
  if (d < 1e-5) return vec3(0.0, 0.0, l);
  float s = d / (1.0 - abs(2.0 * l - 1.0));
  float h;
  if (maxC == c.r) h = mod((c.g - c.b) / d, 6.0);
  else if (maxC == c.g) h = (c.b - c.r) / d + 2.0;
  else h = (c.r - c.g) / d + 4.0;
  return vec3(h * 60.0, clamp(s, 0.0, 1.0), l);
}

vec3 hslToRgb(vec3 hsl) {
  float c = (1.0 - abs(2.0 * hsl.z - 1.0)) * hsl.y;
  float hp = mod(hsl.x, 360.0) / 60.0;
  float x = c * (1.0 - abs(mod(hp, 2.0) - 1.0));
  vec3 rgb;
  if (hp < 1.0) rgb = vec3(c, x, 0.0);
  else if (hp < 2.0) rgb = vec3(x, c, 0.0);
  else if (hp < 3.0) rgb = vec3(0.0, c, x);
  else if (hp < 4.0) rgb = vec3(0.0, x, c);
  else if (hp < 5.0) rgb = vec3(x, 0.0, c);
  else rgb = vec3(c, 0.0, x);
  return rgb + (hsl.z - c * 0.5);
}

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 curves(vec3 c) {
  vec3 x = c * (255.0 / 256.0) + 0.5 / 256.0;
  return vec3(
      texture2D(uCurves, vec2(x.r, 0.5)).r,
      texture2D(uCurves, vec2(x.g, 0.5)).g,
      texture2D(uCurves, vec2(x.b, 0.5)).b);
}

void main() {
  vec4 src = texture2D(uTexSampler, vTexSamplingCoord);
  if (uFlags.x < 0.5) {
    gl_FragColor = src;
    return;
  }
  vec3 c = src.rgb;
  if (uMisc.x > 0.0) {
    vec3 around = texture2D(uTexSampler, vTexSamplingCoord + vec2(uTexelSize.x, 0.0)).rgb
        + texture2D(uTexSampler, vTexSamplingCoord - vec2(uTexelSize.x, 0.0)).rgb
        + texture2D(uTexSampler, vTexSamplingCoord + vec2(0.0, uTexelSize.y)).rgb
        + texture2D(uTexSampler, vTexSamplingCoord - vec2(0.0, uTexelSize.y)).rgb;
    c = clamp(c + (c - around * 0.25) * uMisc.x * 2.0, 0.0, 1.0);
  }
  if (uMisc.y > 0.0) c = mix(c, lut(c), uMisc.y);
  c *= exp2(uAdjustA.x);
  c += uAdjustA.y * 0.25;
  float k = uAdjustA.z * 0.5;
  c = (c - 0.5) * ((1.0 + k) / (1.0001 - k)) + 0.5;
  c *= vec3(1.0 + uAdjustB.x * 0.12, 1.0 - uAdjustB.y * 0.1, 1.0 - uAdjustB.x * 0.12);
  float y = dot(clamp(c, 0.0, 1.0), LUMA);
  c += uAdjustC.x * 0.3 * (1.0 - smoothstep(0.0, 0.55, y));
  c += uAdjustB.w * 0.3 * smoothstep(0.45, 1.0, y);
  c = clamp(c, 0.0, 1.0);
  if (uAdjustA.w != 0.0 || uAdjustB.z != 0.0 || uFlags.y > 0.5) {
    vec3 hsl = rgbToHsl(c);
    hsl.x = mod(hsl.x + uAdjustB.z, 360.0);
    if (uFlags.y > 0.5) {
      vec4 wA = max(1.0 - abs(mod(vec4(hsl.x) - CENTRES_A + 180.0, 360.0) - 180.0) / 45.0, 0.0);
      vec4 wB = max(1.0 - abs(mod(vec4(hsl.x) - CENTRES_B + 180.0, 360.0) - 180.0) / 45.0, 0.0);
      float colourful = smoothstep(0.02, 0.2, hsl.y);
      hsl.x = mod(hsl.x + (dot(wA, uHslHueA) + dot(wB, uHslHueB)) * colourful, 360.0);
      hsl.y = clamp(hsl.y * (1.0 + (dot(wA, uHslSatA) + dot(wB, uHslSatB)) * colourful), 0.0, 1.0);
      hsl.z = clamp(hsl.z + (dot(wA, uHslLightA) + dot(wB, uHslLightB)) * colourful * 0.25, 0.0, 1.0);
    }
    hsl.y = clamp(hsl.y * (1.0 + uAdjustA.w), 0.0, 1.0);
    c = hslToRgb(hsl);
  }
  if (uFlags.z > 0.5) c = curves(c);
  c = mix(c, c * 0.7 + 0.15, uAdjustC.y);
  if (uAdjustC.z > 0.0) {
    vec2 d = (vTexSamplingCoord - 0.5) * vec2(uFlags.w, 1.0);
    float corner = 0.5 * sqrt(uFlags.w * uFlags.w + 1.0);
    c *= 1.0 - uAdjustC.z * 0.85 * smoothstep(0.35, 1.0, length(d) / corner);
  }
  if (uAdjustC.w > 0.0) c += (hash(vTexSamplingCoord / uTexelSize + uMisc.w) - 0.5) * uAdjustC.w * 0.2;
  gl_FragColor = vec4(clamp(c, 0.0, 1.0), src.a);
}
"""
}
