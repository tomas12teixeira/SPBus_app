package com.example.spbus.data

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

class WalkingDistanceTracker(context: Context) {
    private val preferences = context.getSharedPreferences("spbus_walking", Context.MODE_PRIVATE)
    private var today = currentDay()
    private var stepsToday: Long = loadSteps()
    private var lastCumulativeSensorSteps: Long? = preferences.getLong(KEY_SENSOR_STEPS, -1L).takeIf { it >= 0L }
    private var gravity = 9.81f
    private var wasAboveStepThreshold = false
    private var lastDetectedAtMs = 0L

    val distanceTodayMeters: Int
        get() = (stepsToday * AVERAGE_STEP_LENGTH_METERS).roundToInt()

    fun recordDetectedSteps(count: Int = 1) {
        if (count <= 0) return
        resetForNewDay()
        stepsToday += count
        if (stepsToday % PERSIST_EVERY_STEPS == 0L) persist()
    }

    fun recordCumulativeSensorSteps(totalSteps: Long) {
        if (totalSteps < 0L) return
        if (currentDay() != today) {
            today = currentDay()
            stepsToday = 0L
            lastCumulativeSensorSteps = totalSteps
            persist()
            return
        }
        val previous = lastCumulativeSensorSteps
        if (previous != null && totalSteps >= previous) {
            stepsToday += totalSteps - previous
        }
        lastCumulativeSensorSteps = totalSteps
        if (stepsToday % PERSIST_EVERY_STEPS == 0L || previous == null || totalSteps < (previous ?: 0L)) persist()
    }

    fun recordAccelerometer(x: Float, y: Float, z: Float, timestampMs: Long): Boolean {
        resetForNewDay()
        val magnitude = sqrt(x * x + y * y + z * z)
        gravity = LOW_PASS_ALPHA * gravity + (1f - LOW_PASS_ALPHA) * magnitude
        val linearAcceleration = magnitude - gravity
        val aboveThreshold = linearAcceleration > STEP_THRESHOLD
        val detected = aboveThreshold && !wasAboveStepThreshold && timestampMs - lastDetectedAtMs >= MIN_STEP_INTERVAL_MS
        wasAboveStepThreshold = aboveThreshold
        if (detected) {
            lastDetectedAtMs = timestampMs
            recordDetectedSteps()
        }
        return detected
    }

    fun persist() {
        preferences.edit()
            .putString(KEY_DATE, today)
            .putLong(KEY_STEPS, stepsToday)
            .apply {
                lastCumulativeSensorSteps?.let { putLong(KEY_SENSOR_STEPS, it) }
            }
            .apply()
    }

    private fun loadSteps(): Long {
        if (preferences.getString(KEY_DATE, null) != today) return 0L
        return preferences.getLong(KEY_STEPS, 0L).coerceAtLeast(0L)
    }

    private fun resetForNewDay() {
        val date = currentDay()
        if (date == today) return
        today = date
        stepsToday = 0L
        lastCumulativeSensorSteps = null
    }

    private fun currentDay(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    companion object {
        private const val KEY_DATE = "date"
        private const val KEY_STEPS = "steps"
        private const val KEY_SENSOR_STEPS = "sensor_steps"
        private const val AVERAGE_STEP_LENGTH_METERS = 0.75
        private const val PERSIST_EVERY_STEPS = 10L
        private const val LOW_PASS_ALPHA = 0.8f
        private const val STEP_THRESHOLD = 1.25f
        private const val MIN_STEP_INTERVAL_MS = 300L
    }
}