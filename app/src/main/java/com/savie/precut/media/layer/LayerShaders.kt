package com.savie.precut.media.layer

/**
 * GLSL for [OverlayLayersShaderProgram] (GLES 2, SDR). Every pass draws a full-frame quad and maps
 * its output coordinate (y up) to a source coordinate with an affine map: `uMap.xy` and `uMap.zw`
 * are the columns of a 2×2 matrix, `uOffset` the translation.
 */
internal object LayerShaders {

    const val VERTEX = """#version 100
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""

    /**
     * A decoded frame from its Y, U and V planes, turned upright, to RGB: [uRange] is the luma
     * offset and scale and the chroma scale; [uCoefficients] are Cr→R, Cb→G, Cr→G and Cb→B.
     */
    const val COPY_YUV = """#version 100
precision mediump float;
uniform sampler2D uY;
uniform sampler2D uU;
uniform sampler2D uV;
uniform vec4 uMap;
uniform vec2 uOffset;
uniform vec3 uRange;
uniform vec4 uCoefficients;
varying vec2 vUv;
void main() {
  vec2 p = mat2(uMap.xy, uMap.zw) * vUv + uOffset;
  float y = (texture2D(uY, p).r - uRange.x) * uRange.y;
  float cb = (texture2D(uU, p).r - 0.5) * uRange.z;
  float cr = (texture2D(uV, p).r - 0.5) * uRange.z;
  vec3 rgb = vec3(y + uCoefficients.x * cr, y - uCoefficients.y * cb - uCoefficients.z * cr, y + uCoefficients.w * cb);
  gl_FragColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);
}
"""

    /**
     * An overlapping transition (P-06) from [uA] (outgoing frame) to [uB] (incoming frame) at
     * eased progress [uProgress]; [uType] 0 dissolve, 1 slide in from the right, 2 push, 3 wipe.
     */
    const val TRANSITION = """#version 100
precision mediump float;
uniform sampler2D uA;
uniform sampler2D uB;
uniform float uProgress;
uniform float uType;
varying vec2 vUv;
void main() {
  float p = uProgress;
  if (uType < 0.5) {
    gl_FragColor = mix(texture2D(uA, vUv), texture2D(uB, vUv), p);
  } else if (uType < 1.5) {
    float edge = 1.0 - p;
    gl_FragColor = vUv.x >= edge ? texture2D(uB, vec2(vUv.x - edge, vUv.y)) : texture2D(uA, vUv);
  } else if (uType < 2.5) {
    float edge = 1.0 - p;
    gl_FragColor = vUv.x >= edge ? texture2D(uB, vec2(vUv.x - edge, vUv.y)) : texture2D(uA, vec2(vUv.x + p, vUv.y));
  } else {
    float soft = 0.04;
    float showA = smoothstep(p * (1.0 + soft) - soft, p * (1.0 + soft), vUv.x);
    gl_FragColor = mix(texture2D(uB, vUv), texture2D(uA, vUv), showA);
  }
}
"""

    /** A 2D texture through the affine map (bitmaps upright, mirror/turn/crop, plain copies). */
    const val COPY_2D = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec4 uMap;
uniform vec2 uOffset;
varying vec2 vUv;
void main() {
  gl_FragColor = texture2D(uTexSampler, mat2(uMap.xy, uMap.zw) * vUv + uOffset);
}
"""

    /** [COPY_2D] with straight alpha turned premultiplied (so blending and averaging don't darken edges). */
    const val PREMULTIPLY = """#version 100
precision mediump float;
uniform sampler2D uTexSampler;
uniform vec4 uMap;
uniform vec2 uOffset;
varying vec2 vUv;
void main() {
  vec4 c = texture2D(uTexSampler, mat2(uMap.xy, uMap.zw) * vUv + uOffset);
  gl_FragColor = vec4(c.rgb * c.a, c.a);
}
"""

    /**
     * A premultiplied layer [uLayer] over the frame so far [uBackdrop] with blend mode [uMode]
     * (`BlendMode.id`; the same formulas as `BlendMode.mix`), then mixed with the backdrop by the
     * layer's alpha.
     */
    const val BLEND = """#version 100
precision mediump float;
uniform sampler2D uBackdrop;
uniform sampler2D uLayer;
uniform float uMode;
varying vec2 vUv;
// "Where x > edge" is written 1.0 - step(x, edge).
vec3 screen(vec3 b, vec3 s) { return b + s - b * s; }
vec3 hardLight(vec3 b, vec3 s) {
  return mix(b * 2.0 * s, screen(b, 2.0 * s - 1.0), 1.0 - step(s, vec3(0.5)));
}
vec3 softLight(vec3 b, vec3 s) {
  vec3 d = mix(((16.0 * b - 12.0) * b + 4.0) * b, sqrt(b), 1.0 - step(b, vec3(0.25)));
  vec3 dark = b - (1.0 - 2.0 * s) * b * (1.0 - b);
  vec3 light = b + (2.0 * s - 1.0) * (d - b);
  return mix(dark, light, 1.0 - step(s, vec3(0.5)));
}
vec3 blend(vec3 b, vec3 s) {
  if (uMode < 0.5) return s;
  if (uMode < 1.5) return b * s;
  if (uMode < 2.5) return screen(b, s);
  if (uMode < 3.5) return hardLight(s, b);
  if (uMode < 4.5) return min(b, s);
  if (uMode < 5.5) return max(b, s);
  if (uMode < 6.5) return min(vec3(1.0), b + s);
  if (uMode < 7.5) return softLight(b, s);
  return abs(b - s);
}
void main() {
  vec4 backdrop = texture2D(uBackdrop, vUv);
  vec4 layer = texture2D(uLayer, vUv);
  if (layer.a <= 0.0) { gl_FragColor = backdrop; return; }
  vec3 s = clamp(layer.rgb / layer.a, 0.0, 1.0);
  gl_FragColor = vec4(mix(backdrop.rgb, blend(backdrop.rgb, s), layer.a), layer.a + backdrop.a * (1.0 - layer.a));
}
"""
}
