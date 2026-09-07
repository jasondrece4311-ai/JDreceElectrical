package com.example.model

import java.util.UUID

object ComponentFactory {

    fun createComponent(
        type: ComponentType,
        x: Float,
        y: Float,
        customTag: String? = null
    ): CircuitElement {
        val id = UUID.randomUUID().toString().take(8)
        val tag = customTag ?: generateDefaultTag(type)

        val width = when (type) {
            ComponentType.BREAKER_3P, ComponentType.THERMAL_OVERLOAD, ComponentType.MOTOR_3PHASE, ComponentType.GENERATOR -> 130f
            ComponentType.POWER_3P -> 120f
            ComponentType.SELECTOR_3POS -> 110f
            else -> 100f
        }

        val height = when (type) {
            ComponentType.MOTOR_3PHASE, ComponentType.GENERATOR -> 110f
            ComponentType.BREAKER_3P, ComponentType.THERMAL_OVERLOAD -> 95f
            else -> 80f
        }

        val terminals = createTerminalsForType(type, width, height)

        val defaultClosed = when (type) {
            ComponentType.ESTOP, ComponentType.PUSHBUTTON_NC, ComponentType.AUX_CONTACT_NC -> true
            ComponentType.BREAKER_1P, ComponentType.BREAKER_3P -> true // standard closed breaker ready
            else -> false
        }

        return CircuitElement(
            id = id,
            type = type,
            tag = tag,
            x = x,
            y = y,
            width = width,
            height = height,
            terminals = terminals,
            isClosed = defaultClosed,
            isLatched = (type == ComponentType.ESTOP),
            ratingAmps = when (type) {
                ComponentType.BREAKER_3P -> 32f
                ComponentType.BREAKER_1P -> 16f
                ComponentType.FUSE -> 10f
                else -> 16f
            },
            tripCurrentAmps = 18f,
            nominalVoltage = if (type == ComponentType.POWER_DC || type == ComponentType.SENSOR_PROXIMITY) 24f else 230f,
            genRatedKw = 25f,
            genRatedKva = 31.25f,
            isGenRunning = true,
            motorKw = 5.5f,
            motorRunningCurrentAmps = 11.0f
        )
    }

    private fun generateDefaultTag(type: ComponentType): String {
        return when (type) {
            ComponentType.POWER_3P -> "PWR-3Φ"
            ComponentType.POWER_1P -> "PWR-1Φ"
            ComponentType.POWER_DC -> "DC-24V"
            ComponentType.GENERATOR -> "GEN-1"
            ComponentType.BREAKER_1P -> "QF1"
            ComponentType.BREAKER_3P -> "QF-3P"
            ComponentType.FUSE -> "FU1"
            ComponentType.ESTOP -> "SB0 (E-Stop)"
            ComponentType.PUSHBUTTON_NO -> "SB1 (Start)"
            ComponentType.PUSHBUTTON_NC -> "SB2 (Stop)"
            ComponentType.SELECTOR_2POS -> "SA1"
            ComponentType.SELECTOR_3POS -> "SA-HOA"
            ComponentType.THERMAL_OVERLOAD -> "FR1 (Overload)"
            ComponentType.CONTACTOR_COIL -> "KM1"
            ComponentType.AUX_CONTACT_NO -> "KM1-NO"
            ComponentType.AUX_CONTACT_NC -> "KM1-NC"
            ComponentType.CONTROL_RELAY -> "KA1"
            ComponentType.TIMER_ON_DELAY -> "KT1 (On-Del)"
            ComponentType.TIMER_OFF_DELAY -> "KT2 (Off-Del)"
            ComponentType.SENSOR_LIMIT -> "SQ1 (Limit)"
            ComponentType.SENSOR_PROXIMITY -> "B1 (Prox)"
            ComponentType.SENSOR_PHOTOELECTRIC -> "B2 (Photo)"
            ComponentType.SENSOR_FLOAT -> "SL1 (Float)"
            ComponentType.SENSOR_PRESSURE -> "SP1 (Press)"
            ComponentType.MOTOR_3PHASE -> "M1 (3Φ)"
            ComponentType.SOLENOID -> "YV1"
            ComponentType.PILOT_LIGHT -> "HL1"
            ComponentType.BUZZER -> "HA1"
            ComponentType.METER_VOLTMETER -> "PV1"
            ComponentType.METER_AMMETER -> "PA1"
            ComponentType.METER_FREQUENCY -> "PF1"
            ComponentType.METER_TACHOMETER -> "PR1"
        }
    }

    private fun createTerminalsForType(type: ComponentType, w: Float, h: Float): List<CircuitTerminal> {
        return when (type) {
            ComponentType.POWER_3P -> listOf(
                CircuitTerminal("L1", "L1", w * 0.2f, h, isPhase = true),
                CircuitTerminal("L2", "L2", w * 0.4f, h, isPhase = true),
                CircuitTerminal("L3", "L3", w * 0.6f, h, isPhase = true),
                CircuitTerminal("PE", "PE", w * 0.8f, h, isGround = true)
            )
            ComponentType.POWER_1P -> listOf(
                CircuitTerminal("L", "L", w * 0.25f, h, isPhase = true),
                CircuitTerminal("N", "N", w * 0.5f, h, isNeutral = true),
                CircuitTerminal("PE", "PE", w * 0.75f, h, isGround = true)
            )
            ComponentType.POWER_DC -> listOf(
                CircuitTerminal("+24V", "+24V", w * 0.3f, h, isDcPlus = true),
                CircuitTerminal("0V", "0V", w * 0.7f, h, isDcZero = true)
            )
            ComponentType.GENERATOR -> listOf(
                CircuitTerminal("L1", "L1", w * 0.2f, h, isPhase = true),
                CircuitTerminal("L2", "L2", w * 0.4f, h, isPhase = true),
                CircuitTerminal("L3", "L3", w * 0.6f, h, isPhase = true),
                CircuitTerminal("N", "N", w * 0.8f, h, isNeutral = true)
            )
            ComponentType.BREAKER_1P, ComponentType.FUSE, ComponentType.ESTOP,
            ComponentType.PUSHBUTTON_NO, ComponentType.PUSHBUTTON_NC,
            ComponentType.AUX_CONTACT_NO, ComponentType.AUX_CONTACT_NC -> listOf(
                CircuitTerminal("T1", "IN", w * 0.5f, 0f),
                CircuitTerminal("T2", "OUT", w * 0.5f, h)
            )
            ComponentType.BREAKER_3P -> listOf(
                CircuitTerminal("L1_IN", "1", w * 0.25f, 0f, isPhase = true),
                CircuitTerminal("L2_IN", "3", w * 0.5f, 0f, isPhase = true),
                CircuitTerminal("L3_IN", "5", w * 0.75f, 0f, isPhase = true),
                CircuitTerminal("L1_OUT", "2", w * 0.25f, h, isPhase = true),
                CircuitTerminal("L2_OUT", "4", w * 0.5f, h, isPhase = true),
                CircuitTerminal("L3_OUT", "6", w * 0.75f, h, isPhase = true)
            )
            ComponentType.SELECTOR_2POS -> listOf(
                CircuitTerminal("COM", "COM", w * 0.5f, 0f),
                CircuitTerminal("POS1", "1", w * 0.5f, h)
            )
            ComponentType.SELECTOR_3POS -> listOf(
                CircuitTerminal("COM", "COM", w * 0.5f, 0f),
                CircuitTerminal("POS_H", "H", w * 0.3f, h),
                CircuitTerminal("POS_A", "A", w * 0.7f, h)
            )
            ComponentType.THERMAL_OVERLOAD -> listOf(
                CircuitTerminal("L1_IN", "1", w * 0.2f, 0f),
                CircuitTerminal("L2_IN", "3", w * 0.4f, 0f),
                CircuitTerminal("L3_IN", "5", w * 0.6f, 0f),
                CircuitTerminal("NC_95", "95", w * 0.85f, 0f),
                CircuitTerminal("L1_OUT", "2", w * 0.2f, h),
                CircuitTerminal("L2_OUT", "4", w * 0.4f, h),
                CircuitTerminal("L3_OUT", "6", w * 0.6f, h),
                CircuitTerminal("NC_96", "96", w * 0.85f, h)
            )
            ComponentType.CONTACTOR_COIL, ComponentType.CONTROL_RELAY,
            ComponentType.TIMER_ON_DELAY, ComponentType.TIMER_OFF_DELAY -> listOf(
                CircuitTerminal("A1", "A1", w * 0.5f, 0f),
                CircuitTerminal("A2", "A2", w * 0.5f, h)
            )
            ComponentType.SENSOR_LIMIT, ComponentType.SENSOR_FLOAT, ComponentType.SENSOR_PRESSURE -> listOf(
                CircuitTerminal("T1", "1", w * 0.5f, 0f),
                CircuitTerminal("T2", "2", w * 0.5f, h)
            )
            ComponentType.SENSOR_PROXIMITY, ComponentType.SENSOR_PHOTOELECTRIC -> listOf(
                CircuitTerminal("+V", "+V", w * 0.25f, 0f, isDcPlus = true),
                CircuitTerminal("0V", "0V", w * 0.75f, 0f, isDcZero = true),
                CircuitTerminal("OUT", "OUT", w * 0.5f, h)
            )
            ComponentType.MOTOR_3PHASE -> listOf(
                CircuitTerminal("U", "U1", w * 0.25f, 0f),
                CircuitTerminal("V", "V1", w * 0.5f, 0f),
                CircuitTerminal("W", "W1", w * 0.75f, 0f),
                CircuitTerminal("PE", "PE", w * 0.5f, h, isGround = true)
            )
            ComponentType.SOLENOID, ComponentType.PILOT_LIGHT, ComponentType.BUZZER -> listOf(
                CircuitTerminal("T1", "X1", w * 0.5f, 0f),
                CircuitTerminal("T2", "X2", w * 0.5f, h)
            )
            ComponentType.METER_VOLTMETER -> listOf(
                CircuitTerminal("V+", "V+", w * 0.3f, 0f),
                CircuitTerminal("V-", "COM", w * 0.7f, 0f)
            )
            ComponentType.METER_AMMETER -> listOf(
                CircuitTerminal("IN", "IN", w * 0.5f, 0f),
                CircuitTerminal("OUT", "OUT", w * 0.5f, h)
            )
            ComponentType.METER_FREQUENCY -> listOf(
                CircuitTerminal("L", "L", w * 0.3f, 0f),
                CircuitTerminal("N", "N", w * 0.7f, 0f)
            )
            ComponentType.METER_TACHOMETER -> listOf(
                CircuitTerminal("SIG", "IN", w * 0.5f, 0f)
            )
        }
    }
}
