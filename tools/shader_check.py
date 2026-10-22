#!/usr/bin/env python3
"""Compiles and renders every effect and transition shader on this computer's GPU (Mesa, headless).

ShaderSourcesTest writes the shaders and the values the app feeds them to app/build/shaders; this
compiles each program the way the app does (GLES 2, `#version 100`), reports any that fail, and
renders a contact sheet of test scenes through them: every effect at its default intensity, every
overlapping transition at 25/50/75 %, every edge transition at 20/40/50/60/80 %.

Usage: ./gradlew testDebugUnitTest --tests '*ShaderSourcesTest' && python3 -I tools/shader_check.py
Needs Mesa's libEGL and libGLESv2 (no headers) and Pillow. Writes build/shader_check/*.png.
"""
import ctypes as C
import json
import math
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
SHADERS = os.path.join(ROOT, "app", "build", "shaders")
OUT = os.path.join(ROOT, "build", "shader_check")
W, H = 216, 384  # 9:16, like a phone video
EFFECT_SECONDS = 0.9

# GL / EGL constants.
GL_FRAGMENT_SHADER, GL_VERTEX_SHADER = 0x8B30, 0x8B31
GL_COMPILE_STATUS, GL_LINK_STATUS = 0x8B81, 0x8B82
GL_TEXTURE_2D, GL_RGBA, GL_UNSIGNED_BYTE, GL_FLOAT = 0x0DE1, 0x1908, 0x1401, 0x1406
GL_TEXTURE_MIN_FILTER, GL_TEXTURE_MAG_FILTER, GL_LINEAR = 0x2801, 0x2800, 0x2601
GL_TEXTURE_WRAP_S, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE = 0x2802, 0x2803, 0x812F
GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_FRAMEBUFFER_COMPLETE = 0x8D40, 0x8CE0, 0x8CD5
GL_TEXTURE0, GL_TRIANGLE_STRIP, GL_ARRAY_BUFFER, GL_STATIC_DRAW = 0x84C0, 0x0005, 0x8892, 0x88E4
GL_RENDERER = 0x1F01


def gl_context():
    egl = C.CDLL("libEGL.so.1")
    gl = C.CDLL("libGLESv2.so.2")
    egl.eglGetProcAddress.restype = C.c_void_p
    egl.eglGetProcAddress.argtypes = [C.c_char_p]
    get_display = C.CFUNCTYPE(C.c_void_p, C.c_uint, C.c_void_p, C.c_void_p)(egl.eglGetProcAddress(b"eglGetPlatformDisplayEXT"))
    display = C.c_void_p(get_display(0x31DD, None, None))  # EGL_PLATFORM_SURFACELESS_MESA
    egl.eglInitialize.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p]
    if not egl.eglInitialize(display, None, None):
        sys.exit("No EGL display (is Mesa installed?)")
    egl.eglBindAPI(0x30A0)  # EGL_OPENGL_ES_API
    egl.eglCreateContext.restype = C.c_void_p
    egl.eglCreateContext.argtypes = [C.c_void_p, C.c_void_p, C.c_void_p, C.c_void_p]
    context = egl.eglCreateContext(display, None, None, (C.c_int * 3)(0x3098, 2, 0x3038))  # client version 2
    egl.eglMakeCurrent.argtypes = [C.c_void_p] * 4
    if not context or not egl.eglMakeCurrent(display, None, None, C.c_void_p(context)):
        sys.exit("Couldn't make a GLES 2 context current")
    gl.glGetString.restype = C.c_char_p
    gl.glCreateShader.restype = C.c_uint
    gl.glCreateProgram.restype = C.c_uint
    gl.glGetUniformLocation.restype = C.c_int
    gl.glGetUniformLocation.argtypes = [C.c_uint, C.c_char_p]
    gl.glGetAttribLocation.restype = C.c_int
    gl.glGetAttribLocation.argtypes = [C.c_uint, C.c_char_p]
    gl.glCheckFramebufferStatus.restype = C.c_uint
    return gl


class Gpu:
    def __init__(self):
        self.gl = gl_context()
        gl = self.gl
        self.renderer = gl.glGetString(GL_RENDERER).decode()
        quad = (C.c_float * 16)(-1, -1, 0, 1, 1, -1, 0, 1, -1, 1, 0, 1, 1, 1, 0, 1)
        self.vbo = C.c_uint()
        gl.glGenBuffers(1, C.byref(self.vbo))
        gl.glBindBuffer(GL_ARRAY_BUFFER, self.vbo)
        gl.glBufferData(GL_ARRAY_BUFFER, C.sizeof(quad), quad, GL_STATIC_DRAW)
        self.target = self.texture(None, W, H)
        self.fbo = C.c_uint()
        gl.glGenFramebuffers(1, C.byref(self.fbo))
        gl.glBindFramebuffer(GL_FRAMEBUFFER, self.fbo)
        gl.glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, self.target, 0)
        assert gl.glCheckFramebufferStatus(GL_FRAMEBUFFER) == GL_FRAMEBUFFER_COMPLETE

    def texture(self, image, w, h):
        gl = self.gl
        tex = C.c_uint()
        gl.glGenTextures(1, C.byref(tex))
        gl.glBindTexture(GL_TEXTURE_2D, tex)
        for name, value in ((GL_TEXTURE_MIN_FILTER, GL_LINEAR), (GL_TEXTURE_MAG_FILTER, GL_LINEAR),
                            (GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE), (GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)):
            gl.glTexParameteri(GL_TEXTURE_2D, name, value)
        # Bottom row first: the app's frames have v = 0 at the bottom.
        data = image.transpose(Image.FLIP_TOP_BOTTOM).convert("RGBA").tobytes() if image else None
        gl.glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, data)
        return tex.value

    def shader(self, kind, source):
        gl = self.gl
        s = gl.glCreateShader(kind)
        src = C.c_char_p(source.encode())
        gl.glShaderSource(s, 1, C.byref(src), None)
        gl.glCompileShader(s)
        ok = C.c_int()
        gl.glGetShaderiv(s, GL_COMPILE_STATUS, C.byref(ok))
        if not ok.value:
            log = C.create_string_buffer(4096)
            gl.glGetShaderInfoLog(s, 4096, None, log)
            raise RuntimeError(log.value.decode().strip())
        return s

    def program(self, vertex, fragment):
        gl = self.gl
        p = gl.glCreateProgram()
        gl.glAttachShader(p, self.shader(GL_VERTEX_SHADER, vertex))
        gl.glAttachShader(p, self.shader(GL_FRAGMENT_SHADER, fragment))
        gl.glLinkProgram(p)
        ok = C.c_int()
        gl.glGetProgramiv(p, GL_LINK_STATUS, C.byref(ok))
        if not ok.value:
            log = C.create_string_buffer(4096)
            gl.glGetProgramInfoLog(p, 4096, None, log)
            raise RuntimeError(log.value.decode().strip())
        return p

    def draw(self, program, textures, uniforms):
        """Draws a full-frame quad with [textures] (name -> texture id) and float [uniforms]; returns the frame."""
        gl = self.gl
        gl.glBindFramebuffer(GL_FRAMEBUFFER, self.fbo)
        gl.glViewport(0, 0, W, H)
        gl.glUseProgram(program)
        for unit, (name, tex) in enumerate(textures.items()):
            gl.glActiveTexture(GL_TEXTURE0 + unit)
            gl.glBindTexture(GL_TEXTURE_2D, tex)
            gl.glUniform1i(gl.glGetUniformLocation(program, name.encode()), unit)
        for name, values in uniforms.items():
            loc = gl.glGetUniformLocation(program, name.encode())
            if loc < 0:
                continue  # dropped by the compiler, as in the app
            arr = (C.c_float * len(values))(*values)
            {1: gl.glUniform1fv, 2: gl.glUniform2fv, 4: gl.glUniform4fv}.get(len(values), lambda l, n, a: gl.glUniformMatrix4fv(l, 1, 0, a))(loc, 1, arr)
        attr = gl.glGetAttribLocation(program, b"aFramePosition")
        gl.glBindBuffer(GL_ARRAY_BUFFER, self.vbo)
        gl.glEnableVertexAttribArray(attr)
        gl.glVertexAttribPointer(attr, 4, GL_FLOAT, 0, 0, None)
        gl.glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        pixels = (C.c_ubyte * (W * H * 4))()
        gl.glReadPixels(0, 0, W, H, GL_RGBA, GL_UNSIGNED_BYTE, pixels)
        return Image.frombytes("RGBA", (W, H), bytes(pixels)).transpose(Image.FLIP_TOP_BOTTOM).convert("RGB")


def scene(sunset):
    """A test picture with edges, colour, highlights and a big letter, so direction shows."""
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    top, bottom = ((255, 150, 70), (80, 40, 120)) if sunset else ((150, 210, 255), (30, 90, 200))
    for y in range(H):
        t = y / H
        d.line([(0, y), (W, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    if sunset:
        d.ellipse([120, 70, 190, 140], fill=(255, 235, 120))
        d.polygon([(0, 260), (70, 200), (150, 250), (216, 210), (216, 384), (0, 384)], fill=(25, 60, 35))
        d.rectangle([40, 280, 100, 340], fill=(235, 235, 225))
        d.polygon([(32, 282), (70, 245), (108, 282)], fill=(190, 40, 40))
    else:
        d.rectangle([0, 220, 216, 384], fill=(10, 50, 120))
        for y in range(235, 384, 18):
            d.line([(10, y), (206, y)], fill=(200, 220, 255), width=2)
        d.polygon([(60, 210), (160, 210), (140, 232), (80, 232)], fill=(250, 250, 250))
        d.line([(110, 140), (110, 210)], fill=(60, 40, 30), width=3)
        d.polygon([(112, 145), (112, 205), (150, 205)], fill=(240, 90, 60))
    letter = "A" if sunset else "B"
    d.rectangle([12, 12, 72, 82], fill=(0, 0, 0))
    d.text((30, 22), letter, fill=(255, 255, 255), font_size=48)
    return img


def labelled(img, text):
    tile = Image.new("RGB", (W, H + 22), (24, 24, 28))
    tile.paste(img, (0, 0))
    ImageDraw.Draw(tile).text((6, H + 4), text, fill=(230, 230, 230), font_size=13)
    return tile


def sheet(tiles, columns, path):
    rows = (len(tiles) + columns - 1) // columns
    out = Image.new("RGB", (columns * (W + 6), rows * (H + 28)), (12, 12, 14))
    for i, tile in enumerate(tiles):
        out.paste(tile, ((i % columns) * (W + 6), (i // columns) * (H + 28)))
    out.save(path)


def read(name):
    with open(os.path.join(SHADERS, name)) as f:
        return f.read()


def main():
    if not os.path.isdir(SHADERS):
        sys.exit("Run ./gradlew testDebugUnitTest --tests '*ShaderSourcesTest' first")
    os.makedirs(OUT, exist_ok=True)
    gpu = Gpu()
    print(f"GPU: {gpu.renderer}")
    with open(os.path.join(SHADERS, "values.json")) as f:
        values = json.load(f)
    a, b = scene(True), scene(False)
    tex_a, tex_b = gpu.texture(a, W, H), gpu.texture(b, W, H)
    identity = [1.0 if i % 5 == 0 else 0.0 for i in range(16)]
    failed = []

    def build(name, vertex, fragment):
        try:
            return gpu.program(read(vertex), read(fragment))
        except RuntimeError as e:
            failed.append(name)
            print(f"FAIL {name}:\n  " + str(e).replace("\n", "\n  "))
            return None

    tiles = [labelled(a, "original")]
    for name, params in values["effects"].items():
        program = build(name, "effect.vert", f"effect_{name}.frag")
        if program:
            frame = gpu.draw(program, {"uTexSampler": tex_a}, {
                "uTransformationMatrix": identity, "uTexTransformationMatrix": identity, "uParams": params,
                "uTime": [EFFECT_SECONDS], "uTexel": [1 / W, 1 / H], "uAspect": [W / H], "uActive": [1.0],
            })
            tiles.append(labelled(frame, name.lower()))
    sheet(tiles, 8, os.path.join(OUT, "effects.png"))

    tiles = []
    for f in sorted(os.listdir(SHADERS)):
        if not f.startswith("transition_"):
            continue
        name = f[len("transition_"):-len(".frag")]
        program = build(name, "layer.vert", f)
        if program:
            for progress in (0.25, 0.5, 0.75):
                eased = 4 * progress ** 3 if progress < 0.5 else 1 - (-2 * progress + 2) ** 3 / 2  # as Layer.progress
                frame = gpu.draw(program, {"uA": tex_a, "uB": tex_b}, {"uProgress": [eased], "uAspect": [W / H], "uTexel": [1 / W, 1 / H]})
                tiles.append(labelled(frame, f"{name.lower()} {int(progress * 100)}%"))
    sheet(tiles, 9, os.path.join(OUT, "overlapping.png"))

    tiles = []
    program = build("edge", "edge.vert", "edge.frag")
    if program:
        for name, by_progress in values["edges"].items():
            for progress, uniforms in by_progress.items():
                p = float(progress)
                frame = gpu.draw(program, {"uTexSampler": tex_a if p < 0.5 else tex_b}, {
                    "uTransformationMatrix": identity, "uTexTransformationMatrix": identity,
                    "uSize": [W, H], **uniforms,
                })
                tiles.append(labelled(frame, f"{name.lower()} {int(p * 100)}%"))
    sheet(tiles, 10, os.path.join(OUT, "edge.png"))

    print(f"Sheets in {os.path.relpath(OUT, ROOT)}: effects.png, overlapping.png, edge.png")
    if failed:
        sys.exit(f"{len(failed)} shader(s) failed to compile: {', '.join(failed)}")
    print("All shaders compiled")


if __name__ == "__main__":
    main()
