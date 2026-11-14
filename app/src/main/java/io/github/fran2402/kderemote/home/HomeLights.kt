package io.github.fran2402.kderemote.home

import androidx.activity.ComponentActivity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

enum class HomeAccess { Checking, NotGranted, Granted }

/** A light in the user's Google Home, flattened from its Matter traits. */
data class Light(
    val id: String,
    val name: String,
    val room: String?,
    val isOn: Boolean,
    /** 0f..1f, or null when the light can't dim. */
    val level: Float?,
    val colorTemperatureMireds: Int?,
    val minMireds: Int,
    val maxMireds: Int,
    val supportsTemperature: Boolean,
    /** Hue 0..360 and saturation 0..1, when the light reports them. */
    val hue: Float?,
    val saturation: Float?,
    val supportsColor: Boolean,
)

/**
 * Google Home lights. The implementation lives in the `googleHome` source set and is only compiled
 * when the Home APIs SDK is present (see README); otherwise [createHomeLights] returns null.
 */
interface HomeLights {
    val access: StateFlow<HomeAccess>
    val lights: StateFlow<List<Light>>
    val errors: SharedFlow<String>

    /** Must be called from the Activity's onCreate, before it is started. */
    fun register(activity: ComponentActivity)
    fun requestAccess()
    fun setOn(light: Light, on: Boolean)

    /** [level] 0..1. */
    fun setLevel(light: Light, level: Float)
    fun setColorTemperature(light: Light, mireds: Int)

    /** [hue] 0..360, [saturation] 0..1. */
    fun setColor(light: Light, hue: Float, saturation: Float)
}
