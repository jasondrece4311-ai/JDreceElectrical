package com.example.engine

import androidx.compose.ui.geometry.Offset
import com.example.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object CircuitSolver {

    data class SolveResult(
        val updatedElements: List<CircuitElement>,
        val updatedWires: List<Wire>,
        val activeFaults: List<CircuitFault>,
        val totalGenKva: Float = 0f,
        val genLoadPercent: Float = 0f
    )

    data class TerminalRef(val elementId: String, val terminalId: String)

    fun solve(
        elements: List<CircuitElement>,
        wires: List<Wire>,
        deltaTimeMs: Long = 100L
    ): SolveResult {
        val elementMap = elements.associateBy { it.id }.toMutableMap()
        val faults = mutableListOf<CircuitFault>()

        // 1. Map all wire connections into graph adjacency
        val terminalAdjacency = mutableMapOf<TerminalRef, MutableSet<TerminalRef>>()
        fun addEdge(t1: TerminalRef, t2: TerminalRef) {
            terminalAdjacency.getOrPut(t1) { mutableSetOf() }.add(t2)
            terminalAdjacency.getOrPut(t2) { mutableSetOf() }.add(t1)
        }

        for (wire in wires) {
            val t1 = TerminalRef(wire.fromElementId, wire.fromTerminalId)
            val t2 = TerminalRef(wire.toElementId, wire.toTerminalId)
            addEdge(t1, t2)
        }

        // 2. Add internal component closed-contact edges
        for (elem in elements) {
            when (elem.type) {
                ComponentType.BREAKER_1P -> {
                    if (!elem.isTripped && elem.isClosed) {
                        addEdge(TerminalRef(elem.id, "T1"), TerminalRef(elem.id, "T2"))
                    }
                }
                ComponentType.BREAKER_3P -> {
                    if (!elem.isTripped && elem.isClosed) {
                        addEdge(TerminalRef(elem.id, "L1_IN"), TerminalRef(elem.id, "L1_OUT"))
                        addEdge(TerminalRef(elem.id, "L2_IN"), TerminalRef(elem.id, "L2_OUT"))
                        addEdge(TerminalRef(elem.id, "L3_IN"), TerminalRef(elem.id, "L3_OUT"))
                    }
                }
                ComponentType.FUSE -> {
                    if (!elem.isBlown) {
                        addEdge(TerminalRef(elem.id, "T1"), TerminalRef(elem.id, "T2"))
                    }
                }
                ComponentType.ESTOP -> {
                    // E-Stop is NC: closed only when NOT latched
                    if (!elem.isLatched) {
                        addEdge(TerminalRef(elem.id, "T1"), TerminalRef(elem.id, "T2"))
                    }
                }
                ComponentType.PUSHBUTTON_NO -> {
                    if (elem.isPressed) {
                        addEdge(TerminalRef(elem.id, "T1"), TerminalRef(elem.id, "T2"))
                    }
                }
                ComponentType.PUSHBUTTON_NC -> {
                    if (!elem.isPressed) {
                        addEdge(TerminalRef(elem.id, "T1"), TerminalRef(elem.id, "T2"))
                    }
                }
                ComponentType.SELECTOR_2POS -> {
                    if (elem.selectorPosition == 1) {
                        addEdge(TerminalRef(elem.id, "COM"), TerminalRef(elem.id, "POS1"))
                    }
                }
                ComponentType.SELECTOR_3POS -> {
                    if (elem.selectorPosition == 1) {
                        addEdge(TerminalRef(elem.id, "COM"), TerminalRef(elem.id, "POS_H"))
                    } else if (elem.selectorPosition == 2) {
                        addEdge(TerminalRef(elem.id, "COM"), TerminalRef(elem.id, "POS_A"))
                    }
                }
                ComponentType.THERMAL_OVERLOAD -> {
                    if (!elem.isTripped) {
                        addEdge(TerminalRef(elem.id, "L1_IN"), TerminalRef(elem.id, "L1_OUT"))
                        addEdge(TerminalRef(elem.id, "L2_IN"), TerminalRef(elem.id, "L2_OUT"))
                        addEdge(TerminalRef(elem.id, "L3_IN"), TerminalRef(elem.id, "L3_OUT"))
                        addEdge(TerminalRef(elem.id, "NC_95"), TerminalRef(elem.id, "NC_96"))
                    }
                }
                ComponentType.AUX_CONTACT_NO -> {
                    // Check if linked coil is energized
                    val coil = elementMap[elem.linkedCoilId]
                    val isCoilOn = coil?.isEnergized == true
                    if (isCoilOn) {
                        addEdge(TerminalRef(elem.id, "T1"), TerminalRef(elem.id, "T2"))
                    }
                }
                ComponentType.AUX_CONTACT_NC -> {
                    val coil = elementMap[elem.linkedCoilId]
                    val isCoilOn = coil?.isEnergized == true
                    if (!isCoilOn) {
                        addEdge(TerminalRef(elem.id, "T1"), TerminalRef(elem.id, "T2"))
                    }
                }
                ComponentType.CONTROL_RELAY -> {
                    // Relay internal contact
                    if (elem.isEnergized) {
                        addEdge(TerminalRef(elem.id, "A1"), TerminalRef(elem.id, "A2"))
                    }
                }
                ComponentType.TIMER_ON_DELAY, ComponentType.TIMER_OFF_DELAY -> {
                    if (elem.timedContactClosed) {
                        addEdge(TerminalRef(elem.id, "A1"), TerminalRef(elem.id, "A2"))
                    }
                }
                ComponentType.SENSOR_LIMIT, ComponentType.SENSOR_FLOAT, ComponentType.SENSOR_PRESSURE -> {
                    if (elem.isSensorTriggered) {
                        addEdge(TerminalRef(elem.id, "T1"), TerminalRef(elem.id, "T2"))
                    }
                }
                ComponentType.SENSOR_PROXIMITY, ComponentType.SENSOR_PHOTOELECTRIC -> {
                    if (elem.isSensorTriggered) {
                        addEdge(TerminalRef(elem.id, "+V"), TerminalRef(elem.id, "OUT"))
                    }
                }
                ComponentType.METER_AMMETER -> {
                    // Ammeter has ultra-low shunt resistance, conducts straight through
                    addEdge(TerminalRef(elem.id, "IN"), TerminalRef(elem.id, "OUT"))
                }
                else -> {
                    // Other elements are power sources or high impedance loads (Motor, Coils, Lamps, Solenoid, Buzzer, Voltmeter)
                }
            }
        }

        // 3. Find connected components (Equipotential Nets)
        val visited = mutableSetOf<TerminalRef>()
        val nets = mutableListOf<Set<TerminalRef>>()

        for (term in terminalAdjacency.keys) {
            if (term !in visited) {
                val currentNet = mutableSetOf<TerminalRef>()
                val queue = ArrayDeque<TerminalRef>()
                queue.add(term)
                visited.add(term)

                while (queue.isNotEmpty()) {
                    val curr = queue.removeFirst()
                    currentNet.add(curr)
                    for (neighbor in terminalAdjacency[curr].orEmpty()) {
                        if (neighbor !in visited) {
                            visited.add(neighbor)
                            queue.add(neighbor)
                        }
                    }
                }
                nets.add(currentNet)
            }
        }

        // 4. Identify Potential for each Net
        // Labels: "L1", "L2", "L3", "L", "N", "PE", "+24V", "0V"
        val netPotentials = mutableMapOf<Set<TerminalRef>, MutableSet<String>>()

        for (net in nets) {
            val potentials = mutableSetOf<String>()
            for (t in net) {
                val elem = elementMap[t.elementId] ?: continue
                when (elem.type) {
                    ComponentType.POWER_3P -> {
                        when (t.terminalId) {
                            "L1" -> potentials.add("L1")
                            "L2" -> potentials.add("L2")
                            "L3" -> potentials.add("L3")
                            "PE" -> potentials.add("PE")
                        }
                    }
                    ComponentType.POWER_1P -> {
                        when (t.terminalId) {
                            "L" -> potentials.add("L")
                            "N" -> potentials.add("N")
                            "PE" -> potentials.add("PE")
                        }
                    }
                    ComponentType.POWER_DC -> {
                        when (t.terminalId) {
                            "+24V" -> potentials.add("+24V")
                            "0V" -> potentials.add("0V")
                        }
                    }
                    ComponentType.GENERATOR -> {
                        if (elem.isGenRunning && !elem.isGenStalled) {
                            when (t.terminalId) {
                                "L1" -> potentials.add("L1")
                                "L2" -> potentials.add("L2")
                                "L3" -> potentials.add("L3")
                                "N" -> potentials.add("N")
                            }
                        }
                    }
                    else -> {}
                }
            }
            netPotentials[net] = potentials
        }

        // Helper to check terminal potentials
        fun getPotentialsOf(elemId: String, termId: String): Set<String> {
            val ref = TerminalRef(elemId, termId)
            val net = nets.firstOrNull { ref in it } ?: return emptySet()
            return netPotentials[net] ?: emptySet()
        }

        // 5. Short-Circuit Detection:
        // Short occurs if a single net contains both Live and Neutral/Ground/0V, or two different live phases!
        var hasShortCircuit = false
        val shortedNets = mutableSetOf<Set<TerminalRef>>()

        for ((net, pots) in netPotentials) {
            val isPhaseToNeutral = (pots.contains("L") || pots.contains("L1") || pots.contains("L2") || pots.contains("L3")) &&
                    (pots.contains("N") || pots.contains("PE"))
            val isPhaseToPhase = (pots.contains("L1") && pots.contains("L2")) ||
                    (pots.contains("L2") && pots.contains("L3")) ||
                    (pots.contains("L1") && pots.contains("L3"))
            val isDcShort = pots.contains("+24V") && pots.contains("0V")

            if (isPhaseToNeutral || isPhaseToPhase || isDcShort) {
                hasShortCircuit = true
                shortedNets.add(net)

                // Trip breakers and blow fuses connected to this net
                for (t in net) {
                    val elem = elementMap[t.elementId] ?: continue
                    if (elem.type == ComponentType.FUSE && !elem.isBlown) {
                        elementMap[elem.id] = elem.copy(isBlown = true)
                        faults.add(
                            CircuitFault(
                                id = "fault-fuse-${elem.id}",
                                componentId = elem.id,
                                componentTag = elem.tag,
                                title = "Blown Fuse (${elem.tag})",
                                description = "Excessive fault current caused fuse element to rupture instantaneously.",
                                rootCause = "Direct short-circuit detected across line conductors without sufficient load impedance.",
                                standardRef = "NEC 240.21 / IEC 60947-4"
                            )
                        )
                    } else if ((elem.type == ComponentType.BREAKER_1P || elem.type == ComponentType.BREAKER_3P) && !elem.isTripped) {
                        elementMap[elem.id] = elem.copy(isTripped = true, isClosed = false)
                        faults.add(
                            CircuitFault(
                                id = "fault-breaker-${elem.id}",
                                componentId = elem.id,
                                componentTag = elem.tag,
                                title = "Circuit Breaker Tripped (${elem.tag})",
                                description = "Magnetic instantaneous trip element engaged due to high inrush fault current.",
                                rootCause = "Dead short circuit or phase-to-ground fault on feeder circuit.",
                                standardRef = "NEC 430.52 / NFPA 79"
                            )
                        )
                    }
                }
            }
        }

        // 6. Evaluate Active Loads & Update Component States
        var totalActiveKva = 0f
        var totalActiveKw = 0f

        for ((id, elem) in elementMap.entries) {
            when (elem.type) {
                ComponentType.CONTACTOR_COIL -> {
                    val pA1 = getPotentialsOf(id, "A1")
                    val pA2 = getPotentialsOf(id, "A2")
                    val hasLive = pA1.any { it.startsWith("L") || it == "+24V" }
                    val hasReturn = pA2.contains("N") || pA2.contains("0V") || (pA2.any { it.startsWith("L") } && pA2 != pA1)
                    val isEnergized = hasLive && hasReturn && !hasShortCircuit
                    elementMap[id] = elem.copy(isEnergized = isEnergized)
                    if (isEnergized) {
                        totalActiveKw += 0.02f
                        totalActiveKva += 0.05f
                    }
                }
                ComponentType.CONTROL_RELAY -> {
                    val pA1 = getPotentialsOf(id, "A1")
                    val pA2 = getPotentialsOf(id, "A2")
                    val isEnergized = (pA1.isNotEmpty() && pA2.isNotEmpty() && pA1 != pA2) && !hasShortCircuit
                    elementMap[id] = elem.copy(isEnergized = isEnergized)
                }
                ComponentType.TIMER_ON_DELAY -> {
                    val pA1 = getPotentialsOf(id, "A1")
                    val pA2 = getPotentialsOf(id, "A2")
                    val isPowered = (pA1.isNotEmpty() && pA2.isNotEmpty() && pA1 != pA2) && !hasShortCircuit

                    var elapsed = elem.elapsedTimerMs
                    var contactClosed = elem.timedContactClosed
                    if (isPowered) {
                        elapsed += deltaTimeMs
                        val targetMs = (elem.delaySeconds * 1000).toLong()
                        if (elapsed >= targetMs) {
                            contactClosed = true
                        }
                    } else {
                        elapsed = 0L
                        contactClosed = false
                    }
                    elementMap[id] = elem.copy(
                        isEnergized = isPowered,
                        elapsedTimerMs = elapsed,
                        isTimingActive = isPowered && !contactClosed,
                        timedContactClosed = contactClosed
                    )
                }
                ComponentType.TIMER_OFF_DELAY -> {
                    val pA1 = getPotentialsOf(id, "A1")
                    val pA2 = getPotentialsOf(id, "A2")
                    val isPowered = (pA1.isNotEmpty() && pA2.isNotEmpty() && pA1 != pA2) && !hasShortCircuit

                    var elapsed = elem.elapsedTimerMs
                    var contactClosed = elem.timedContactClosed
                    if (isPowered) {
                        contactClosed = true
                        elapsed = 0L
                    } else if (contactClosed) {
                        elapsed += deltaTimeMs
                        val targetMs = (elem.delaySeconds * 1000).toLong()
                        if (elapsed >= targetMs) {
                            contactClosed = false
                            elapsed = 0L
                        }
                    }
                    elementMap[id] = elem.copy(
                        isEnergized = isPowered,
                        elapsedTimerMs = elapsed,
                        isTimingActive = !isPowered && contactClosed,
                        timedContactClosed = contactClosed
                    )
                }
                ComponentType.PILOT_LIGHT -> {
                    val p1 = getPotentialsOf(id, "T1")
                    val p2 = getPotentialsOf(id, "T2")
                    val isLit = (p1.isNotEmpty() && p2.isNotEmpty() && p1 != p2) && !hasShortCircuit
                    elementMap[id] = elem.copy(isPilotLit = isLit)
                    if (isLit) {
                        totalActiveKw += 0.01f
                        totalActiveKva += 0.015f
                    }
                }
                ComponentType.BUZZER -> {
                    val p1 = getPotentialsOf(id, "T1")
                    val p2 = getPotentialsOf(id, "T2")
                    val isSounding = (p1.isNotEmpty() && p2.isNotEmpty() && p1 != p2) && !hasShortCircuit
                    elementMap[id] = elem.copy(isBuzzerSounding = isSounding)
                    if (isSounding) {
                        totalActiveKw += 0.015f
                        totalActiveKva += 0.02f
                    }
                }
                ComponentType.SOLENOID -> {
                    val p1 = getPotentialsOf(id, "T1")
                    val p2 = getPotentialsOf(id, "T2")
                    val isEnergized = (p1.isNotEmpty() && p2.isNotEmpty() && p1 != p2) && !hasShortCircuit
                    val stroke = if (isEnergized) 100f else 0f
                    elementMap[id] = elem.copy(isEnergized = isEnergized, solenoidStrokePercent = stroke)
                    if (isEnergized) {
                        totalActiveKw += 0.1f
                        totalActiveKva += 0.2f
                    }
                }
                ComponentType.MOTOR_3PHASE -> {
                    val pU = getPotentialsOf(id, "U")
                    val pV = getPotentialsOf(id, "V")
                    val pW = getPotentialsOf(id, "W")

                    val hasL1 = pU.contains("L1") || pV.contains("L1") || pW.contains("L1")
                    val hasL2 = pU.contains("L2") || pV.contains("L2") || pW.contains("L2")
                    val hasL3 = pU.contains("L3") || pV.contains("L3") || pW.contains("L3")

                    val allThreePhases = hasL1 && hasL2 && hasL3 && !hasShortCircuit

                    var rotation = MotorRotation.STOPPED
                    var rpm = 0f
                    var isStarting = false
                    var startDuration = elem.motorStartDurationMs

                    if (allThreePhases) {
                        // Check phase sequence: U=L1, V=L2, W=L3 is Forward. If swapped: Reverse.
                        val isFwd = pU.contains("L1") && pV.contains("L2") && pW.contains("L3")
                        rotation = if (isFwd) MotorRotation.FORWARD else MotorRotation.REVERSE

                        if (elem.motorRotation == MotorRotation.STOPPED) {
                            isStarting = true
                            startDuration = 0L
                        } else if (elem.isMotorStarting) {
                            startDuration += deltaTimeMs
                            if (startDuration < 2000L) {
                                isStarting = true
                            } else {
                                isStarting = false
                            }
                        }

                        val targetRpm = if (rotation == MotorRotation.FORWARD) 1750f else -1750f
                        rpm = if (isStarting) targetRpm * (startDuration / 2000f) else targetRpm

                        // 6x starting inrush current physics!
                        val currentMultiplier = if (isStarting) 6.0f else 1.0f
                        val currentDraw = elem.motorRunningCurrentAmps * currentMultiplier
                        val motorKva = (1.732f * 400f * currentDraw) / 1000f
                        totalActiveKva += motorKva
                        totalActiveKw += elem.motorKw * (if (isStarting) 2.5f else 1.0f)
                    } else {
                        // Check if single-phasing (phase loss fault!)
                        val phaseCount = listOf(hasL1, hasL2, hasL3).count { it }
                        if (phaseCount in 1..2) {
                            faults.add(
                                CircuitFault(
                                    id = "fault-phase-loss-${elem.id}",
                                    componentId = elem.id,
                                    componentTag = elem.tag,
                                    title = "Single-Phasing / Phase Loss on Motor (${elem.tag})",
                                    description = "Only $phaseCount of 3 phases detected at motor stator terminals. Rotor cannot generate rotating magnetic field.",
                                    rootCause = "Blown fuse or open phase contact upstream causing catastrophic motor stator overheating.",
                                    standardRef = "NEC 430.36 / IEEE Std 141"
                                )
                            )
                        }
                    }

                    elementMap[id] = elem.copy(
                        motorRotation = rotation,
                        motorRpm = rpm,
                        isMotorStarting = isStarting,
                        motorStartDurationMs = startDuration
                    )
                }
                ComponentType.METER_VOLTMETER -> {
                    val p1 = getPotentialsOf(id, "V+")
                    val p2 = getPotentialsOf(id, "V-")
                    val reading = when {
                        p1.contains("L1") && p2.contains("L2") -> 400f
                        p1.contains("L2") && p2.contains("L3") -> 400f
                        p1.contains("L1") && p2.contains("L3") -> 400f
                        (p1.contains("L") || p1.contains("L1") || p1.contains("L2") || p1.contains("L3")) && p2.contains("N") -> 230f
                        p1.contains("+24V") && p2.contains("0V") -> 24f
                        else -> 0f
                    }
                    elementMap[id] = elem.copy(meterReading = reading, meterUnit = "V")
                }
                ComponentType.METER_FREQUENCY -> {
                    val p1 = getPotentialsOf(id, "L")
                    val p2 = getPotentialsOf(id, "N")
                    val reading = if (p1.isNotEmpty() && p2.isNotEmpty()) 50.0f else 0f
                    elementMap[id] = elem.copy(meterReading = reading, meterUnit = "Hz")
                }
                ComponentType.METER_AMMETER -> {
                    // Ammeter reading reflects total load current passing through
                    elementMap[id] = elem.copy(
                        meterReading = if (totalActiveKva > 0f) (totalActiveKva * 1000f) / 400f / 1.732f else 0f,
                        meterUnit = "A"
                    )
                }
                ComponentType.METER_TACHOMETER -> {
                    // Finds primary motor in circuit and reports shaft RPM
                    val anyMotor = elementMap.values.firstOrNull { it.type == ComponentType.MOTOR_3PHASE }
                    val reading = anyMotor?.let { abs(it.motorRpm) } ?: 0f
                    elementMap[id] = elem.copy(meterReading = reading, meterUnit = "RPM")
                }
                else -> {}
            }
        }

        // 7. Generator Engine Physics & Stall Overload Detection
        var genLoadPercent = 0f
        for ((id, elem) in elementMap.entries) {
            if (elem.type == ComponentType.GENERATOR) {
                genLoadPercent = if (elem.genRatedKva > 0) (totalActiveKva / elem.genRatedKva) * 100f else 0f
                val isStalled = totalActiveKva > (elem.genRatedKva * 1.15f)

                if (isStalled && !elem.isGenStalled) {
                    faults.add(
                        CircuitFault(
                            id = "fault-gen-stall-${elem.id}",
                            componentId = elem.id,
                            componentTag = elem.tag,
                            title = "Generator Engine Stall / Flameout (${elem.tag})",
                            description = "Connected motor inrush and load demand (${"%.1f".format(totalActiveKva)} kVA) severely exceeded alternator capacity (${"%.1f".format(elem.genRatedKva)} kVA).",
                            rootCause = "Heavy motor locked-rotor starting current pulled engine RPM below governor torque limit.",
                            standardRef = "ISO 8528-5 / NFPA 110"
                        )
                    )
                }

                elementMap[id] = elem.copy(
                    genCurrentKva = totalActiveKva,
                    genLoadPercent = genLoadPercent,
                    isGenStalled = isStalled,
                    isGenOverloaded = genLoadPercent > 100f
                )
            }
        }

        // 8. Update Wire States, Colors, and Live Volt/Amp readings
        val updatedWires = wires.map { wire ->
            val refFrom = TerminalRef(wire.fromElementId, wire.fromTerminalId)
            val net = nets.firstOrNull { refFrom in it }
            val potentials = net?.let { netPotentials[it] } ?: emptySet()

            val state = when {
                net in shortedNets -> WireState.SHORT_CIRCUIT
                potentials.any { it.startsWith("L") } -> WireState.LIVE_AC
                potentials.contains("+24V") -> WireState.LIVE_DC
                potentials.contains("N") || potentials.contains("0V") -> WireState.NEUTRAL
                potentials.contains("PE") -> WireState.GROUND
                else -> WireState.DEAD
            }

            val voltage = when {
                potentials.any { it.startsWith("L") } -> 230f
                potentials.contains("+24V") -> 24f
                else -> 0f
            }

            val current = when (state) {
                WireState.SHORT_CIRCUIT -> 150f
                WireState.LIVE_AC, WireState.LIVE_DC -> if (totalActiveKva > 0) max(0.5f, totalActiveKva * 1.4f) else 0f
                else -> 0f
            }

            // Calculate 90° auto-routing Manhattan waypoints
            val elemFrom = elementMap[wire.fromElementId]
            val elemTo = elementMap[wire.toElementId]
            val termFrom = elemFrom?.findTerminal(wire.fromTerminalId)
            val termTo = elemTo?.findTerminal(wire.toTerminalId)

            val waypoints = if (elemFrom != null && elemTo != null && termFrom != null && termTo != null) {
                val start = elemFrom.getTerminalAbsolutePos(termFrom)
                val end = elemTo.getTerminalAbsolutePos(termTo)
                computeManhattanRoute(start, end)
            } else {
                wire.waypoints
            }

            wire.copy(
                state = state,
                voltage = voltage,
                current = current,
                waypoints = waypoints
            )
        }

        return SolveResult(
            updatedElements = elementMap.values.toList(),
            updatedWires = updatedWires,
            activeFaults = faults,
            totalGenKva = totalActiveKva,
            genLoadPercent = genLoadPercent
        )
    }

    /**
     * Computes 90-degree orthogonal Manhattan route between two terminal offsets.
     */
    fun computeManhattanRoute(start: Offset, end: Offset): List<Offset> {
        val waypoints = mutableListOf<Offset>()
        waypoints.add(start)

        val dx = end.x - start.x
        val dy = end.y - start.y

        if (abs(dx) < 2f || abs(dy) < 2f) {
            // Almost aligned straight line
            waypoints.add(end)
            return waypoints
        }

        // Smart corner route: travel vertically halfway or based on coordinate
        val midY = start.y + dy * 0.5f
        waypoints.add(Offset(start.x, midY))
        waypoints.add(Offset(end.x, midY))
        waypoints.add(end)

        return waypoints
    }
}
