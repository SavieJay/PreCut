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
}
