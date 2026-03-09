package rip.build.courier.data.remote.api

import org.json.JSONObject

class BridgeApiException(
    val statusCode: Int,
    val errorBody: String
) : Exception("HTTP $statusCode: $errorBody") {

    val displayMessage: String
        get() = try {
            JSONObject(errorBody)
                .optString("failureReason", "")
                .ifEmpty { JSONObject(errorBody).optString("reason", "") }
                .ifEmpty { errorBody }
        } catch (_: Exception) {
            errorBody
        }
}
