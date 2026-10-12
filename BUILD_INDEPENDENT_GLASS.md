# Independent Liquid Glass build notes

## What changed
- HyperOS private APIs (`setMiGlass`, `setGlassBloom`, …) removed.
- Single portable AGSL liquid-glass path (Android 13+), tint+highlight fallback below.
- Settings: Appearance → Liquid glass material (always enabled control).

## Build APK
Requirements: Android SDK 37, JDK 17+, LSPosed-compatible signing key.

```bash
cd WeType_UI_Enhanced-main
./gradlew :app:assembleRelease
# APK: app/build/outputs/apk/release/app-release-unsigned.apk  (sign before install)
```

## Install
1. Install the signed APK
2. Enable module in LSPosed, scope: 微信输入法 (`com.tencent.wetype`)
3. Force-stop WeType, open module settings → Appearance → enable Liquid glass

## Tests
`GlassMaterialOverridesTest` may need updates for the new parameter model.
Skip tests if needed: `./gradlew :app:assembleRelease -x test`
