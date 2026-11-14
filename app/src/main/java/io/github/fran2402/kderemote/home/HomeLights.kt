package io.github.fran2402.kderemote.home

import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import com.google.home.DeviceType
import com.google.home.FactoryRegistry
import com.google.home.Home
import com.google.home.HomeClient
import com.google.home.HomeConfig
import com.google.home.HomeDevice
import com.google.home.HomeException
import com.google.home.PermissionsResultStatus
import com.google.home.PermissionsState
import com.google.home.matter.standard.ColorControl
import com.google.home.matter.standard.ColorControlTrait
import com.google.home.matter.standard.ColorTemperatureLightDevice
import com.google.home.matter.standard.DimmableLightDevice
import com.google.home.matter.standard.ExtendedColorLightDevice
import com.google.home.matter.standard.LevelControl
import com.google.home.matter.standard.LevelControlTrait
import com.google.home.matter.standard.OnOff
import com.google.home.matter.standard.OnOffLightDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    internal val onOff: OnOff?,
    internal val levelControl: LevelControl?,
    internal val colorControl: ColorControl?,
)

/** Google Home lights through the Home APIs: permissions, live state and commands. */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeLights(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client: HomeClient = Home.getClient(
        context.applicationContext,
        homeConfig = HomeConfig(
            coroutineContext = Dispatchers.IO,
            factoryRegistry = FactoryRegistry(
                types = listOf(OnOffLightDevice, DimmableLightDevice, ColorTemperatureLightDevice, ExtendedColorLightDevice),
                traits = listOf(OnOff, LevelControl, ColorControl),
            ),
        ),
    )

    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val errors: SharedFlow<String> = _errors.asSharedFlow()

    val access: StateFlow<HomeAccess> = client.hasPermissions()
        .map { state ->
            when (state) {
                PermissionsState.GRANTED -> HomeAccess.Granted
                PermissionsState.PERMISSIONS_STATE_UNINITIALIZED -> HomeAccess.Checking
                else -> HomeAccess.NotGranted
            }
        }
        .catch { e ->
            report("Google Home is unavailable: ${e.message}")
            emit(HomeAccess.NotGranted)
        }
        .stateIn(scope, SharingStarted.Eagerly, HomeAccess.Checking)

    val lights: StateFlow<List<Light>> = access
        .flatMapLatest { if (it == HomeAccess.Granted) observeLights() else flowOf(emptyList()) }
        .catch { e ->
            report("Couldn't load lights: ${e.message}")
            emit(emptyList())
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Must be called from the Activity's onCreate, before it is started. */
    fun register(activity: ComponentActivity) {
        client.registerActivityResultCallerForPermissions(activity)
    }

    fun requestAccess() {
        scope.launch {
            try {
                val result = client.requestPermissions()
                if (result.status != PermissionsResultStatus.SUCCESS) report("Google Home access wasn't granted")
            } catch (e: HomeException) {
                report("Google Home sign-in failed: ${e.message}")
            }
        }
    }

    private fun observeLights(): Flow<List<Light>> = client.structures().flatMapLatest { structures ->
        val structure = structures.firstOrNull() ?: return@flatMapLatest flowOf(emptyList())
        combine(structure.rooms(), structure.devices()) { rooms, devices ->
            rooms.associate { it.id to it.name } to devices.filter { it.isLight() }
        }.flatMapLatest { (roomNames, devices) ->
            if (devices.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(devices.map { device -> device.types().map { types -> toLight(device, types, device.roomId?.let { roomNames[it] }) } }) {
                    it.filterNotNull().sortedWith(compareBy<Light>({ it.room ?: "~" }, { it.name.lowercase() }))
                }
            }
        }
    }

    private fun HomeDevice.isLight() =
        has(OnOffLightDevice) || has(DimmableLightDevice) || has(ColorTemperatureLightDevice) || has(ExtendedColorLightDevice)

    private fun toLight(device: HomeDevice, types: Set<DeviceType>, room: String?): Light? {
        val traits = types.flatMap { it.traits() }
        val onOff = traits.filterIsInstance<OnOff>().firstOrNull() ?: return null
        val level = traits.filterIsInstance<LevelControl>().firstOrNull()
        val color = traits.filterIsInstance<ColorControl>().firstOrNull()
        val hasColor = types.any { it is ExtendedColorLightDevice }
        val hasTemperature = hasColor || types.any { it is ColorTemperatureLightDevice }
        return Light(
            id = device.id.id,
            name = device.name,
            room = room,
            isOn = onOff.onOff == true,
            level = level?.currentLevel?.let { it.toInt() / MAX_LEVEL.toFloat() },
            colorTemperatureMireds = color?.colorTemperatureMireds?.toInt(),
            minMireds = color?.colorTempPhysicalMinMireds?.toInt()?.takeIf { it > 0 } ?: COOLEST_MIREDS,
            maxMireds = color?.colorTempPhysicalMaxMireds?.toInt()?.takeIf { it > 0 } ?: WARMEST_MIREDS,
            supportsTemperature = hasTemperature && color != null,
            hue = color?.currentHue?.let { it.toInt() * 360f / MAX_HUE },
            saturation = color?.currentSaturation?.let { it.toInt() / MAX_HUE.toFloat() },
            supportsColor = hasColor && color != null,
            onOff = onOff,
            levelControl = level,
            colorControl = color,
        )
    }

    fun setOn(light: Light, on: Boolean) = command("Couldn't switch ${light.name}") {
        val trait = light.onOff ?: return@command
        if (on) trait.on() else trait.off()
    }

    fun setLevel(light: Light, level: Float) = command("Couldn't dim ${light.name}") {
        light.levelControl?.moveToLevelWithOnOff(
            level = (level.coerceIn(0.01f, 1f) * MAX_LEVEL).toInt().toUByte(),
            transitionTime = null,
            optionsMask = LevelControlTrait.OptionsBitmap(),
            optionsOverride = LevelControlTrait.OptionsBitmap(),
        )
    }

    fun setColorTemperature(light: Light, mireds: Int) = command("Couldn't change ${light.name}'s white") {
        light.colorControl?.moveToColorTemperature(
            colorTemperatureMireds = mireds.coerceIn(light.minMireds, light.maxMireds).toUShort(),
            transitionTime = TRANSITION.toUShort(),
            optionsMask = ColorControlTrait.OptionsBitmap(),
            optionsOverride = ColorControlTrait.OptionsBitmap(),
        )
    }

    /** [hue] 0..360, [saturation] 0..1. */
    fun setColor(light: Light, hue: Float, saturation: Float) = command("Couldn't change ${light.name}'s colour") {
        light.colorControl?.moveToHueAndSaturation(
            hue = (hue / 360f * MAX_HUE).toInt().coerceIn(0, MAX_HUE).toUByte(),
            saturation = (saturation * MAX_HUE).toInt().coerceIn(0, MAX_HUE).toUByte(),
            transitionTime = TRANSITION.toUShort(),
            optionsMask = ColorControlTrait.OptionsBitmap(),
            optionsOverride = ColorControlTrait.OptionsBitmap(),
        )
    }

    private fun command(failure: String, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: HomeException) {
                report("$failure: ${e.message}")
            }
        }
    }

    private fun report(message: String) {
        Log.w("KdeRemote", message)
        _errors.tryEmit(message)
    }

    companion object {
        /** Matter levels and hue/saturation run 0..254. */
        private const val MAX_LEVEL = 254
        private const val MAX_HUE = 254

        /** About 6500 K and 2200 K, used when a light doesn't report its range. */
        const val COOLEST_MIREDS = 153
        const val WARMEST_MIREDS = 454

        /** Tenths of a second. */
        private const val TRANSITION = 3
    }
}
