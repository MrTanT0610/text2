package com.xposed.wetypehook.wetype.graphics

import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import com.xposed.wetypehook.wetype.settings.GlassMaterialOverrides
import com.xposed.wetypehook.xposed.Log
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Independent liquid-glass material for the IME background carrier.
 *
 * Single fully-compatible path:
 * - Android 13+ (API 33): AGSL RuntimeShader with SDF edge refraction, chromatic
 *   dispersion, Fresnel rim and frost blur — no HyperOS private APIs.
 * - Older APIs: high-quality translucent tint + soft edge highlight fallback.
 *
 * Designed for extreme visual quality with moderate cost (single shader pass,
 * optional downsampled blur radius capped for keyboard use).
 */
internal class WeTypeHyperMaterial(
    private val view: View,
    private var overrides: GlassMaterialOverrides = GlassMaterialOverrides(),
    @Suppress("UNUSED_PARAMETER") sampleBehindWindow: Boolean = true,
) {
    private data class Style(val isDark: Boolean, val density: Float, val tintColor: Int?)

    private var appliedStyle: Style? = null
    private var shader: RuntimeShader? = null
    private var lastGeometry: Triple<Int, Int, WeTypeCornerRadii>? = null
    private var cornerRadii: WeTypeCornerRadii? = null

    private val glassLayer: View? = if (view is ViewGroup && agslSupported) {
        View(view.context).apply {
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            view.addView(
                this,
                0,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    } else {
        null
    }

    private val materialView: View = glassLayer ?: view

    fun apply(isDark: Boolean, tintColor: Int? = null): Boolean {
        if (!overrides.enabled) {
            clear()
            return false
        }
        val density = view.resources.displayMetrics.density
        val style = Style(isDark, density, tintColor)
        if (appliedStyle == style && materialView.getTag(TAG_APPLIED) == true) {
            updateShaderUniforms(style)
            return true
        }
        return runCatching {
            clearEffects()
            if (agslSupported) {
                applyAgslGlass(style)
            } else {
                applyFallbackGlass(style)
            }
            appliedStyle = style
            materialView.setTag(TAG_APPLIED, true)
            true
        }.getOrElse {
            Log.e(it)
            applyFallbackGlass(style)
            appliedStyle = style
            materialView.setTag(TAG_APPLIED, true)
            true
        }
    }

    fun updateGeometry(corners: WeTypeCornerRadii) {
        cornerRadii = corners
        val w = view.width
        val h = view.height
        if (w <= 0 || h <= 0) return
        val geometry = Triple(w, h, corners)
        if (geometry == lastGeometry) return
        lastGeometry = geometry

        glassLayer?.let { layer ->
            val outset = (overrides.depth * 0.35f).toInt().coerceIn(0, 48)
            layer.measure(
                View.MeasureSpec.makeMeasureSpec(w + outset * 2, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY),
            )
            layer.layout(-outset, 0, w + outset, h)
            val radius = corners.maxRadius()
            layer.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(target: View, outline: Outline) {
                    outline.setRoundRect(0, 0, target.width, target.height, radius)
                }
            }
            layer.clipToOutline = true
        }
        appliedStyle?.let { updateShaderUniforms(it) }
        materialView.invalidate()
    }

    fun clear(force: Boolean = false) {
        if (appliedStyle == null && !force) return
        clearEffects()
        appliedStyle = null
        lastGeometry = null
        materialView.setTag(TAG_APPLIED, false)
    }

    private fun clearEffects() {
        if (Build.VERSION.SDK_INT >= 31) {
            runCatching { materialView.setRenderEffect(null) }
        }
        materialView.background = null
        shader = null
    }

    private fun applyAgslGlass(style: Style) {
        val rs = shader ?: RuntimeShader(LIQUID_GLASS_AGSL).also { shader = it }
        updateShaderUniforms(style)
        // Frost blur first, then optical glass. Keep blur modest for IME performance.
        val blurPx = overrides.blurRadius.coerceIn(0f, 48f)
        val effect = if (blurPx > 0.5f && Build.VERSION.SDK_INT >= 31) {
            val blur = RenderEffect.createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
            RenderEffect.createChainEffect(
                RenderEffect.createRuntimeShaderEffect(rs, "uContent"),
                blur,
            )
        } else {
            RenderEffect.createRuntimeShaderEffect(rs, "uContent")
        }
        materialView.setRenderEffect(effect)
        // Transparent so the shader samples compositor content behind the IME when available.
        materialView.setBackgroundColor(Color.TRANSPARENT)
    }

    private fun updateShaderUniforms(style: Style) {
        val rs = shader ?: return
        val w = max(materialView.width, 1).toFloat()
        val h = max(materialView.height, 1).toFloat()
        val density = style.density
        val radius = (cornerRadii?.maxRadius() ?: (28f * density))
        val tint = style.tintColor ?: fallbackColor(style.isDark)
        val tr = Color.red(tint) / 255f
        val tg = Color.green(tint) / 255f
        val tb = Color.blue(tint) / 255f
        val ta = (Color.alpha(tint) / 255f) * overrides.opacity

        val angleRad = Math.toRadians(overrides.lightAngle.toDouble())
        val lx = sin(angleRad).toFloat()
        val ly = -cos(angleRad).toFloat()

        rs.setFloatUniform("uResolution", w, h)
        rs.setFloatUniform("uCornerRadius", radius)
        rs.setFloatUniform("uIOR", overrides.refraction)
        rs.setFloatUniform("uDepth", overrides.depth)
        rs.setFloatUniform("uThickness", overrides.thickness)
        rs.setFloatUniform("uDispersion", overrides.dispersion)
        rs.setFloatUniform("uLightIntensity", overrides.lightIntensity)
        rs.setFloatUniform("uLightDir", lx, ly)
        rs.setFloatUniform("uEdgeCurve", overrides.edgeCurve)
        rs.setFloatUniform("uTint", tr, tg, tb, ta)
        rs.setFloatUniform("uIsDark", if (style.isDark) 1f else 0f)
    }

    private fun applyFallbackGlass(style: Style) {
        val tint = style.tintColor ?: fallbackColor(style.isDark)
        val alpha = ((Color.alpha(tint) / 255f) * overrides.opacity * 255f).toInt().coerceIn(40, 230)
        val color = Color.argb(alpha, Color.red(tint), Color.green(tint), Color.blue(tint))
        val radius = cornerRadii?.maxRadius() ?: (28f * style.density)
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            val strokeAlpha = (overrides.lightIntensity * 90f).toInt().coerceIn(20, 120)
            setStroke(
                max(1, (style.density * 1.2f).toInt()),
                Color.argb(
                    strokeAlpha,
                    if (style.isDark) 255 else 255,
                    if (style.isDark) 255 else 255,
                    if (style.isDark) 255 else 255,
                ),
            )
        }
        materialView.background = drawable
        if (Build.VERSION.SDK_INT >= 31 && overrides.blurRadius > 1f) {
            val b = overrides.blurRadius.coerceIn(2f, 32f)
            runCatching {
                materialView.setRenderEffect(
                    RenderEffect.createBlurEffect(b, b, android.graphics.Shader.TileMode.CLAMP),
                )
            }
        }
    }

    companion object {
        private const val TAG_APPLIED = 0x4C475F41 // 'LG_A'

        val agslSupported: Boolean by lazy {
            Build.VERSION.SDK_INT >= 33 && runCatching {
                RuntimeShader(LIQUID_GLASS_AGSL)
                true
            }.getOrDefault(false)
        }

        fun fallbackColor(isDark: Boolean): Int =
            if (isDark) 0xCC18191B.toInt() else 0xB3E8E9EB.toInt()

        /** Always available — independent path has no system visual-effect gate. */
        fun isAvailable(@Suppress("UNUSED_PARAMETER") context: Context): Boolean = true

        /** Glass overrides are always usable on the independent path. */
        fun areGlassOverridesAvailable(): Boolean = true

        fun observeAvailability(
            @Suppress("UNUSED_PARAMETER") context: Context,
            @Suppress("UNUSED_PARAMETER") onChanged: () -> Unit,
        ): () -> Unit = {}

        /**
         * Extreme-quality liquid glass AGSL.
         *
         * Optical model:
         * - Rounded-rect SDF → height field → surface normal
         * - Snell refraction UV offset (edge lens)
         * - Per-channel dispersion
         * - Fresnel rim + directional specular
         * - Tint mix
         *
         * uContent is the backdrop sampled by RenderEffect (framework-provided).
         */
        private const val LIQUID_GLASS_AGSL = """
            uniform shader uContent;
            uniform float2 uResolution;
            uniform float uCornerRadius;
            uniform float uIOR;
            uniform float uDepth;
            uniform float uThickness;
            uniform float uDispersion;
            uniform float uLightIntensity;
            uniform float2 uLightDir;
            uniform float uEdgeCurve;
            uniform float4 uTint;
            uniform float uIsDark;

            float sdRoundRect(float2 p, float2 b, float r) {
                float2 q = abs(p) - b + r;
                return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
            }

            float2 refractOffset(float2 n, float ior, float thickness) {
                // Approximate slab refraction: stronger near grazing normals.
                float eta = 1.0 / max(ior, 1.01);
                float ndi = 1.0; // view from +Z
                float k = 1.0 - eta * eta * (1.0 - ndi * ndi);
                float2 dir = n * (1.0 - eta);
                return dir * thickness * 0.015 * (k > 0.0 ? 1.0 : 0.35);
            }

            half4 main(float2 fragCoord) {
                float2 uv = fragCoord;
                float2 center = uResolution * 0.5;
                float2 halfSize = uResolution * 0.5;
                float radius = min(uCornerRadius, min(halfSize.x, halfSize.y));

                float2 p = uv - center;
                float dist = sdRoundRect(p, halfSize - float2(0.5), radius);

                // Soft clip outside glass
                float alphaMask = 1.0 - smoothstep(-1.2, 1.2, dist);
                if (alphaMask < 0.004) {
                    return half4(0.0);
                }

                // Height field from SDF (edge bevel)
                float edge = clamp(-dist / max(uDepth, 1.0), 0.0, 1.0);
                float height = pow(edge, max(uEdgeCurve, 0.15));

                // Screen-space normal from height gradient
                float e = 1.25;
                float d1 = sdRoundRect(p + float2(e, 0.0), halfSize - float2(0.5), radius);
                float d2 = sdRoundRect(p - float2(e, 0.0), halfSize - float2(0.5), radius);
                float d3 = sdRoundRect(p + float2(0.0, e), halfSize - float2(0.5), radius);
                float d4 = sdRoundRect(p - float2(0.0, e), halfSize - float2(0.5), radius);
                float2 grad = float2(d1 - d2, d3 - d4);
                float2 n2 = length(grad) > 1e-5 ? normalize(grad) : float2(0.0);
                // Blend normal strength by height so center stays clear
                float2 normal = n2 * height;

                float ior = max(uIOR, 1.01);
                float2 baseOff = refractOffset(normal, ior, uThickness);

                // Chromatic dispersion: R/G/B sample with slightly different IOR
                float disp = uDispersion * 0.55;
                float2 offR = refractOffset(normal, ior * (1.0 - disp * 0.08), uThickness);
                float2 offG = baseOff;
                float2 offB = refractOffset(normal, ior * (1.0 + disp * 0.10), uThickness);

                float2 uvR = clamp(uv + offR, float2(0.5), uResolution - float2(0.5));
                float2 uvG = clamp(uv + offG, float2(0.5), uResolution - float2(0.5));
                float2 uvB = clamp(uv + offB, float2(0.5), uResolution - float2(0.5));

                half4 cR = uContent.eval(uvR);
                half4 cG = uContent.eval(uvG);
                half4 cB = uContent.eval(uvB);
                half3 refracted = half3(cR.r, cG.g, cB.b);

                // Fresnel rim
                float fresnel = pow(1.0 - height, 2.2);
                float rim = fresnel * uLightIntensity;

                // Directional specular along light
                float2 ld = length(uLightDir) > 0.01 ? normalize(uLightDir) : float2(0.7, -0.7);
                float spec = pow(max(0.0, dot(normal, ld)), 32.0) * uLightIntensity * 0.85;
                half3 lightCol = half3(1.0, 1.0, 1.0);
                if (uIsDark > 0.5) {
                    lightCol = half3(0.85, 0.90, 1.0);
                }

                half3 tint = half3(uTint.r, uTint.g, uTint.b);
                float tintA = uTint.a;

                // Mix: refracted backdrop, then tint, then rim/specular
                half3 base = mix(refracted, tint, tintA * 0.55);
                base += lightCol * half(rim * 0.55 + spec);

                // Slight center clarity boost
                float centerClear = smoothstep(0.0, 0.45, height);
                base = mix(refracted * half(0.92) + tint * half(tintA * 0.25), base, half(centerClear));

                float outA = alphaMask * max(tintA, 0.55);
                return half4(base * half(outA), half(outA));
            }
        """
    }
}
