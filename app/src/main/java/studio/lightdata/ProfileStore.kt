package studio.lightdata

import android.content.SharedPreferences

/**
 * Commit the active profile and its samples together so a failed sweep cannot replace a usable
 * profile.
 */
class ProfileStore(private val prefs: SharedPreferences) {
    fun save(lens: String, profile: Calibration) {
        require(profile.response.size == 256 && profile.response.all(Double::isFinite))
        require(profile.stops.isFinite() && profile.stops > 0 && profile.iso > 0)
        val key = lens + "/" + profile.channel + "/" + profile.iso
        prefs
            .edit()
            .putString("profile/" + lens, key)
            .putString("lut/" + key, profile.response.joinToString(","))
            .putInt("lutVersion/" + key, if (profile.legacyDecode) 1 else 2)
            .putString("range/" + key, profile.stops.toString())
            .apply()
    }

    fun load(lens: String): Calibration? {
        val key = prefs.getString("profile/" + lens, null) ?: return null
        return runCatching {
                val parts = key.split("/")
                require(parts.size == 3 && parts[0] == lens)
                val response =
                    prefs
                        .getString("lut/" + key, null)!!
                        .split(",")
                        .map(String::toDouble)
                        .toDoubleArray()
                val stops = prefs.getString("range/" + key, null)!!.toDouble()
                val iso = parts.last().toInt()
                require(
                    response.size == 256 &&
                        response.all(Double::isFinite) &&
                        stops.isFinite() &&
                        stops > 0 &&
                        iso > 0
                )
                Calibration(response, stops, parts[1], iso, prefs.getInt("lutVersion/" + key, 1) < 2)
            }
            .getOrNull()
    }
}
