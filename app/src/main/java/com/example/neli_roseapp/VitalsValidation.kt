package com.example.neli_roseapp

/** Checks input format only; this does not assess whether readings are medically safe. */
object VitalsValidation {
    fun errors(bp: String, hr: String, temp: String, weight: String): Map<String, String> {
        val errors = mutableMapOf<String, String>()
        if (listOf(bp, hr, temp, weight).all { it.isBlank() }) {
            errors["bp"] = "Enter at least one reading"
        }
        if (bp.isNotBlank()) {
            val parts = bp.split("/")
            if (parts.size != 2 || parts.any { it.toIntOrNull()?.let { n -> n in 1..999 } != true }) {
                errors["bp"] = "Use systolic/diastolic, for example 120/80"
            }
        }
        if (hr.isNotBlank() && hr.toIntOrNull()?.let { it in 1..999 } != true) {
            errors["hr"] = "Enter a positive whole number"
        }
        fun validDecimal(value: String) = value.toDoubleOrNull()?.let { it.isFinite() && it > 0 && it < 1000 } == true
        if (temp.isNotBlank() && !validDecimal(temp)) errors["temp"] = "Enter a positive number"
        if (weight.isNotBlank() && !validDecimal(weight)) errors["weight"] = "Enter a positive number"
        return errors
    }
}
