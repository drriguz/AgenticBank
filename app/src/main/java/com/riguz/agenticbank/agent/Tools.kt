package com.riguz.agenticbank.agent

import com.google.ai.edge.litertlm.OpenApiTool
import org.json.JSONObject

class CalculateReturnTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String {
        return """
        {
            "name": "calculate_return",
            "description": "Calculate expected returns for a structured deposit investment. Amount must be in USD, minimum 10000.",
            "parameters": {
                "type": "object",
                "properties": {
                    "amount": {
                        "type": "number",
                        "description": "Investment amount in USD (minimum 10000, increments of 1000). Example: 50000 for fifty thousand dollars."
                    }
                },
                "required": ["amount"]
            }
        }
        """.trimIndent()
    }

    override fun execute(paramsJsonString: String): String {
        val params = JSONObject(paramsJsonString)
        // Handle both Int and Double types
        val amount = when (val rawAmount = params.opt("amount")) {
            is Number -> rawAmount.toDouble()
            is String -> rawAmount.replace(",", "").toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        
        android.util.Log.d("CalculateReturnTool", "Input amount: $amount (raw: ${params.opt("amount")})")
        
        if (amount < 10000) {
            return """{"error": "Minimum investment is USD 10,000"}"""
        }
        if (amount % 1000 != 0.0) {
            return """{"error": "Investment must be in increments of USD 1,000"}"""
        }

        val annualRate = 0.055
        val tenorMonths = 12
        val expectedReturn = amount * annualRate * tenorMonths / 12
        val totalPayout = amount + expectedReturn

        return """
        {
            "investment_amount": $amount,
            "annual_rate": $annualRate,
            "tenor_months": $tenorMonths,
            "expected_return": $expectedReturn,
            "total_payout": $totalPayout
        }
        """.trimIndent()
    }
}

class ShowConfirmationTool : OpenApiTool {
    override fun getToolDescriptionJsonString(): String {
        return """
        {
            "name": "show_confirmation",
            "description": "Show subscription confirmation card for a structured deposit product. Amount must be in USD, minimum 10000.",
            "parameters": {
                "type": "object",
                "properties": {
                    "amount": {
                        "type": "number",
                        "description": "Investment amount in USD (minimum 10000). Example: 200000 for two hundred thousand dollars."
                    }
                },
                "required": ["amount"]
            }
        }
        """.trimIndent()
    }

    override fun execute(paramsJsonString: String): String {
        val params = JSONObject(paramsJsonString)
        // Handle both Int and Double types
        val amount = when (val rawAmount = params.opt("amount")) {
            is Number -> rawAmount.toDouble()
            is String -> rawAmount.replace(",", "").toDoubleOrNull() ?: 0.0
            else -> 0.0
        }
        
        android.util.Log.d("ShowConfirmationTool", "Input amount: $amount (raw: ${params.opt("amount")})")
        
        if (amount < 10000) {
            return """{"error": "Minimum investment is USD 10,000"}"""
        }

        val annualRate = 0.055
        val tenorMonths = 12
        val expectedReturn = amount * annualRate * tenorMonths / 12
        val totalPayout = amount + expectedReturn

        return """
        {
            "product_id": "MALI260710BLU02BD",
            "product_name": "MALI - Non Principal Protected - BD - 1",
            "investment_amount": $amount,
            "tenor_months": $tenorMonths,
            "risk_rating": 5,
            "principal_protection": "100%",
            "expected_return": $expectedReturn,
            "total_payout": $totalPayout
        }
        """.trimIndent()
    }
}
