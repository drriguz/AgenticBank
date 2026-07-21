package com.riguz.agenticbank.agent

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

sealed class ToolResult {
    data class CalculationResult(
        val investmentAmount: Double,
        val annualRate: Double,
        val tenorMonths: Int,
        val expectedReturn: Double,
        val maturityDate: String,
        val totalPayout: Double,
    ) : ToolResult()

    data class SubscriptionConfirmation(
        val productId: String,
        val productName: String,
        val investmentAmount: Double,
        val tenorMonths: Int,
        val riskRating: Int,
        val principalProtection: String,
        val expectedReturn: Double,
        val maturityDate: String,
        val totalPayout: Double,
    ) : ToolResult()

    data class ToolMessage(val message: String) : ToolResult()
    data class Error(val message: String) : ToolResult()
    object None : ToolResult()
}

object ToolExecutor {

    private const val ANNUAL_RATE = 0.055 // 5.5% example rate from termsheet

    fun parseToolCall(text: String): Pair<String, JSONObject>? {
        // Try ```json ... ``` format
        val jsonBlockPattern = """```json\s*(\{.*?\})\s*```""".toRegex(setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
        val jsonBlockMatch = jsonBlockPattern.find(text)
        if (jsonBlockMatch != null) {
            val result = parseJsonToolCall(jsonBlockMatch.groupValues[1])
            if (result != null) return result
        }

        // Try <|tool_call|> format - extract JSON after it
        val toolCallPattern = """<\|tool_call\|>[\s\S]*?(\{[\s\S]*\})""".toRegex()
        val toolCallMatch = toolCallPattern.find(text)
        if (toolCallMatch != null) {
            val result = parseJsonToolCall(toolCallMatch.groupValues[1])
            if (result != null) return result
        }

        // Try to find any JSON with "tool" key in the text
        val jsonPattern = """\{[^{}]*"tool"\s*:\s*"[^"]*"[^{}]*\}""".toRegex()
        val jsonMatch = jsonPattern.find(text)
        if (jsonMatch != null) {
            val result = parseJsonToolCall(jsonMatch.value)
            if (result != null) return result
        }

        // If we see <|tool_call|> but no JSON, try to infer from context
        if (text.contains("<|tool_call|>") || text.contains("tool_call")) {
            // Try to extract amount from text
            val amountPattern = """(\d[\d,]*)\s*(?:美元|USD|万)""".toRegex()
            val amountMatch = amountPattern.find(text)
            if (amountMatch != null) {
                val amountStr = amountMatch.groupValues[1].replace(",", "")
                val amount = amountStr.toDoubleOrNull()
                if (amount != null) {
                    // Determine tool based on context
                    val tool = if (text.contains("subscribe") || text.contains("purchase") || text.contains("confirm") || text.contains("认购")) {
                        "show_confirmation"
                    } else {
                        "calculate_return"
                    }
                    return tool to JSONObject().put("amount", amount)
                }
            }
        }

        return null
    }

    private fun parseJsonToolCall(jsonStr: String): Pair<String, JSONObject>? {
        return try {
            val json = JSONObject(jsonStr)
            val tool = json.getString("tool")
            val params = json.optJSONObject("params") ?: JSONObject()
            tool to params
        } catch (e: Exception) {
            null
        }
    }

    fun execute(tool: String, params: JSONObject): ToolResult {
        return when (tool) {
            "calculate_return" -> calculateReturn(params)
            "show_confirmation" -> showConfirmation(params)
            else -> ToolResult.Error("Unknown tool: $tool")
        }
    }

    private fun calculateReturn(params: JSONObject): ToolResult {
        val amount = params.optDouble("amount", 0.0)
        if (amount < 50000) {
            return ToolResult.Error("Minimum investment is USD 50,000")
        }
        if (amount % 10000 != 0.0) {
            return ToolResult.Error("Investment must be in increments of USD 10,000")
        }

        val tenorMonths = params.optInt("tenor_months", 12)
        val rate = params.optDouble("annual_rate", ANNUAL_RATE)
        val expectedReturn = amount * rate * tenorMonths / 12
        val totalPayout = amount + expectedReturn

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, tenorMonths)
        val maturityDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        return ToolResult.CalculationResult(
            investmentAmount = amount,
            annualRate = rate,
            tenorMonths = tenorMonths,
            expectedReturn = expectedReturn,
            maturityDate = maturityDate,
            totalPayout = totalPayout,
        )
    }

    private fun showConfirmation(params: JSONObject): ToolResult {
        val amount = params.optDouble("amount", 0.0)
        if (amount < 10000) {
            return ToolResult.Error("Minimum investment is USD 10,000")
        }

        val tenorMonths = params.optInt("tenor_months", 12)
        val rate = params.optDouble("annual_rate", ANNUAL_RATE)
        val expectedReturn = amount * rate * tenorMonths / 12
        val totalPayout = amount + expectedReturn

        val calendar = Calendar.getInstance()
        calendar.add(Calendar.MONTH, tenorMonths)
        val maturityDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.time)

        return ToolResult.SubscriptionConfirmation(
            productId = "MALI260710BLU02BD",
            productName = "Non Principal Protected - BD - 1",
            investmentAmount = amount,
            tenorMonths = tenorMonths,
            riskRating = 5,
            principalProtection = "100%",
            expectedReturn = expectedReturn,
            maturityDate = maturityDate,
            totalPayout = totalPayout,
        )
    }

    fun formatCalculationResult(result: ToolResult.CalculationResult): String {
        return """
            |📊 Return Calculation
            |━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            |Investment Amount: USD %,.0f
            |Annual Rate: %.1f%%
            |Tenor: %d Months
            |Expected Return: USD %,.2f
            |Maturity Date: %s
            |Total Payout: USD %,.2f
        """.trimMargin().format(
            result.investmentAmount,
            result.annualRate * 100,
            result.tenorMonths,
            result.expectedReturn,
            result.maturityDate,
            result.totalPayout,
        )
    }
}
