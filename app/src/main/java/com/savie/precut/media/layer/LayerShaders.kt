package com.savie.precut.media.layer

import com.savie.precut.model.motion.TransitionType

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
     * An overlapping transition (P-06) of [type] from `uA` (outgoing frame) to `uB` (incoming
     * frame) at eased progress `uProgress`; `uAspect` is width / height and `uTexel` one pixel in
     * uv. One program per type, so a shader that one GPU can't compile only loses that transition.
     */
    fun transition(type: TransitionType): String = TRANSITION_HEAD + "void main() {\n  float p = uProgress;\n" + transitionBody(type) + "\n}\n"

    private const val TRANSITION_HEAD = """#version 100
precision highp float;
uniform sampler2D uA;
uniform sampler2D uB;
uniform float uProgress;
uniform float uAspect;
uniform vec2 uTexel;
varying vec2 vUv;
vec4 a(vec2 uv) { return texture2D(uA, uv); }
vec4 b(vec2 uv) { return texture2D(uB, uv); }
// Aspect-corrected offset from the centre, in frame heights.
vec2 centred(vec2 uv) { return vec2((uv.x - 0.5) * uAspect, uv.y - 0.5); }
// A distance in short-side units as uv.
vec2 su(vec2 d) { return uAspect >= 1.0 ? vec2(d.x / uAspect, d.y) : vec2(d.x, d.y * uAspect); }
float hash(vec2 q) { return fract(sin(dot(q, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 q) {
  vec2 i = floor(q);
  vec2 f = fract(q);
  f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
// 16-tap golden-angle spiral blur of a frame; r in short-side units.
vec4 blurA(vec2 uv, float r) {
  vec4 sum = a(uv);
  vec2 dir = vec2(1.0, 0.0);
  for (int i = 0; i < 16; i++) {
    sum += a(uv + su(dir * r * sqrt((float(i) + 0.5) / 16.0)));
    dir = vec2(-0.737366696 * dir.x - 0.675492676 * dir.y, 0.675492676 * dir.x - 0.737366696 * dir.y);
  }
  return sum / 17.0;
}
vec4 blurB(vec2 uv, float r) {
  vec4 sum = b(uv);
  vec2 dir = vec2(1.0, 0.0);
  for (int i = 0; i < 16; i++) {
    sum += b(uv + su(dir * r * sqrt((float(i) + 0.5) / 16.0)));
    dir = vec2(-0.737366696 * dir.x - 0.675492676 * dir.y, 0.675492676 * dir.x - 0.737366696 * dir.y);
  }
  return sum / 17.0;
}
"""

    /** What [transition]'s `main` does after `float p = uProgress;` (uv y up). */
    private fun transitionBody(type: TransitionType): String = when (type) {
        TransitionType.DISSOLVE -> "gl_FragColor = mix(a(vUv), b(vUv), p);"
        TransitionType.SLIDE -> "float edge = 1.0 - p;\n  gl_FragColor = vUv.x >= edge ? b(vec2(vUv.x - edge, vUv.y)) : a(vUv);"
        TransitionType.SLIDE_RIGHT -> "gl_FragColor = vUv.x < p ? b(vec2(vUv.x + 1.0 - p, vUv.y)) : a(vUv);"
        TransitionType.SLIDE_UP -> "gl_FragColor = vUv.y < p ? b(vec2(vUv.x, vUv.y + 1.0 - p)) : a(vUv);"
        TransitionType.SLIDE_DOWN -> "float edge = 1.0 - p;\n  gl_FragColor = vUv.y >= edge ? b(vec2(vUv.x, vUv.y - edge)) : a(vUv);"
        TransitionType.PUSH -> "float edge = 1.0 - p;\n  gl_FragColor = vUv.x >= edge ? b(vec2(vUv.x - edge, vUv.y)) : a(vec2(vUv.x + p, vUv.y));"
        TransitionType.PUSH_RIGHT -> "gl_FragColor = vUv.x < p ? b(vec2(vUv.x + 1.0 - p, vUv.y)) : a(vec2(vUv.x - p, vUv.y));"
        TransitionType.PUSH_UP -> "gl_FragColor = vUv.y < p ? b(vec2(vUv.x, vUv.y + 1.0 - p)) : a(vec2(vUv.x, vUv.y - p));"
        TransitionType.PUSH_DOWN -> "float edge = 1.0 - p;\n  gl_FragColor = vUv.y >= edge ? b(vec2(vUv.x, vUv.y - edge)) : a(vec2(vUv.x, vUv.y + p));"
        TransitionType.WIPE -> """float soft = 0.04;
  float showA = smoothstep(p * (1.0 + soft) - soft, p * (1.0 + soft), vUv.x);
  gl_FragColor = mix(b(vUv), a(vUv), showA);"""
        // An iris opening from the centre until it covers the corners.
        TransitionType.CIRCLE -> """float soft = 0.02;
  float r = p * (0.5 * sqrt(uAspect * uAspect + 1.0) + soft);
  gl_FragColor = mix(b(vUv), a(vUv), smoothstep(r - soft, r, length(centred(vUv))));"""
        // A hand sweeping clockwise from 12 o'clock.
        TransitionType.CLOCK -> """vec2 q = centred(vUv);
  float turn = fract(atan(q.x, q.y) / 6.2831853 + 1.0);
  float soft = 0.01;
  float edge = p * (1.0 + soft);
  gl_FragColor = mix(b(vUv), a(vUv), smoothstep(edge - soft, edge, turn));"""
        // Eight slats, each opening downwards.
        TransitionType.BLINDS -> """float slat = 1.0 - fract(vUv.y * 8.0);
  float edge = p * 1.05;
  gl_FragColor = mix(b(vUv), a(vUv), smoothstep(edge - 0.05, edge, slat));"""
        // The outgoing clip zooms in as the incoming one grows from 70 % over it, its edges feathered.
        TransitionType.ZOOM_DISSOLVE -> """vec4 from = a((vUv - 0.5) / (1.0 + 0.6 * p) + 0.5);
  vec2 inUv = (vUv - 0.5) / (0.7 + 0.3 * p) + 0.5;
  vec2 edge = smoothstep(0.0, 0.08, inUv) * smoothstep(1.0, 0.92, inUv);
  float inside = mix(edge.x * edge.y, 1.0, p * p);
  gl_FragColor = mix(from, b(clamp(inUv, 0.0, 1.0)), p * inside);"""
        // Both blur most at the middle.
        TransitionType.BLUR_DISSOLVE -> """float r = 0.035 * sin(3.14159265 * p);
  gl_FragColor = mix(blurA(vUv, r), blurB(vUv, r), p);"""
        // Cells grow to the middle, the picture changes there, then they shrink.
        TransitionType.PIXELATE -> """float size = 0.08 * sin(3.14159265 * p);
  vec2 cell = max(su(vec2(size)), uTexel);
  vec2 uv = (floor(vUv / cell) + 0.5) * cell;
  gl_FragColor = mix(a(uv), b(uv), smoothstep(0.4, 0.6, p));"""
        // Dark areas of the outgoing clip give way first, in soft blots.
        TransitionType.INK -> """vec4 from = a(vUv);
  float order = mix(1.0 - dot(from.rgb, vec3(0.299, 0.587, 0.114)), noise(centred(vUv) * 7.0), 0.4);
  float edge = p * 1.2 - 0.1;
  gl_FragColor = mix(from, b(vUv), smoothstep(order - 0.1, order + 0.1, edge));"""
        // Edge transitions are drawn on the clips themselves (TransitionShaders) and never come here;
        // a dissolve keeps every uniform the program sets in use.
        TransitionType.FADE_BLACK, TransitionType.FADE_WHITE, TransitionType.FLASH, TransitionType.ZOOM_IN, TransitionType.ZOOM_OUT,
        TransitionType.BLUR, TransitionType.GLITCH, TransitionType.SPIN, TransitionType.SHAKE, TransitionType.RGB_SHIFT,
        TransitionType.FLIP, TransitionType.STRETCH, TransitionType.STROBE,
        -> "gl_FragColor = mix(a(vUv), b(vUv), p);"
    }

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
