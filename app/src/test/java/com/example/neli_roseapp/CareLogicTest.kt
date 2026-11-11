package com.example.neli_roseapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CareLogicTest {

    // ---- vitals

    @Test
    fun vitals_acceptsACompleteReading() {
        val check = VitalsValidator.check("120/80", "72", "36.6", "70")
        assertTrue(check.isValid)
        val values = check.values!!
        assertEquals(120, values.systolic)
        assertEquals(80, values.diastolic)
        assertEquals(72, values.heartRate)
        assertEquals(36.6, values.temperatureC!!, 0.0001)
        assertEquals(70.0, values.weightKg!!, 0.0001)
    }

    @Test
    fun vitals_acceptsOnlyOneField() {
        val check = VitalsValidator.check(" 118 / 76 ", "", "", "")
        assertTrue(check.isValid)
        assertEquals(118, check.values!!.systolic)
        assertEquals(76, check.values!!.diastolic)
        assertNull(check.values!!.heartRate)
        assertNull(check.values!!.temperatureC)
        assertNull(check.values!!.weightKg)
    }

    @Test
    fun vitals_rejectsAnEmptyForm() {
        val check = VitalsValidator.check("", " ", "", "")
        assertFalse(check.isValid)
        assertNotNull(check.formError)
    }

    @Test
    fun vitals_rejectsBloodPressureThatIsNotTwoNumbers() {
        assertNotNull(VitalsValidator.check("120", "", "", "").bloodPressureError)
        assertNotNull(VitalsValidator.check("120/80 mmHg", "", "", "").bloodPressureError)
        assertNotNull(VitalsValidator.check("normal", "", "", "").bloodPressureError)
    }

    @Test
    fun vitals_rejectsBloodPressureOutOfRangeOrReversed() {
        assertNotNull(VitalsValidator.check("300/80", "", "", "").bloodPressureError)
        assertNotNull(VitalsValidator.check("120/20", "", "", "").bloodPressureError)
        assertNotNull(VitalsValidator.check("80/120", "", "", "").bloodPressureError)
        assertNotNull(VitalsValidator.check("90/90", "", "", "").bloodPressureError)
    }

    @Test
    fun vitals_rejectsHeartRateOutOfRangeOrNotANumber() {
        assertNotNull(VitalsValidator.check("", "10", "", "").heartRateError)
        assertNotNull(VitalsValidator.check("", "400", "", "").heartRateError)
        assertNotNull(VitalsValidator.check("", "72 bpm", "", "").heartRateError)
        assertNull(VitalsValidator.check("", "72", "", "").heartRateError)
    }

    @Test
    fun vitals_acceptsACommaAsTheDecimalSign() {
        val check = VitalsValidator.check("", "", "36,6", "70,5")
        assertTrue(check.isValid)
        assertEquals(36.6, check.values!!.temperatureC!!, 0.0001)
        assertEquals(70.5, check.values!!.weightKg!!, 0.0001)
    }

    @Test
    fun vitals_rejectsTemperatureAndWeightOutOfRange() {
        assertNotNull(VitalsValidator.check("", "", "50", "").temperatureError)
        assertNotNull(VitalsValidator.check("", "", "12", "").temperatureError)
        assertNotNull(VitalsValidator.check("", "", "", "500").weightError)
        assertNotNull(VitalsValidator.check("", "", "", "5").weightError)
    }

    @Test
    fun vitals_reportsEveryWrongFieldAtOnce() {
        val check = VitalsValidator.check("abc", "abc", "abc", "abc")
        assertFalse(check.isValid)
        assertNotNull(check.bloodPressureError)
        assertNotNull(check.heartRateError)
        assertNotNull(check.temperatureError)
        assertNotNull(check.weightError)
    }

    @Test
    fun vitals_describesAReadingWithUnits() {
        assertEquals(
            "120/80 mmHg, 72 bpm, 36.6 °C, 70 kg",
            VitalsValidator.describe(120, 80, 72, 36.6, 70.0)
        )
        assertEquals("72 bpm", VitalsValidator.describe(null, null, 72, null, null))
        assertEquals("", VitalsValidator.describe(null, null, null, null, null))
    }

    @Test
    fun vitals_readsOldFreeTextValues() {
        val values = VitalsValidator.parseLegacy("120/80", "72 bpm", "36.6 °C", "70 kg")
        assertEquals(120, values.systolic)
        assertEquals(80, values.diastolic)
        assertEquals(72, values.heartRate)
        assertEquals(36.6, values.temperatureC!!, 0.0001)
        assertEquals(70.0, values.weightKg!!, 0.0001)
    }

    @Test
    fun vitals_leavesOutOldValuesThatAreNotReadings() {
        assertTrue(VitalsValidator.parseLegacy("", "", "", "").isEmpty)
        assertTrue(VitalsValidator.parseLegacy("fine", "ok", "warm", "heavy").isEmpty)
        assertTrue(VitalsValidator.parseLegacy("999/999", "5", "99", "1").isEmpty)
    }

    // ---- times and dates

    @Test
    fun times_findTheTimeInsideOldText() {
        assertEquals(480, CareTimes.minutesOfDay("Morning 08:00"))
        assertEquals(870, CareTimes.minutesOfDay("14:30"))
        assertEquals(0, CareTimes.minutesOfDay("00:00"))
        assertNull(CareTimes.minutesOfDay("after lunch"))
        assertNull(CareTimes.minutesOfDay("25:00"))
        assertNull(CareTimes.minutesOfDay("10:75"))
    }

    @Test
    fun times_formatAndValidate() {
        assertEquals("09:05", CareTimes.format(9, 5))
        assertEquals("23:59", CareTimes.format(23, 59))
        assertTrue(CareTimes.isValid("09:05"))
        assertFalse(CareTimes.isValid("9:05"))
        assertFalse(CareTimes.isValid("24:00"))
    }

    @Test
    fun dates_moveAcrossMonthAndYearEnds() {
        assertEquals("2026-11-12", CareDates.plusDays("2026-11-11", 1))
        assertEquals("2026-10-31", CareDates.plusDays("2026-11-01", -1))
        assertEquals("2027-01-01", CareDates.plusDays("2026-12-31", 1))
        assertEquals("2026-03-05", CareDates.of(2026, 3, 5))
    }

    @Test
    fun dates_oldRecordsSortBeforeEveryRealDay() {
        assertTrue(CareDates.LEGACY_DATE < "2000-01-01")
        assertNull(CareDates.parse(CareDates.LEGACY_DATE))
    }

    // ---- next reminder

    @Test
    fun reminder_showsTheNextItemWithTheTimeLeft() {
        val reminder = ReminderCalculator.next(
            listOf(
                PendingItem("Dinner", "18:00", ReminderCalculator.KIND_SCHEDULE),
                PendingItem("Vitamin D", "14:00", ReminderCalculator.KIND_MEDICATION)
            ),
            nowMinutes = 12 * 60,
            hasAnyItems = true
        )
        assertEquals("Vitamin D", reminder.title)
        assertEquals("Medication at 14:00", reminder.detail)
        assertEquals("in 2 h", reminder.badge)
    }

    @Test
    fun reminder_putsSomethingOverdueFirst() {
        val reminder = ReminderCalculator.next(
            listOf(
                PendingItem("Dinner", "18:00", ReminderCalculator.KIND_SCHEDULE),
                PendingItem("Metformin 500mg", "Morning 08:00", ReminderCalculator.KIND_MEDICATION)
            ),
            nowMinutes = 12 * 60,
            hasAnyItems = true
        )
        assertEquals("Metformin 500mg", reminder.title)
        assertEquals("Due", reminder.badge)
        assertEquals("Medication, was due at 08:00", reminder.detail)
    }

    @Test
    fun reminder_countsTheOtherOverdueItems() {
        val reminder = ReminderCalculator.next(
            listOf(
                PendingItem("Breakfast", "08:00", ReminderCalculator.KIND_SCHEDULE),
                PendingItem("Exercise", "10:00", ReminderCalculator.KIND_SCHEDULE),
                PendingItem("Tablets", "07:00", ReminderCalculator.KIND_MEDICATION)
            ),
            nowMinutes = 12 * 60,
            hasAnyItems = true
        )
        assertEquals("Tablets", reminder.title)
        assertEquals("Medication, was due at 07:00 (2 more waiting)", reminder.detail)
    }

    @Test
    fun reminder_saysWhenNothingIsLeftOrNothingExists() {
        val done = ReminderCalculator.next(emptyList(), nowMinutes = 600, hasAnyItems = true)
        assertEquals("Nothing else is due today", done.title)
        val none = ReminderCalculator.next(emptyList(), nowMinutes = 600, hasAnyItems = false)
        assertEquals("No reminders yet", none.title)
    }

    @Test
    fun reminder_ignoresItemsWithoutAReadableTime() {
        val reminder = ReminderCalculator.next(
            listOf(PendingItem("Eye drops", "when needed", ReminderCalculator.KIND_MEDICATION)),
            nowMinutes = 600,
            hasAnyItems = true
        )
        assertEquals("Nothing else is due today", reminder.title)
    }

    @Test
    fun reminder_countdownWording() {
        assertEquals("Now", ReminderCalculator.countdown(0))
        assertEquals("in 45 min", ReminderCalculator.countdown(45))
        assertEquals("in 2 h", ReminderCalculator.countdown(120))
        assertEquals("in 2 h 15 min", ReminderCalculator.countdown(135))
    }

    // ---- removing repeating items, phone numbers

    @Test
    fun removal_keepsEarlierDaysOfARepeatingItem() {
        assertEquals(RemovalPlan.STOP_FROM_TODAY, CareRules.removalPlan(true, "2026-11-01", "2026-11-11"))
        assertEquals(RemovalPlan.DELETE_COMPLETELY, CareRules.removalPlan(true, "2026-11-11", "2026-11-11"))
        assertEquals(RemovalPlan.DELETE_COMPLETELY, CareRules.removalPlan(true, "2026-11-20", "2026-11-11"))
        assertEquals(RemovalPlan.DELETE_COMPLETELY, CareRules.removalPlan(false, "2026-11-01", "2026-11-11"))
    }

    @Test
    fun phone_cleaningAndChecking() {
        assertEquals("+27821234567", CareRules.cleanPhone("+27 82 123-4567"))
        assertTrue(CareRules.isPlausiblePhone("+27 82 123 4567"))
        assertFalse(CareRules.isPlausiblePhone("12345"))
        assertFalse(CareRules.isPlausiblePhone("call me"))
    }
}
