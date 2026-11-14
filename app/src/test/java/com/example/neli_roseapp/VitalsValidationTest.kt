package com.example.neli_roseapp

import org.junit.Assert.*
import org.junit.Test

class VitalsValidationTest {
    @Test fun acceptsValidAndPartialReadings() {
        assertTrue(VitalsValidation.errors("120/80", "72", "36.5", "70").isEmpty())
        assertTrue(VitalsValidation.errors("", "72", "", "").isEmpty())
    }
    @Test fun rejectsEmptyAndMalformedReadings() {
        assertTrue(VitalsValidation.errors("", "", "", "").isNotEmpty())
        val errors = VitalsValidation.errors("120", "-1", "NaN", "0")
        assertEquals(setOf("bp", "hr", "temp", "weight"), errors.keys)
    }
    @Test fun acceptsUnusualValuesWithoutMakingClinicalJudgments() {
        assertTrue(VitalsValidation.errors("200/110", "150", "40", "100").isEmpty())
    }
}
