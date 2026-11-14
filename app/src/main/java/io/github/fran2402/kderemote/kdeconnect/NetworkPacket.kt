package io.github.fran2402.kderemote.kdeconnect

import org.json.JSONObject

/** A single KDE Connect packet: newline-terminated JSON with `id`, `type` and `body`. */
class NetworkPacket(val type: String, val body: JSONObject = JSONObject()) {

    fun serialize(): String {
        val json = JSONObject()
            .put("id", System.currentTimeMillis())
            .put("type", type)
            .put("body", body)
        return json.toString() + "\n"
    }

    override fun toString(): String = "NetworkPacket($type, $body)"

    companion object {
        fun parse(line: String): NetworkPacket {
            val json = JSONObject(line)
            return NetworkPacket(json.getString("type"), json.optJSONObject("body") ?: JSONObject())
        }
    }
}

object PacketType {
    const val IDENTITY = "kdeconnect.identity"
    const val PAIR = "kdeconnect.pair"
    const val MOUSEPAD_REQUEST = "kdeconnect.mousepad.request"
    const val MPRIS = "kdeconnect.mpris"
    const val MPRIS_REQUEST = "kdeconnect.mpris.request"
    const val SYSTEMVOLUME = "kdeconnect.systemvolume"
    const val SYSTEMVOLUME_REQUEST = "kdeconnect.systemvolume.request"
    const val RUNCOMMAND = "kdeconnect.runcommand"
    const val RUNCOMMAND_REQUEST = "kdeconnect.runcommand.request"
}

/** Special key codes understood by the desktop's Remote input (mousepad) plugin. */
object SpecialKey {
    const val BACKSPACE = 1
    const val TAB = 2
    const val LEFT = 4
    const val UP = 5
    const val RIGHT = 6
    const val DOWN = 7
    const val PAGE_UP = 8
    const val PAGE_DOWN = 9
    const val HOME = 10
    const val END = 11
    const val RETURN = 12
    const val DELETE = 13
    const val ESCAPE = 14

    /** Pressing a modifier on its own, e.g. Super to open a launcher. */
    const val CTRL = 17
    const val ALT = 18
    const val SHIFT = 19
    const val SUPER = 20

    /** F1 is 21, F12 is 32. */
    fun function(n: Int) = 20 + n
}
