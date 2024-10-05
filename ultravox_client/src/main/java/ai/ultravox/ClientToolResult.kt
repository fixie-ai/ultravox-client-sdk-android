package ai.ultravox

/** Return type for client-implemented tools. */
data class ClientToolResult(
    /** The result, exactly as it will be seen by the model. (Often JSON.) */
    val result: String,
    /**
     * For tools that affect the call instead of providing information back to the model,
     * responseType may be set to indicate how the call should be altered. In such cases,
     * result should be encoded JSON with instructions for the server. The schema depends
     * on the response type. See https://docs.ultravox.ai/tools/ for more details.
     */
    val responseType: String?,
)
