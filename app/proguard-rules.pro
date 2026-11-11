# Project-specific R8 rules. Keep rules are verified against a release build in Phase 7.

# Android 10 crash (build 64): R8's horizontal class merging put Media3's DefaultAssetLoaderFactory
# and an unrelated class into one, widened its LogSessionId field to Object and added a
# check-cast to LogSessionId on every read. ART on API < 31 fails that cast's class resolution
# even when the value is null -> NoClassDefFoundError when a video is added. Classes that hold
# fields of types newer than minSdk 26 must not be merged, so their field types stay as written
# and the code paths guarded by SDK_INT checks never touch the missing classes.
# allowshrinking/allowobfuscation keep the size win; only optimization (merging) is blocked.
-keep,allowshrinking,allowobfuscation class * {
    android.media.metrics.** *;
    android.media.Spatializer *;
    android.media.Spatializer$** *;
    android.media.LoudnessCodecController *;
    android.media.LoudnessCodecController$** *;
    android.view.WindowInsetsController *;
    android.view.ScrollCaptureSession *;
    android.graphics.RenderNode *;
    android.graphics.ImageDecoder$Source *;
}
