package com.cybersentinel.aidiag.ai

import org.json.JSONArray
import org.json.JSONObject
import javax.net.ssl.HttpsURLConnection
import java.net.URL

class AiRequestException(message: String) : Exception(message)

/**
 * Client minimale per l'API Messages di Anthropic (Claude): una singola
 * chiamata HTTPS, nessuna libreria esterna oltre org.json (già incluso in
 * Android). Va chiamato da un thread di background (IO), mai dal thread UI.
 */
class AiClient(private val apiKey: String, private val model: String) {

    fun analyze(prompt: String): String {
        val connection = (URL(ENDPOINT).openConnection() as HttpsURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
            setRequestProperty("content-type", "application/json")
            doOutput = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

        val body = JSONObject().apply {
            put("model", model)
            put("max_tokens", 1200)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
        }

        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

        val responseCode = connection.responseCode
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream.bufferedReader().use { it.readText() }

        if (responseCode !in 200..299) {
            throw AiRequestException("Errore HTTP $responseCode: ${extractErrorMessage(responseText)}")
        }

        val content = JSONObject(responseText).getJSONArray("content")
        val text = (0 until content.length())
            .map { content.getJSONObject(it) }
            .filter { it.optString("type") == "text" }
            .joinToString("\n") { it.optString("text") }

        return text.ifBlank { throw AiRequestException("Risposta vuota dal modello") }
    }

    private fun extractErrorMessage(responseText: String): String = runCatching {
        JSONObject(responseText).optJSONObject("error")?.optString("message") ?: responseText
    }.getOrDefault(responseText)

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        const val DEFAULT_MODEL = "claude-sonnet-5"
        private const val TIMEOUT_MS = 30_000
    }
}
