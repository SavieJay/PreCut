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
varying vec2 vTexSamplingCoord;
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
  float alpha = clip.a * coverage * uOpacity;
  gl_FragColor = uTransparent > 0.5 ? vec4(clip.rgb, alpha) : vec4(mix(background, clip.rgb, alpha), 1.0);
}
"""
}
