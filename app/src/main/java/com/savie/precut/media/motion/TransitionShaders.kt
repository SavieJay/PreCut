package com.savie.precut.media.motion

/** GLSL for [TransitionShaderProgram] (GLES 2, SDR). */
internal object TransitionShaders {

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
     * The whole frame scaled and turned about its centre (black outside), blurred (a 24-tap
     * spiral), glitched (shifted slices and split colour channels), then mixed towards black or
     * white and flashed.
     */
    const val FRAGMENT = """#version 100
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uSize;        // frame size in pixels
uniform vec2 uScaleTurn;   // scale, clockwise radians
uniform vec4 uAmounts;     // colour mix, mix to white (0/1), flash, blur
uniform vec2 uGlitch;      // strength, seed
varying vec2 vTexSamplingCoord;

float hash(float n) { return fract(sin(n) * 43758.5453); }

vec4 sampleFrame(vec2 uv) {
  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) return vec4(0.0, 0.0, 0.0, 1.0);
  return texture2D(uTexSampler, uv);
}

vec4 blurred(vec2 uv, float radius) {
  if (radius <= 0.0) return sampleFrame(uv);
  vec4 sum = sampleFrame(uv);
  for (int i = 1; i < 24; i++) {
    float f = float(i);
    float r = radius * sqrt(f / 24.0);
    float a = f * 2.39996; // golden angle
    sum += sampleFrame(uv + vec2(cos(a), sin(a)) * r / uSize);
  }
  return sum / 24.0;
}

void main() {
  // Undo the scale and the clockwise turn (counter-clockwise in y-up space) about the centre.
  vec2 p = (vTexSamplingCoord - 0.5) * uSize / uScaleTurn.x;
  float c = cos(uScaleTurn.y);
  float s = sin(uScaleTurn.y);
  vec2 uv = vec2(c * p.x - s * p.y, s * p.x + c * p.y) / uSize + 0.5;

  float glitch = uGlitch.x;
  if (glitch > 0.0) {
    float slice = floor(uv.y * 24.0);
    float n = hash(slice + uGlitch.y);
    if (n > 1.0 - glitch * 0.7) uv.x += (hash(slice * 7.0 + uGlitch.y) - 0.5) * 0.15 * glitch;
  }
  float radius = uAmounts.w * 0.04 * min(uSize.x, uSize.y);
  vec4 color = blurred(uv, radius);
  if (glitch > 0.0) {
    vec2 split = vec2(0.012 * glitch, 0.0);
    color.r = blurred(uv + split, radius).r;
    color.b = blurred(uv - split, radius).b;
  }
  vec3 target = mix(vec3(0.0), vec3(1.0), uAmounts.y);
  vec3 rgb = mix(color.rgb, target, uAmounts.x);
  rgb = mix(rgb, vec3(1.0), uAmounts.z);
  gl_FragColor = vec4(rgb, 1.0);
}
"""
}
