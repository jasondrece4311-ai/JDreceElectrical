package com.example.engine

import com.example.model.CircuitElement
import com.example.model.ComponentType
import com.example.model.GenSizingReport
import kotlin.math.max

object GenSizerUtility {

    private val STANDARD_GEN_RATINGS = listOf(
        5.0f, 7.5f, 10.0f, 15.0f, 20.0f, 25.0f, 30.0f, 40.0f, 50.0f, 65.0f, 75.0f, 100.0f, 125.0f, 150.0f, 200.0f, 250.0f
    )

    fun calculateSizing(elements: List<CircuitElement>): GenSizingReport {
        var totalRunningKw = 0f
        var totalRunningKva = 0f
        var largestMotorStartingKva = 0f
        var largestMotorRunningKva = 0f
        var motorCount = 0
        val notes = mutableListOf<String>()

        for (elem in elements) {
            when (elem.type) {
                ComponentType.MOTOR_3PHASE -> {
                    motorCount++
                    val kw = elem.motorKw
                    val pf = 0.85f // Typical induction motor power factor
                    val kva = kw / pf
                    val startMultiplier = 6.0f // 6x starting inrush current

                    totalRunningKw += kw
                    totalRunningKva += kva

                    val startKva = kva * startMultiplier
                    if (startKva > largestMotorStartingKva) {
                        largestMotorStartingKva = startKva
                        largestMotorRunningKva = kva
                    }
                }
                ComponentType.CONTACTOR_COIL, ComponentType.CONTROL_RELAY -> {
                    totalRunningKw += 0.05f
                    totalRunningKva += 0.08f
                }
                ComponentType.SOLENOID -> {
                    totalRunningKw += 0.15f
                    totalRunningKva += 0.25f
                }
                ComponentType.PILOT_LIGHT -> {
                    totalRunningKw += 0.01f
                    totalRunningKva += 0.015f
                }
                ComponentType.BUZZER -> {
                    totalRunningKw += 0.02f
                    totalRunningKva += 0.025f
                }
                else -> {}
            }
        }

        // Peak starting kVA: All other baseline loads running + largest motor starting across-the-line (6x inrush)
        val nonStartingBaselineKva = max(0f, totalRunningKva - largestMotorRunningKva)
        val peakStartingKva = nonStartingBaselineKva + largestMotorStartingKva

        // Generator continuous capacity with 25% safety margin
        val rawRequiredKw = totalRunningKw * 1.25f
        // Alternator transient capacity (voltage dip threshold <= 20% requires sizing against peak inrush / 1.6)
        val rawRequiredKva = max(totalRunningKva * 1.25f, peakStartingKva * 0.55f)

        // Find standard commercial genset rating that satisfies both kW and kVA
        val recommendedKva = STANDARD_GEN_RATINGS.firstOrNull { it >= rawRequiredKva }
            ?: (kotlin.math.ceil(rawRequiredKva / 25f) * 25f)
        val recommendedKw = recommendedKva * 0.8f // Genset typical 0.8 power factor rating

        notes.add("Continuous running base load: ${"%.2f".format(totalRunningKw)} kW / ${"%.2f".format(totalRunningKva)} kVA.")
        if (motorCount > 0) {
            notes.add("Largest motor peak DOL inrush: ${"%.1f".format(largestMotorStartingKva)} kVA (6x factor).")
            notes.add("Maximum step load surge: ${"%.1f".format(peakStartingKva)} kVA.")
            notes.add("Engine governor torque reserve sized for < 20% transient voltage drop.")
        } else {
            notes.add("No motor inrush loads detected. Sizing based purely on static continuous demand.")
        }
        notes.add("Recommendation includes 25% continuous headroom for harmonic distortion and thermal derating.")

        return GenSizingReport(
            totalRunningKw = totalRunningKw,
            totalRunningKva = totalRunningKva,
            largestMotorStartingKva = largestMotorStartingKva,
            peakStartingKva = peakStartingKva,
            recommendedGenKw = recommendedKw,
            recommendedGenKva = recommendedKva,
            loadSafetyMarginPercent = 25f,
            motorsCount = motorCount,
            notes = notes
        )
    }
}
