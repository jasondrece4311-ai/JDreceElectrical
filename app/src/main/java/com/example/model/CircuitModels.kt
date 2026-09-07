package com.example.model

import androidx.compose.ui.geometry.Offset

enum class ComponentCategory(val title: String) {
    POWER("Power Sources"),
    PROTECT("Switch & Protect"),
    LOGIC("Logic & Relays"),
    SENSORS("Sensors"),
    OUTPUTS("Outputs"),
    METERS("Meters")
}

enum class ComponentType(val displayName: String, val category: ComponentCategory) {
    // Power
    POWER_3P("3Φ Supply (L1/L2/L3/PE)", ComponentCategory.POWER),
    POWER_1P("1Φ Supply (L/N/PE)", ComponentCategory.POWER),
    POWER_DC("24V DC Supply (24V/0V)", ComponentCategory.POWER),
    GENERATOR("Generator Set", ComponentCategory.POWER),

    // Switch & Protect
    BREAKER_1P("1P Circuit Breaker", ComponentCategory.PROTECT),
    BREAKER_3P("3P Circuit Breaker", ComponentCategory.PROTECT),
    FUSE("Fuse", ComponentCategory.PROTECT),
    ESTOP("E-Stop (Mushroom NC)", ComponentCategory.PROTECT),
    PUSHBUTTON_NO("Start Pushbutton (NO)", ComponentCategory.PROTECT),
    PUSHBUTTON_NC("Stop Pushbutton (NC)", ComponentCategory.PROTECT),
    SELECTOR_2POS("Selector 2-Pos (0-1)", ComponentCategory.PROTECT),
    SELECTOR_3POS("Selector 3-Pos (H-0-A)", ComponentCategory.PROTECT),
    THERMAL_OVERLOAD("Thermal Overload Relay", ComponentCategory.PROTECT),

    // Logic
    CONTACTOR_COIL("Contactor Coil (KM)", ComponentCategory.LOGIC),
    AUX_CONTACT_NO("Aux Contact (NO)", ComponentCategory.LOGIC),
    AUX_CONTACT_NC("Aux Contact (NC)", ComponentCategory.LOGIC),
    CONTROL_RELAY("Control Relay (KA)", ComponentCategory.LOGIC),
    TIMER_ON_DELAY("Timer (On-Delay)", ComponentCategory.LOGIC),
    TIMER_OFF_DELAY("Timer (Off-Delay)", ComponentCategory.LOGIC),

    // Sensors
    SENSOR_LIMIT("Limit Switch", ComponentCategory.SENSORS),
    SENSOR_PROXIMITY("Proximity Sensor", ComponentCategory.SENSORS),
    SENSOR_PHOTOELECTRIC("Photoelectric Sensor", ComponentCategory.SENSORS),
    SENSOR_FLOAT("Float Level Switch", ComponentCategory.SENSORS),
    SENSOR_PRESSURE("Pressure Switch", ComponentCategory.SENSORS),

    // Outputs
    MOTOR_3PHASE("3Φ Induction Motor", ComponentCategory.OUTPUTS),
    SOLENOID("Solenoid Valve", ComponentCategory.OUTPUTS),
    PILOT_LIGHT("Pilot Light", ComponentCategory.OUTPUTS),
    BUZZER("Audible Buzzer", ComponentCategory.OUTPUTS),

    // Meters
    METER_VOLTMETER("Voltmeter", ComponentCategory.METERS),
    METER_AMMETER("Ammeter", ComponentCategory.METERS),
    METER_FREQUENCY("Frequency Meter", ComponentCategory.METERS),
    METER_TACHOMETER("Tachometer (RPM)", ComponentCategory.METERS)
}

enum class WireState {
    DEAD,
    LIVE_AC,
    LIVE_DC,
    NEUTRAL,
    GROUND,
    SHORT_CIRCUIT
}

enum class LightColor(val hex: Long, val label: String) {
    GREEN(0xFF22C55E, "Green"),
    RED(0xFFEF4444, "Red"),
    AMBER(0xFFF59E0B, "Amber"),
    BLUE(0xFF3B82F6, "Blue"),
    WHITE(0xFFF8FAFC, "White")
}

enum class TimerMode {
    ON_DELAY,
    OFF_DELAY
}

enum class MotorRotation {
    STOPPED,
    FORWARD,
    REVERSE
}

data class CircuitTerminal(
    val id: String,
    val label: String,
    val localX: Float,
    val localY: Float,
    val isPhase: Boolean = false,
    val isNeutral: Boolean = false,
    val isGround: Boolean = false,
    val isDcPlus: Boolean = false,
    val isDcZero: Boolean = false
)

data class Wire(
    val id: String,
    val fromElementId: String,
    val fromTerminalId: String,
    val toElementId: String,
    val toTerminalId: String,
    val state: WireState = WireState.DEAD,
    val voltage: Float = 0f,
    val current: Float = 0f,
    val waypoints: List<Offset> = emptyList()
)

data class CircuitElement(
    val id: String,
    val type: ComponentType,
    val tag: String,
    val x: Float,
    val y: Float,
    val rotation: Int = 0, // 0, 90, 180, 270 degrees
    val width: Float = 100f,
    val height: Float = 80f,
    val terminals: List<CircuitTerminal> = emptyList(),

    // State properties
    // Switch/Protection states
    val isClosed: Boolean = false,
    val isTripped: Boolean = false,
    val isBlown: Boolean = false,
    val isLatched: Boolean = false,
    val isPressed: Boolean = false,
    val selectorPosition: Int = 0, // 0, 1, 2
    val ratingAmps: Float = 16f,
    val tripCurrentAmps: Float = 18f,

    // Logic & Contact states
    val linkedCoilId: String = "", // for AuxContact linked to ContactorCoil
    val isEnergized: Boolean = false,
    val nominalVoltage: Float = 230f,

    // Timer states
    val timerMode: TimerMode = TimerMode.ON_DELAY,
    val delaySeconds: Float = 3f,
    val elapsedTimerMs: Long = 0L,
    val isTimingActive: Boolean = false,
    val timedContactClosed: Boolean = false,

    // Sensor states
    val isSensorTriggered: Boolean = false,

    // Output & Load states
    val motorRotation: MotorRotation = MotorRotation.STOPPED,
    val motorRpm: Float = 0f,
    val motorTargetRpm: Float = 1750f,
    val motorKw: Float = 4.0f,
    val motorRunningCurrentAmps: Float = 8.2f,
    val isMotorStarting: Boolean = false,
    val motorStartDurationMs: Long = 0L,

    val solenoidStrokePercent: Float = 0f,
    val pilotColor: LightColor = LightColor.GREEN,
    val isPilotLit: Boolean = false,
    val isBuzzerSounding: Boolean = false,

    // Meters live values
    val meterReading: Float = 0f,
    val meterUnit: String = "V",

    // Generator physics
    val genRatedKw: Float = 20.0f,
    val genRatedKva: Float = 25.0f,
    val genCurrentKva: Float = 0f,
    val genLoadPercent: Float = 0f,
    val isGenRunning: Boolean = true,
    val isGenStalled: Boolean = false,
    val isGenOverloaded: Boolean = false
) {
    fun getTerminalAbsolutePos(terminal: CircuitTerminal): Offset {
        // Rotate local offset around center if rotation != 0
        val cx = x + width / 2f
        val cy = y + height / 2f
        val rad = Math.toRadians(rotation.toDouble())
        val cos = Math.cos(rad).toFloat()
        val sin = Math.sin(rad).toFloat()

        val lx = (x + terminal.localX) - cx
        val ly = (y + terminal.localY) - cy

        val rx = lx * cos - ly * sin
        val ry = lx * sin + ly * cos
        return Offset(cx + rx, cy + ry)
    }

    fun findTerminal(terminalId: String): CircuitTerminal? =
        terminals.firstOrNull { it.id == terminalId }
}

data class CircuitFault(
    val id: String,
    val componentId: String,
    val componentTag: String,
    val title: String,
    val description: String,
    val rootCause: String,
    val standardRef: String,
    val timestampMs: Long = System.currentTimeMillis()
)

data class GenSizingReport(
    val totalRunningKw: Float,
    val totalRunningKva: Float,
    val largestMotorStartingKva: Float,
    val peakStartingKva: Float,
    val recommendedGenKw: Float,
    val recommendedGenKva: Float,
    val loadSafetyMarginPercent: Float = 25f,
    val motorsCount: Int,
    val notes: List<String>
)
