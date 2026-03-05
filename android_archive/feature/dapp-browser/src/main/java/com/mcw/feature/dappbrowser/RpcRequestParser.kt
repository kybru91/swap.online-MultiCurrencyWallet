package com.mcw.feature.dappbrowser

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Parses EIP-1193 JSON-RPC requests from the JS bridge.
 *
 * Expected format from the injected window.ethereum provider:
 * ```json
 * {"method": "eth_requestAccounts", "params": []}
 * ```
 *
 * Uses Android's built-in org.json rather than Moshi for parsing bridge
 * messages, since the format is simple and fixed.
 */
object RpcRequestParser {

  /**
   * Parse a JSON-RPC request string into an [RpcRequest].
   *
   * @param json the JSON string from the JS bridge
   * @return [RpcRequest] if valid, null if JSON is malformed or missing required fields
   */
  fun parse(json: String): RpcRequest? {
    return try {
      val obj = JSONObject(json)
      val method = obj.optString("method", "")
      if (method.isEmpty()) return null

      val paramsArray = obj.optJSONArray("params") ?: JSONArray()
      val params = mutableListOf<Any>()
      for (i in 0 until paramsArray.length()) {
        params.add(paramsArray.get(i))
      }

      val id = obj.opt("id") // may be null, number, or string

      RpcRequest(method = method, params = params, id = id)
    } catch (e: JSONException) {
      null
    }
  }

  /**
   * Build a JSON-RPC success response string.
   *
   * @param id the request ID (from the original request)
   * @param result the result value
   * @return JSON string in EIP-1193 response format
   */
  fun buildSuccessResponse(id: Any?, result: Any?): String {
    val obj = JSONObject()
    obj.put("id", id)
    obj.put("result", result)
    return obj.toString()
  }

  /**
   * Build a JSON-RPC error response string.
   *
   * @param id the request ID (from the original request)
   * @param code EIP-1193 error code (e.g., 4001 for user rejected, 4200 for unsupported method)
   * @param message human-readable error message
   * @return JSON string in EIP-1193 error format
   */
  fun buildErrorResponse(id: Any?, code: Int, message: String): String {
    val obj = JSONObject()
    obj.put("id", id)
    val error = JSONObject()
    error.put("code", code)
    error.put("message", message)
    obj.put("error", error)
    return obj.toString()
  }
}

/**
 * Parsed EIP-1193 JSON-RPC request.
 *
 * @param method the RPC method name (e.g., "eth_requestAccounts")
 * @param params the parameters array
 * @param id the optional request ID (for matching responses)
 */
data class RpcRequest(
  val method: String,
  val params: List<Any>,
  val id: Any? = null,
)
