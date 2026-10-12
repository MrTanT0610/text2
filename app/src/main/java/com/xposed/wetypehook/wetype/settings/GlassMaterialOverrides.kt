package com.xposed.wetypehook.wetype.settings

/**
 * Independent liquid-glass optical parameters.
 * No HyperOS private arrays — one portable model for all devices.
 */
data class GlassMaterialOverrides(
    /** Master switch for the independent liquid-glass path. */
    val enabled: Boolean = false,
    /** Index of refraction (1.0–2.5). Higher = stronger edge lensing. */
    val refraction: Float = DEFAULT_REFRACTION,
    /** Edge depth / bevel height in physical pixels (1–120). */
    val depth: Float = DEFAULT_DEPTH,
    /** Optical thickness driving refraction displacement (1–200). */
    val thickness: Float = DEFAULT_THICKNESS,
    /** Chromatic dispersion strength (0–1). */
    val dispersion: Float = DEFAULT_DISPERSION,
    /** Specular / rim light intensity (0–1). */
    val lightIntensity: Float = DEFAULT_LIGHT_INTENSITY,
    /** Light azimuth in degrees (0–359). */
    val lightAngle: Float = DEFAULT_LIGHT_ANGLE,
    /** Frost blur radius in physical pixels (0–80). */
    val blurRadius: Float = DEFAULT_BLUR_RADIUS,
    /** Edge curve exponent (0.2–3). Lower = sharper bevel. */
    val edgeCurve: Float = DEFAULT_EDGE_CURVE,
    /** Overall glass opacity multiplier (0–1). */
    val opacity: Float = DEFAULT_OPACITY,
) {
    init {
        require(refraction in 1.0f..2.5f)
        require(depth in 1f..120f)
        require(thickness in 1f..200f)
        require(dispersion in 0f..1f)
        require(lightIntensity in 0f..1f)
        require(lightAngle in 0f..359.999f || lightAngle == 360f)
        require(blurRadius in 0f..80f)
        require(edgeCurve in 0.2f..3f)
        require(opacity in 0f..1f)
    }

    fun withEnabled(on: Boolean): GlassMaterialOverrides = copy(enabled = on)

    companion object {
        const val DEFAULT_REFRACTION = 1.52f
        const val DEFAULT_DEPTH = 28f
        const val DEFAULT_THICKNESS = 48f
        const val DEFAULT_DISPERSION = 0.35f
        const val DEFAULT_LIGHT_INTENSITY = 0.72f
        const val DEFAULT_LIGHT_ANGLE = 135f
        const val DEFAULT_BLUR_RADIUS = 18f
        const val DEFAULT_EDGE_CURVE = 1.0f
        const val DEFAULT_OPACITY = 1.0f

        /** Extreme-quality preset used when the user turns liquid glass on. */
        fun extreme(): GlassMaterialOverrides = GlassMaterialOverrides(
            enabled = true,
            refraction = 1.62f,
            depth = 36f,
            thickness = 64f,
            dispersion = 0.42f,
            lightIntensity = 0.85f,
            lightAngle = 128f,
            blurRadius = 22f,
            edgeCurve = 0.85f,
            opacity = 1.0f,
        )

        fun defaults(): GlassMaterialOverrides = GlassMaterialOverrides(enabled = false)

        fun read(values: Map<String, Any>): GlassMaterialOverrides = runCatching {
            val enabled = (values["liquid_glass_enabled"] as? Boolean)
                ?: ((values["glass_material_type"] as? Int) == 1)
                ?: false
            // Prefer new keys; fall back to coarse mapping from legacy 42-float glass if present.
            val refraction = (values["lg_refraction"] as? Float)
                ?: (values["glass_params_32"] as? Float)?.coerceIn(1f, 2.5f)
                ?: DEFAULT_REFRACTION
            val depth = (values["lg_depth"] as? Float)
                ?: (values["glass_params_19"] as? Float)?.coerceIn(1f, 120f)
                ?: DEFAULT_DEPTH
            val thickness = (values["lg_thickness"] as? Float)
                ?: (values["glass_params_21"] as? Float)?.coerceIn(1f, 200f)
                ?: DEFAULT_THICKNESS
            val dispersion = (values["lg_dispersion"] as? Float)?.coerceIn(0f, 1f) ?: DEFAULT_DISPERSION
            val lightIntensity = (values["lg_light_intensity"] as? Float)?.coerceIn(0f, 1f)
                ?: ((values["glass_params_28"] as? Float)?.coerceIn(0f, 1f) ?: DEFAULT_LIGHT_INTENSITY)
            val lightAngle = (values["lg_light_angle"] as? Float)?.coerceIn(0f, 360f) ?: DEFAULT_LIGHT_ANGLE
            val blurRadius = (values["lg_blur_radius"] as? Float)
                ?: (values["glass_blur_small"] as? Int)?.toFloat()?.coerceIn(0f, 80f)
                ?: DEFAULT_BLUR_RADIUS
            val edgeCurve = (values["lg_edge_curve"] as? Float)?.coerceIn(0.2f, 3f)
                ?: (values["glass_params_20"] as? Float)?.coerceIn(0.2f, 3f)
                ?: DEFAULT_EDGE_CURVE
            val opacity = (values["lg_opacity"] as? Float)?.coerceIn(0f, 1f) ?: DEFAULT_OPACITY
            GlassMaterialOverrides(
                enabled = enabled,
                refraction = refraction,
                depth = depth,
                thickness = thickness,
                dispersion = dispersion,
                lightIntensity = lightIntensity,
                lightAngle = lightAngle,
                blurRadius = blurRadius,
                edgeCurve = edgeCurve,
                opacity = opacity,
            )
        }.getOrDefault(defaults())
    }
}

/** Slider metadata for the independent liquid-glass controls. */
enum class LiquidGlassParameter(
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
) {
    REFRACTION(1.0f..2.5f, 0.01f),
    DEPTH(1f..120f, 1f),
    THICKNESS(1f..200f, 1f),
    DISPERSION(0f..1f, 0.01f),
    LIGHT_INTENSITY(0f..1f, 0.01f),
    LIGHT_ANGLE(0f..360f, 1f),
    BLUR_RADIUS(0f..80f, 1f),
    EDGE_CURVE(0.2f..3f, 0.05f),
    OPACITY(0f..1f, 0.01f);

    fun read(o: GlassMaterialOverrides): Float = when (this) {
        REFRACTION -> o.refraction
        DEPTH -> o.depth
        THICKNESS -> o.thickness
        DISPERSION -> o.dispersion
        LIGHT_INTENSITY -> o.lightIntensity
        LIGHT_ANGLE -> o.lightAngle
        BLUR_RADIUS -> o.blurRadius
        EDGE_CURVE -> o.edgeCurve
        OPACITY -> o.opacity
    }

    fun write(o: GlassMaterialOverrides, value: Float): GlassMaterialOverrides {
        val v = value.coerceIn(range.start, range.endInclusive)
        return when (this) {
            REFRACTION -> o.copy(refraction = v)
            DEPTH -> o.copy(depth = v)
            THICKNESS -> o.copy(thickness = v)
            DISPERSION -> o.copy(dispersion = v)
            LIGHT_INTENSITY -> o.copy(lightIntensity = v)
            LIGHT_ANGLE -> o.copy(lightAngle = v % 360f)
            BLUR_RADIUS -> o.copy(blurRadius = v)
            EDGE_CURVE -> o.copy(edgeCurve = v)
            OPACITY -> o.copy(opacity = v)
        }
    }
}

// Legacy names kept so older call sites / tests compile during migration.
enum class GlassOverrideField { GLASS, BLUR_RADII, BLOOM, MATERIAL_TYPE }

enum class GlassSliderParameter(
    val range: ClosedFloatingPointRange<Float>,
    val step: Float,
) {
    LIGHT_ANGLE(0f..359f, 1f),
    LIGHT_INTENSITY(0f..100f, 1f),
    REFRACTION(1f..2.5f, 0.01f),
    DEPTH(1f..100f, 1f),
    SPLAY(1f..180f, 1f),
    THICKNESS(1f..200f, 1f),
    EDGE_CURVE(0.1f..4f, 0.05f);
}
