package com.cgl.avai

/** Versioned wire contract shared by AVai's exported Messenger service and Friend's client. */
object AiBridgeProtocol {
    const val ACTION_CONSENT = "com.cgl.avai.action.CONSENT"
    const val PERMISSION_USE_AI = "com.cgl.avai.permission.USE_AI"
    const val MESSAGE_INFER = 701
    const val MESSAGE_RESULT = 702
    const val KEY_PROTOCOL_VERSION = "protocol_version"
    const val KEY_CHAT_ID = "chat_id"
    const val KEY_PROMPT = "prompt"
    const val KEY_HISTORY_JSON = "history_json"
    const val KEY_TONE = "tone"
    const val KEY_MEMORY = "memory"
    const val KEY_IMPORTED_HISTORY = "imported_history"
    const val KEY_RESULT = "result"
    const val KEY_ERROR = "error"
}
