package com.mcw.feature.walletconnect

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * JSON serialization/deserialization for WalletConnect sessions.
 *
 * Uses org.json (Android built-in) to avoid adding Gson/Moshi dependency.
 * Sessions are stored as a JSON array in EncryptedSharedPreferences via SecureStorage.
 *
 * Format:
 * ```json
 * [
 *   {
 *     "topic": "abc123",
 *     "peerName": "My dApp",
 *     "peerUrl": "https://example.com",
 *     "peerIcon": "https://example.com/icon.png",
 *     "chains": ["eip155:1", "eip155:137"],
 *     "methods": ["eth_sendTransaction", "personal_sign"],
 *     "createdAt": 1709654400000
 *   }
 * ]
 * ```
 */
object SessionSerializer {

    /**
     * Serializes a list of sessions to JSON string.
     */
    fun toJson(sessions: List<WalletConnectSession>): String {
        val array = JSONArray()
        for (session in sessions) {
            val obj = JSONObject().apply {
                put("topic", session.topic)
                put("peerName", session.peerName)
                put("peerUrl", session.peerUrl)
                put("peerIcon", session.peerIcon ?: JSONObject.NULL)
                put("chains", JSONArray(session.chains))
                put("methods", JSONArray(session.methods))
                put("createdAt", session.createdAt)
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Deserializes a JSON string to a list of sessions.
     *
     * @return list of sessions, or empty list if JSON is null/invalid
     */
    fun fromJson(json: String?): List<WalletConnectSession> {
        if (json.isNullOrBlank()) return emptyList()

        return try {
            val array = JSONArray(json)
            val sessions = mutableListOf<WalletConnectSession>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                sessions.add(
                    WalletConnectSession(
                        topic = obj.getString("topic"),
                        peerName = obj.getString("peerName"),
                        peerUrl = obj.getString("peerUrl"),
                        peerIcon = if (obj.isNull("peerIcon")) null else obj.optString("peerIcon"),
                        chains = obj.optJSONArray("chains")?.let { chainsArr ->
                            (0 until chainsArr.length()).map { chainsArr.getString(it) }
                        } ?: emptyList(),
                        methods = obj.optJSONArray("methods")?.let { methodsArr ->
                            (0 until methodsArr.length()).map { methodsArr.getString(it) }
                        } ?: emptyList(),
                        createdAt = obj.getLong("createdAt")
                    )
                )
            }
            sessions
        } catch (e: JSONException) {
            emptyList()
        }
    }
}
