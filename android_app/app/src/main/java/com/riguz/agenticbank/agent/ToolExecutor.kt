package com.riguz.agenticbank.agent

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private const val API_BASE = "http://192.168.31.66:8080"
private const val DEFAULT_CARD_NUMBER = "4242424242424242"

private val client = OkHttpClient.Builder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build()

private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

sealed class ToolResult {
    data class Balance(val name: String, val balance: Double) : ToolResult()
    data class TransactionHistory(val transactions: List<TransactionItem>) : ToolResult()
    data class ConfirmRequest(
        val operation: String,
        val params: Map<String, Any>,
    ) : ToolResult()

    data class OperationSuccess(
        val operation: String,
        val message: String,
        val newBalance: Double? = null,
    ) : ToolResult()

    data class ValidationError(val message: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
    object None : ToolResult()
}

data class TransactionItem(
    val type: String,
    val amount: Double,
    val timestamp: String,
)

object ToolExecutor {

    fun execute(tool: String, params: JSONObject): ToolResult {
        return when (tool) {
            "check_balance" -> checkBalance()
            "transaction_history" -> transactionHistory(params)
            "deposit" -> {
                val amount = params.optDouble("amount", 0.0)
                when {
                    amount <= 0 -> ToolResult.ValidationError("Amount is required but not provided")
                    amount > 10000 -> ToolResult.ValidationError("Maximum deposit amount is $10,000")
                    else -> ToolResult.ConfirmRequest("deposit", mapOf("amount" to amount))
                }
            }
            "withdraw" -> {
                val amount = params.optDouble("amount", 0.0)
                when {
                    amount <= 0 -> ToolResult.ValidationError("Amount is required but not provided")
                    amount > 10000 -> ToolResult.ValidationError("Maximum withdrawal amount is $10,000 per transaction")
                    else -> ToolResult.ConfirmRequest("withdraw", mapOf("amount" to amount))
                }
            }
            "transfer" -> {
                val amount = params.optDouble("amount", 0.0)
                val toCardNumber = params.optString("to_card_number", "")
                when {
                    amount <= 0 && toCardNumber.isBlank() ->
                        ToolResult.ValidationError("Amount and destination card number are both required")
                    amount <= 0 ->
                        ToolResult.ValidationError("Amount is required. Destination card number received: $toCardNumber")
                    amount > 10000 ->
                        ToolResult.ValidationError("Maximum transfer amount is $10,000")
                    toCardNumber.isBlank() ->
                        ToolResult.ValidationError("Destination card number is required. Amount received: $amount")
                    !toCardNumber.matches(Regex("^\\d{13,19}$")) ->
                        ToolResult.ValidationError("Invalid card number format. Must be 13-19 digits without spaces.")
                    toCardNumber == DEFAULT_CARD_NUMBER ->
                        ToolResult.ValidationError("Cannot transfer to your own card")
                    else -> ToolResult.ConfirmRequest(
                        "transfer",
                        mapOf("amount" to amount, "to_card_number" to toCardNumber),
                    )
                }
            }
            "change_password" -> {
                ToolResult.ConfirmRequest("change_password", emptyMap())
            }
            else -> ToolResult.Error("Unknown tool: $tool")
        }
    }

    fun executeConfirmed(operation: String, params: Map<String, Any>): ToolResult {
        return when (operation) {
            "deposit" -> {
                val amount = params["amount"] as Double
                val body = JSONObject().put("cardNumber", DEFAULT_CARD_NUMBER).put("amount", amount).toString()
                apiPost("/api/transactions/deposit", body, "deposit")
            }
            "withdraw" -> {
                val amount = params["amount"] as Double
                val body = JSONObject().put("cardNumber", DEFAULT_CARD_NUMBER).put("amount", amount).toString()
                apiPost("/api/transactions/withdraw", body, "withdraw")
            }
            "transfer" -> {
                val amount = params["amount"] as Double
                val toCardNumber = params["to_card_number"] as String
                val body = JSONObject()
                    .put("fromCardNumber", DEFAULT_CARD_NUMBER)
                    .put("toCardNumber", toCardNumber)
                    .put("amount", amount)
                    .toString()
                apiPost("/api/transactions/transfer", body, "transfer")
            }
            "change_password" -> {
                val oldPwd = params["old_password"] as String
                val newPwd = params["new_password"] as String
                if (oldPwd.isBlank()) return ToolResult.Error("Current password is required")
                if (newPwd.isBlank()) return ToolResult.Error("New password is required")
                if (newPwd.length < 4) return ToolResult.Error("New password must be at least 4 characters")
                val body = JSONObject()
                    .put("cardNumber", DEFAULT_CARD_NUMBER)
                    .put("oldPassword", oldPwd)
                    .put("newPassword", newPwd)
                    .toString()
                apiPatch("/api/accounts/$DEFAULT_CARD_NUMBER/password", body)
            }
            else -> ToolResult.Error("Unknown operation: $operation")
        }
    }

    private fun checkBalance(): ToolResult {
        return try {
            val resp = get("/api/accounts/$DEFAULT_CARD_NUMBER")
            android.util.Log.d("ToolExecutor", "Balance response: $resp")
            val json = JSONObject(resp)
            if (json.optBoolean("success", false)) {
                val data = json.getJSONObject("data")
                ToolResult.Balance(
                    name = data.getString("name"),
                    balance = data.getDouble("balance"),
                )
            } else {
                ToolResult.Error(json.optString("error", "Unknown error"))
            }
        } catch (e: Exception) {
            android.util.Log.e("ToolExecutor", "checkBalance failed", e)
            ToolResult.Error("Failed to check balance: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun transactionHistory(params: JSONObject): ToolResult {
        return try {
            val startDate = params.optString("start_date", "")
            val endDate = params.optString("end_date", "")

            var path = "/api/accounts/$DEFAULT_CARD_NUMBER/transactions"
            val queryParams = mutableListOf<String>()
            if (startDate.isNotBlank()) queryParams.add("startDate=$startDate")
            if (endDate.isNotBlank()) queryParams.add("endDate=$endDate")
            if (queryParams.isNotEmpty()) path += "?" + queryParams.joinToString("&")

            val resp = get(path)
            val json = JSONObject(resp)
            if (json.optBoolean("success", false)) {
                val arr = json.getJSONArray("data")
                val txns = mutableListOf<TransactionItem>()
                for (i in 0 until arr.length()) {
                    val txn = arr.getJSONObject(i)
                    txns.add(
                        TransactionItem(
                            type = txn.getString("type"),
                            amount = txn.getDouble("amount"),
                            timestamp = txn.getString("timestamp"),
                        )
                    )
                }
                ToolResult.TransactionHistory(txns)
            } else {
                ToolResult.Error(json.optString("error", "Unknown error"))
            }
        } catch (e: Exception) {
            ToolResult.Error("Failed to get history: ${e.message}")
        }
    }

    private fun apiPost(path: String, body: String, operation: String): ToolResult {
        return try {
            val request = Request.Builder()
                .url("$API_BASE$path")
                .post(body.toRequestBody(JSON_MEDIA))
                .build()
            val resp = client.newCall(request).execute().body?.string()
                ?: return ToolResult.Error("Empty response from server")
            val json = JSONObject(resp)
            if (json.optBoolean("success", false)) {
                val data = json.get("data")
                val newBalance = when (data) {
                    is JSONObject -> data.optDouble("balance", Double.NaN).takeIf { !it.isNaN() }
                    is org.json.JSONArray -> {
                        if (data.length() > 0) data.getJSONObject(0).optDouble("balance", Double.NaN).takeIf { !it.isNaN() }
                        else null
                    }
                    else -> null
                }
                val opName = when (operation) {
                    "deposit" -> "Deposit"
                    "withdraw" -> "Withdrawal"
                    "transfer" -> "Transfer"
                    else -> operation
                }
                ToolResult.OperationSuccess(
                    operation = operation,
                    message = "$opName completed successfully",
                    newBalance = newBalance,
                )
            } else {
                ToolResult.Error(json.optString("error", "Operation failed"))
            }
        } catch (e: Exception) {
            ToolResult.Error("Request failed: ${e.message}")
        }
    }

    private fun apiPatch(path: String, body: String): ToolResult {
        return try {
            val request = Request.Builder()
                .url("$API_BASE$path")
                .patch(body.toRequestBody(JSON_MEDIA))
                .build()
            val resp = client.newCall(request).execute().body?.string()
                ?: return ToolResult.Error("Empty response from server")
            val json = JSONObject(resp)
            if (json.optBoolean("success", false)) {
                ToolResult.OperationSuccess(
                    operation = "change_password",
                    message = "Password changed successfully",
                )
            } else {
                ToolResult.Error(json.optString("error", "Operation failed"))
            }
        } catch (e: Exception) {
            ToolResult.Error("Request failed: ${e.message}")
        }
    }

    private fun get(path: String): String {
        val url = "$API_BASE$path"
        android.util.Log.d("ToolExecutor", "GET $url")
        val request = Request.Builder().url(url).get().build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()
        android.util.Log.d("ToolExecutor", "Response ${response.code}: $body")
        return body ?: """{"success":false,"error":"Empty response"}"""
    }

    private val MASKED_CARD = DEFAULT_CARD_NUMBER.take(4) + " **** **** " + DEFAULT_CARD_NUMBER.takeLast(4)

    fun confirmDescription(operation: String, params: Map<String, Any>): String {
        return when (operation) {
            "deposit" -> "Deposit USD %,.2f to Card $MASKED_CARD".format(params["amount"] as Double)
            "withdraw" -> "Withdraw USD %,.2f from Card $MASKED_CARD".format(params["amount"] as Double)
            "transfer" -> {
                val cardNum = params["to_card_number"] as String
                val masked = cardNum.take(4) + " **** **** " + cardNum.takeLast(4)
                "Transfer USD %,.2f\nFrom: $MASKED_CARD\nTo: $masked".format(params["amount"] as Double)
            }
            "change_password" -> "Change password for Card $MASKED_CARD"
            else -> operation
        }
    }

    fun toolResponseJson(result: ToolResult): String {
        return when (result) {
            is ToolResult.Balance -> """{"balance":${result.balance},"name":"${result.name}"}"""
            is ToolResult.TransactionHistory -> {
                val arr = result.transactions.joinToString(",") {
                    """{"type":"${it.type}","amount":${it.amount},"timestamp":"${it.timestamp}"}"""
                }
                """{"transactions":[$arr]}"""
            }
            is ToolResult.OperationSuccess -> {
                val extra = if (result.newBalance != null) ""","new_balance":${result.newBalance}""" else ""
                """{"status":"success","message":"${result.message}"$extra}"""
            }
            is ToolResult.ValidationError -> """{"error":"${result.message}"}"""
            is ToolResult.Error -> """{"error":"${result.message}"}"""
            else -> """{"status":"ok"}"""
        }
    }
}
