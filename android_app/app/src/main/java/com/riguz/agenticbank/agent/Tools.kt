package com.riguz.agenticbank.agent

import com.google.ai.edge.litertlm.OpenApiTool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

private const val BASE_URL = "http://192.168.31.66:8080"
private const val CARD_NUMBER = "4242424242424242"

private val httpClient = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

private fun apiGet(path: String): String {
    val request = Request.Builder().url("$BASE_URL$path").get().build()
    return try {
        httpClient.newCall(request).execute().body?.string()
            ?: """{"success":false,"error":"Empty response"}"""
    } catch (e: Exception) {
        """{"success":false,"error":"${e.message}"}"""
    }
}

private fun apiPost(path: String, body: String): String {
    val request = Request.Builder()
        .url("$BASE_URL$path")
        .post(body.toRequestBody(JSON_MEDIA))
        .build()
    return try {
        httpClient.newCall(request).execute().body?.string()
            ?: """{"success":false,"error":"Empty response"}"""
    } catch (e: Exception) {
        """{"success":false,"error":"${e.message}"}"""
    }
}

private fun apiPatch(path: String, body: String): String {
    val request = Request.Builder()
        .url("$BASE_URL$path")
        .patch(body.toRequestBody(JSON_MEDIA))
        .build()
    return try {
        httpClient.newCall(request).execute().body?.string()
            ?: """{"success":false,"error":"Empty response"}"""
    } catch (e: Exception) {
        """{"success":false,"error":"${e.message}"}"""
    }
}

class CheckBalanceTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = """
    {
        "name": "check_balance",
        "description": "Check the current account balance. Returns the account name and balance.",
        "parameters": {
            "type": "object",
            "properties": {},
            "required": []
        }
    }
    """.trimIndent()

    override fun execute(paramsJsonString: String): String {
        val resp = apiGet("/api/accounts/$CARD_NUMBER")
        return try {
            val json = JSONObject(resp)
            if (json.optBoolean("success", false)) {
                val data = json.getJSONObject("data")
                val balance = data.getDouble("balance")
                val name = data.getString("name")
                """{"balance":$balance,"name":"$name"}"""
            } else {
                """{"error":"${json.optString("error", "Unknown error")}"}"""
            }
        } catch (e: Exception) {
            """{"error":"Failed to parse response: ${e.message}"}"""
        }
    }
}

class TransactionHistoryTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = """
    {
        "name": "transaction_history",
        "description": "View recent transaction history. Returns a list of transactions with type, amount, and timestamp.",
        "parameters": {
            "type": "object",
            "properties": {},
            "required": []
        }
    }
    """.trimIndent()

    override fun execute(paramsJsonString: String): String {
        val resp = apiGet("/api/accounts/$CARD_NUMBER/transactions")
        return try {
            val json = JSONObject(resp)
            if (json.optBoolean("success", false)) {
                val arr = json.getJSONArray("data")
                val txns = StringBuilder("[")
                for (i in 0 until arr.length()) {
                    if (i > 0) txns.append(",")
                    val txn = arr.getJSONObject(i)
                    txns.append("""{"type":"${txn.getString("type")}","amount":${txn.getDouble("amount")},"timestamp":"${txn.getString("timestamp")}"}""")
                }
                txns.append("]")
                """{"transactions":$txns}"""
            } else {
                """{"error":"${json.optString("error", "Unknown error")}"}"""
            }
        } catch (e: Exception) {
            """{"error":"Failed to parse response: ${e.message}"}"""
        }
    }
}

class DepositTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = """
    {
        "name": "deposit",
        "description": "Deposit money into the account. The amount MUST be explicitly provided by the user. NEVER guess or use a default value. If the user did not specify an amount, do NOT call this tool — ask the user first.",
        "parameters": {
            "type": "object",
            "properties": {
                "amount": {
                    "type": "number",
                    "description": "Exact amount to deposit in USD, as specified by the user. Must be a positive number, max 10000. Do NOT use a default value."
                }
            },
            "required": ["amount"]
        }
    }
    """.trimIndent()

    override fun execute(paramsJsonString: String): String {
        val params = JSONObject(paramsJsonString)
        val amount = params.optDouble("amount", 0.0)
        if (amount <= 0) {
            return """{"error":"Amount must be a positive number"}"""
        }
        if (amount > 10000) {
            return """{"error":"Maximum deposit amount is $10,000"}"""
        }
        return """{"operation":"deposit","amount":$amount,"status":"pending_confirmation"}"""
    }
}

class WithdrawTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = """
    {
        "name": "withdraw",
        "description": "Withdraw money from the account. The amount MUST be explicitly provided by the user. NEVER guess or use a default value. If the user did not specify an amount, do NOT call this tool — ask the user first.",
        "parameters": {
            "type": "object",
            "properties": {
                "amount": {
                    "type": "number",
                    "description": "Exact amount to withdraw in USD, as specified by the user. Must be a positive number, max 10000. Do NOT use a default value."
                }
            },
            "required": ["amount"]
        }
    }
    """.trimIndent()

    override fun execute(paramsJsonString: String): String {
        val params = JSONObject(paramsJsonString)
        val amount = params.optDouble("amount", 0.0)
        if (amount <= 0) {
            return """{"error":"Amount must be a positive number"}"""
        }
        if (amount > 10000) {
            return """{"error":"Maximum withdrawal amount is $10,000 per transaction"}"""
        }
        return """{"operation":"withdraw","amount":$amount,"status":"pending_confirmation"}"""
    }
}

class TransferTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = """
    {
        "name": "transfer",
        "description": "Transfer money to another account by card number. Both amount and to_card_number MUST be explicitly provided by the user. NEVER guess or invent values. If the user did not provide both the exact amount and the exact destination card number, do NOT call this tool — ask the user for the missing information first.",
        "parameters": {
            "type": "object",
            "properties": {
                "amount": {
                    "type": "number",
                    "description": "Exact amount to transfer in USD, as specified by the user. Must be a positive number, max 10000. Do NOT use a default value."
                },
                "to_card_number": {
                    "type": "string",
                    "description": "The exact destination card number (13-19 digits, no spaces) as provided by the user. Do NOT make up a card number."
                }
            },
            "required": ["amount", "to_card_number"]
        }
    }
    """.trimIndent()

    override fun execute(paramsJsonString: String): String {
        val params = JSONObject(paramsJsonString)
        val amount = params.optDouble("amount", 0.0)
        val toCardNumber = params.optString("to_card_number", "")
        if (amount <= 0) {
            return """{"error":"Amount must be a positive number"}"""
        }
        if (amount > 10000) {
            return """{"error":"Maximum transfer amount is $10,000"}"""
        }
        if (toCardNumber.isBlank() || !toCardNumber.matches(Regex("^\\d{13,19}$"))) {
            return """{"error":"Invalid card number. Must be 13-19 digits."}"""
        }
        if (toCardNumber == CARD_NUMBER) {
            return """{"error":"Cannot transfer to your own card"}"""
        }
        return """{"operation":"transfer","amount":$amount,"to_card_number":"$toCardNumber","status":"pending_confirmation"}"""
    }
}

class ChangePasswordTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String = """
    {
        "name": "change_password",
        "description": "Initiate a password change for the account. A dialog will be shown for the user to enter their passwords securely. Do NOT ask the user to provide passwords in the chat. Simply call this tool when the user wants to change their password.",
        "parameters": {
            "type": "object",
            "properties": {},
            "required": []
        }
    }
    """.trimIndent()

    override fun execute(paramsJsonString: String): String {
        return """{"operation":"change_password","status":"pending_confirmation"}"""
    }
}
