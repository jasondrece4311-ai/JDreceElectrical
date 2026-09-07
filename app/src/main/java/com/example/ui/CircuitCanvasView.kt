package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*
import kotlin.math.*

@Composable
fun CircuitCanvasView(
    elements: List<CircuitElement>,
    wires: List<Wire>,
    selectedElementId: String?,
    onSelectElement: (CircuitElement?) -> Unit,
    onToggleElement: (CircuitElement) -> Unit,
    onMoveElement: (String, Float, Float) -> Unit,
    onAddWire: (String, String, String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Zoom and Pan State
    var scale by remember { mutableStateOf(1.0f) }
    var panX by remember { mutableStateOf(0f) }
    var panY by remember { mutableStateOf(0f) }

    // Wire Connection Dragging State
    var connectingFrom by remember { mutableStateOf<Pair<String, String>?>(null) } // (elementId, terminalId)
    var dragCurrentPos by remember { mutableStateOf<Offset?>(null) }

    // Wire Long-Press Tooltip State
    var inspectedWire by remember { mutableStateOf<Wire?>(null) }
    var tooltipPos by remember { mutableStateOf<Offset?>(null) }

    // Element Dragging State
    var draggingElementId by remember { mutableStateOf<String?>(null) }
    var dragStartOffset by remember { mutableStateOf<Offset?>(null) }

    val textMeasurer = rememberTextMeasurer()

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(CadDarkBackground)
                .testTag("circuit_canvas")
                // Transform Gestures: Pinch-Zoom & Two-finger Pan
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(0.4f, 3.0f)
                        panX += pan.x
                        panY += pan.y
                    }
                }
                // Tap & Long-Press Detection
                .pointerInput(elements, wires, scale, panX, panY) {
                    detectTapGestures(
                        onTap = { screenOffset ->
                            // Dismiss inspected wire tooltip
                            inspectedWire = null

                            // Convert screen offset to world canvas coordinates
                            val worldX = (screenOffset.x - panX) / scale
                            val worldY = (screenOffset.y - panY) / scale
                            val worldPos = Offset(worldX, worldY)

                            // 1. Check if tapped on a terminal to start/complete wire connection
                            var tappedTerminal: Pair<String, String>? = null
                            for (elem in elements) {
                                for (term in elem.terminals) {
                                    val termPos = elem.getTerminalAbsolutePos(term)
                                    val dist = (worldPos - termPos).getDistance()
                                    if (dist <= 24f) {
                                        tappedTerminal = Pair(elem.id, term.id)
                                        break
                                    }
                                }
                                if (tappedTerminal != null) break
                            }

                            if (tappedTerminal != null) {
                                val currentFrom = connectingFrom
                                if (currentFrom == null) {
                                    connectingFrom = tappedTerminal
                                } else {
                                    if (currentFrom.first != tappedTerminal.first || currentFrom.second != tappedTerminal.second) {
                                        onAddWire(
                                            currentFrom.first, currentFrom.second,
                                            tappedTerminal.first, tappedTerminal.second
                                        )
                                    }
                                    connectingFrom = null
                                    dragCurrentPos = null
                                }
                                return@detectTapGestures
                            }

                            // If we tapped anywhere else and were wiring, cancel wiring
                            if (connectingFrom != null) {
                                connectingFrom = null
                                dragCurrentPos = null
                                return@detectTapGestures
                            }

                            // 2. Check if tapped directly on an element
                            val tappedElement = elements.lastOrNull { elem ->
                                worldX >= elem.x && worldX <= elem.x + elem.width &&
                                        worldY >= elem.y && worldY <= elem.y + elem.height
                            }

                            if (tappedElement != null) {
                                // If already selected, tap toggles primary switch/push action!
                                if (selectedElementId == tappedElement.id) {
                                    onToggleElement(tappedElement)
                                } else {
                                    onSelectElement(tappedElement)
                                }
                            } else {
                                onSelectElement(null)
                            }
                        },
                        onLongPress = { screenOffset ->
                            // Wire probe tooltip: Find wire closest to tap
                            val worldX = (screenOffset.x - panX) / scale
                            val worldY = (screenOffset.y - panY) / scale
                            val worldPos = Offset(worldX, worldY)

                            val hitWire = wires.firstOrNull { wire ->
                                isPointNearWire(worldPos, wire.waypoints, threshold = 20f)
                            }

                            if (hitWire != null) {
                                inspectedWire = hitWire
                                tooltipPos = screenOffset
                            }
                        }
                    )
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 1. Draw CAD Grid Background with Snap Lines
            drawCadGrid(scale, panX, panY, canvasWidth, canvasHeight)

            // Push Canvas Matrix (Pan + Zoom)
            withTransform({
                translate(panX, panY)
                scale(scale, scale, Offset.Zero)
            }) {
                // 2. Draw Wires with 90° Manhattan Orthogonal Routing, Bridge Jumps, and T-Junctions
                drawCircuitWires(wires, elements, textMeasurer)

                // 3. Draw In-Progress Routing Wire
                connectingFrom?.let { fromPair ->
                    val fromElem = elements.firstOrNull { it.id == fromPair.first }
                    val fromTerm = fromElem?.findTerminal(fromPair.second)
                    if (fromElem != null && fromTerm != null) {
                        val startPos = fromElem.getTerminalAbsolutePos(fromTerm)
                        val endPos = dragCurrentPos ?: (startPos + Offset(40f, 40f))

                        val path = Path().apply {
                            moveTo(startPos.x, startPos.y)
                            val midY = (startPos.y + endPos.y) / 2f
                            lineTo(startPos.x, midY)
                            lineTo(endPos.x, midY)
                            lineTo(endPos.x, endPos.y)
                        }
                        drawPath(
                            path = path,
                            color = ElectricCyan,
                            style = Stroke(
                                width = 3f,
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                            )
                        )
                        drawCircle(ElectricCyan, radius = 6f, center = startPos)
                        drawCircle(ElectricAmber, radius = 6f, center = endPos)
                    }
                }

                // 4. Draw Circuit Elements
                for (elem in elements) {
                    val isSelected = elem.id == selectedElementId
                    drawCircuitElement(elem, isSelected, textMeasurer)
                }
            }
        }

        // Touch Magnifier Loupe (shows magnified view around finger/terminal during wiring)
        connectingFrom?.let { fromPair ->
            val fromElem = elements.firstOrNull { it.id == fromPair.first }
            val fromTerm = fromElem?.findTerminal(fromPair.second)
            if (fromElem != null && fromTerm != null) {
                MagnifierLoupe(
                    terminalLabel = "${fromElem.tag}.${fromTerm.label}",
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )
            }
        }

        // Live Wire Volt/Amp Tooltip Popup on Long-Press
        inspectedWire?.let { wire ->
            tooltipPos?.let { pos ->
                WireMeasurementTooltip(
                    wire = wire,
                    screenPos = pos,
                    onDismiss = { inspectedWire = null }
                )
            }
        }
    }
}

/**
 * Renders technical CAD grid lines with 20dp spacing and coordinate dots.
 */
private fun DrawScope.drawCadGrid(
    scale: Float,
    panX: Float,
    panY: Float,
    width: Float,
    height: Float
) {
    val gridSize = 30f * scale
    val startX = (panX % gridSize + gridSize) % gridSize
    val startY = (panY % gridSize + gridSize) % gridSize

    // Subtle grid dots
    var x = startX
    while (x < width) {
        var y = startY
        while (y < height) {
            drawCircle(
                color = CadGridDot.copy(alpha = 0.6f),
                radius = 1.2f,
                center = Offset(x, y)
            )
            y += gridSize
        }
        x += gridSize
    }
}

/**
 * Draws all auto-routed 90° wires with cross-wire bridge jumps and T-junction solder dots.
 */
private fun DrawScope.drawCircuitWires(
    wires: List<Wire>,
    elements: List<CircuitElement>,
    textMeasurer: TextMeasurer
) {
    // 1. Draw Wires with Cross-Wire Bridge Jumps
    for (wire in wires) {
        val waypoints = wire.waypoints
        if (waypoints.size < 2) continue

        val wireColor = when (wire.state) {
            WireState.LIVE_AC -> WireLive
            WireState.LIVE_DC -> ElectricAmber
            WireState.NEUTRAL -> WireNeutral
            WireState.GROUND -> WireGround
            WireState.SHORT_CIRCUIT -> WireShort
            WireState.DEAD -> WireDead
        }

        val strokeWidth = if (wire.state == WireState.SHORT_CIRCUIT) 4.5f else 3.0f

        val path = Path()
        path.moveTo(waypoints[0].x, waypoints[0].y)

        for (i in 1 until waypoints.size) {
            val pPrev = waypoints[i - 1]
            val pCurr = waypoints[i]

            // Check if this horizontal/vertical segment crosses any other wire
            val hasCrossBridge = checkCrossWireIntersection(pPrev, pCurr, wires, wire)

            if (hasCrossBridge != null) {
                // Draw up to cross point, make a bridge jump arc, then continue
                val crossPoint = hasCrossBridge
                path.lineTo(crossPoint.x - 8f, crossPoint.y)
                path.quadraticBezierTo(crossPoint.x, crossPoint.y - 12f, crossPoint.x + 8f, crossPoint.y)
                path.lineTo(pCurr.x, pCurr.y)
            } else {
                path.lineTo(pCurr.x, pCurr.y)
            }
        }

        drawPath(
            path = path,
            color = wireColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        // Draw glowing wire effect for live phases
        if (wire.state == WireState.LIVE_AC || wire.state == WireState.SHORT_CIRCUIT) {
            drawPath(
                path = path,
                color = wireColor.copy(alpha = 0.25f),
                style = Stroke(width = strokeWidth * 2.5f, cap = StrokeCap.Round)
            )
        }
    }

    // 2. Draw T-Junction Solder Dots where multiple wire endpoints meet
    val endpointCounts = mutableMapOf<Offset, Int>()
    for (wire in wires) {
        for (pt in wire.waypoints) {
            val rounded = Offset(round(pt.x / 4f) * 4f, round(pt.y / 4f) * 4f)
            endpointCounts[rounded] = (endpointCounts[rounded] ?: 0) + 1
        }
    }

    for ((pt, count) in endpointCounts) {
        if (count >= 3) {
            // Solder junction dot!
            drawCircle(color = TextPrimary, radius = 4.5f, center = pt)
            drawCircle(color = CadDarkBackground, radius = 2f, center = pt)
        }
    }
}

/**
 * Checks if segment p1->p2 intersects orthogonally with any other non-connected wire segment.
 */
private fun checkCrossWireIntersection(
    p1: Offset,
    p2: Offset,
    wires: List<Wire>,
    currentWire: Wire
): Offset? {
    val isHorizontal = abs(p1.y - p2.y) < 2f
    if (!isHorizontal) return null

    val minX = min(p1.x, p2.x) + 10f
    val maxX = max(p1.x, p2.x) - 10f
    val y = p1.y

    for (other in wires) {
        if (other.id == currentWire.id) continue
        for (i in 1 until other.waypoints.size) {
            val op1 = other.waypoints[i - 1]
            val op2 = other.waypoints[i]
            val isOtherVertical = abs(op1.x - op2.x) < 2f
            if (isOtherVertical) {
                val ox = op1.x
                val minY = min(op1.y, op2.y)
                val maxY = max(op1.y, op2.y)
                if (ox in minX..maxX && y in minY..maxY) {
                    return Offset(ox, y)
                }
            }
        }
    }
    return null
}

/**
 * Renders individual schematic symbols according to industrial electrical standards.
 */
private fun DrawScope.drawCircuitElement(
    elem: CircuitElement,
    isSelected: Boolean,
    textMeasurer: TextMeasurer
) {
    val x = elem.x
    val y = elem.y
    val w = elem.width
    val h = elem.height

    // Component Enclosure Background
    val cardBg = if (elem.isTripped || elem.isBlown || elem.isGenStalled) {
        IndustrialStopRed.copy(alpha = 0.18f)
    } else if (elem.isEnergized || elem.motorRotation != MotorRotation.STOPPED || elem.isPilotLit) {
        ElectricCyan.copy(alpha = 0.12f)
    } else {
        CadSurface
    }

    drawRoundRect(
        color = cardBg,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(10f, 10f)
    )

    // Selection border / Status border
    val borderColor = when {
        isSelected -> AiGeminiLight
        elem.isTripped || elem.isBlown || elem.isGenStalled -> IndustrialStopRed
        elem.isEnergized || elem.isPilotLit -> ElectricCyan
        else -> CadSurfaceVariant
    }

    drawRoundRect(
        color = borderColor,
        topLeft = Offset(x, y),
        size = Size(w, h),
        cornerRadius = CornerRadius(10f, 10f),
        style = Stroke(width = if (isSelected) 3.5f else 1.5f)
    )

    // Component Tag Label at top-left
    val tagResult = textMeasurer.measure(
        text = elem.tag,
        style = TextStyle(
            color = if (isSelected) AiGeminiLight else TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    )
    drawText(
        textLayoutResult = tagResult,
        topLeft = Offset(x + 8f, y + 6f)
    )

    // Internal Schematic Symbol Rendering
    when (elem.type) {
        ComponentType.POWER_3P, ComponentType.POWER_1P, ComponentType.POWER_DC -> {
            drawPowerSourceSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.GENERATOR -> {
            drawGeneratorSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.BREAKER_1P, ComponentType.BREAKER_3P -> {
            drawBreakerSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.FUSE -> {
            drawFuseSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.ESTOP -> {
            drawEStopSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.PUSHBUTTON_NO, ComponentType.PUSHBUTTON_NC -> {
            drawPushbuttonSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.SELECTOR_2POS, ComponentType.SELECTOR_3POS -> {
            drawSelectorSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.THERMAL_OVERLOAD -> {
            drawThermalOverloadSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.CONTACTOR_COIL, ComponentType.CONTROL_RELAY -> {
            drawCoilSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.AUX_CONTACT_NO, ComponentType.AUX_CONTACT_NC -> {
            drawAuxContactSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.TIMER_ON_DELAY, ComponentType.TIMER_OFF_DELAY -> {
            drawTimerSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.SENSOR_LIMIT, ComponentType.SENSOR_PROXIMITY,
        ComponentType.SENSOR_PHOTOELECTRIC, ComponentType.SENSOR_FLOAT,
        ComponentType.SENSOR_PRESSURE -> {
            drawSensorSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.MOTOR_3PHASE -> {
            drawMotor3PhaseSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.SOLENOID -> {
            drawSolenoidSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.PILOT_LIGHT -> {
            drawPilotLightSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.BUZZER -> {
            drawBuzzerSymbol(elem, x, y, w, h, textMeasurer)
        }
        ComponentType.METER_VOLTMETER, ComponentType.METER_AMMETER,
        ComponentType.METER_FREQUENCY, ComponentType.METER_TACHOMETER -> {
            drawMeterSymbol(elem, x, y, w, h, textMeasurer)
        }
    }

    // Draw Terminals with Solder Pad Rings and Pin Labels
    for (t in elem.terminals) {
        val tPos = elem.getTerminalAbsolutePos(t)

        val termColor = when {
            t.isPhase -> WireLive
            t.isNeutral -> WireNeutral
            t.isGround -> WireGround
            t.isDcPlus -> ElectricAmber
            t.isDcZero -> WireNeutral
            else -> ElectricCyan
        }

        // Terminal pin circle
        drawCircle(color = CadDarkBackground, radius = 6f, center = tPos)
        drawCircle(color = termColor, radius = 4.5f, center = tPos)
        drawCircle(color = Color.White, radius = 1.5f, center = tPos)

        // Terminal pin small label
        val labelResult = textMeasurer.measure(
            text = t.label,
            style = TextStyle(color = TextSecondary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
        val labelOffset = if (t.localY < h / 2f) {
            Offset(tPos.x - labelResult.size.width / 2f, tPos.y - 14f)
        } else {
            Offset(tPos.x - labelResult.size.width / 2f, tPos.y + 4f)
        }
        drawText(textLayoutResult = labelResult, topLeft = labelOffset)
    }
}

// ----------------- SYMBOL RENDERERS -----------------

private fun DrawScope.drawPowerSourceSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f
    val label = if (elem.type == ComponentType.POWER_DC) "24V DC" else "400V 3Φ"
    val res = textMeasurer.measure(
        text = label,
        style = TextStyle(color = ElectricAmber, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
    )
    drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f))
}

private fun DrawScope.drawGeneratorSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 8f

    // Outer circle
    val circleColor = if (elem.isGenStalled) IndustrialStopRed else ElectricAmber
    drawCircle(color = circleColor, radius = 22f, center = Offset(cx, cy), style = Stroke(2.5f))

    val statusText = if (elem.isGenStalled) "STALL" else "G~"
    val res = textMeasurer.measure(
        text = statusText,
        style = TextStyle(
            color = if (elem.isGenStalled) IndustrialStopRed else Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    )
    drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f - 6f))

    // Generator Load % below
    val loadText = "${"%.0f".format(elem.genLoadPercent)}%"
    val loadRes = textMeasurer.measure(
        text = loadText,
        style = TextStyle(
            color = if (elem.isGenOverloaded) IndustrialStopRed else IndustrialRunGreen,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
    )
    drawText(loadRes, topLeft = Offset(cx - loadRes.size.width / 2f, cy + 8f))
}

private fun DrawScope.drawBreakerSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    // Breaker contact switch arm
    val isClosed = elem.isClosed && !elem.isTripped
    val armColor = if (elem.isTripped) IndustrialStopRed else if (isClosed) IndustrialRunGreen else TextSecondary

    drawLine(
        color = armColor,
        start = Offset(cx - 14f, cy + 12f),
        end = if (isClosed) Offset(cx + 14f, cy - 12f) else Offset(cx + 4f, cy - 20f),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )

    // Trip indicator
    if (elem.isTripped) {
        val tripRes = textMeasurer.measure(
            text = "TRIPPED",
            style = TextStyle(color = IndustrialStopRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
        drawText(tripRes, topLeft = Offset(cx - tripRes.size.width / 2f, y + h - 18f))
    }
}

private fun DrawScope.drawFuseSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    // Fuse rectangle
    drawRect(
        color = if (elem.isBlown) IndustrialStopRed else ElectricAmber,
        topLeft = Offset(cx - 10f, cy - 16f),
        size = Size(20f, 32f),
        style = Stroke(2f)
    )
    // Internal wire link
    if (!elem.isBlown) {
        drawLine(
            color = ElectricAmber,
            start = Offset(cx, cy - 16f),
            end = Offset(cx, cy + 16f),
            strokeWidth = 2f
        )
    } else {
        // Blown break
        val res = textMeasurer.measure(
            text = "BLOWN",
            style = TextStyle(color = IndustrialStopRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
        drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - 6f))
    }
}

private fun DrawScope.drawEStopSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 6f

    // Yellow base ring
    drawCircle(color = ElectricYellow, radius = 20f, center = Offset(cx, cy))
    // Mushroom head (Red)
    val mushroomColor = if (elem.isLatched) IndustrialStopRed.copy(alpha = 0.6f) else IndustrialStopRed
    drawCircle(color = mushroomColor, radius = 15f, center = Offset(cx, cy))

    val label = if (elem.isLatched) "LATCH" else "NC"
    val res = textMeasurer.measure(
        text = label,
        style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    )
    drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f))
}

private fun DrawScope.drawPushbuttonSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f
    val isNo = elem.type == ComponentType.PUSHBUTTON_NO
    val buttonColor = if (isNo) IndustrialRunGreen else IndustrialStopRed

    // Pushbutton actuator head
    drawCircle(color = buttonColor, radius = 14f, center = Offset(cx, cy))
    val label = if (elem.isPressed) "ON" else if (isNo) "START" else "STOP"
    val res = textMeasurer.measure(
        text = label,
        style = TextStyle(color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    )
    drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f))
}

private fun DrawScope.drawSelectorSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    drawCircle(color = CadSurfaceVariant, radius = 16f, center = Offset(cx, cy))
    // Switch pointer angle
    val angleDeg = when (elem.selectorPosition) {
        1 -> 45f
        2 -> 135f
        else -> 90f
    }
    val rad = Math.toRadians(angleDeg.toDouble())
    val px = cx + 12f * cos(rad).toFloat()
    val py = cy - 12f * sin(rad).toFloat()

    drawLine(
        color = ElectricCyan,
        start = Offset(cx, cy),
        end = Offset(px, py),
        strokeWidth = 3f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawThermalOverloadSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    // Bimetal thermal heater arcs
    drawArc(
        color = if (elem.isTripped) IndustrialStopRed else ElectricAmber,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - 20f, cy - 8f),
        size = Size(18f, 18f),
        style = Stroke(2.5f)
    )
    drawArc(
        color = if (elem.isTripped) IndustrialStopRed else ElectricAmber,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx + 2f, cy - 8f),
        size = Size(18f, 18f),
        style = Stroke(2.5f)
    )

    if (elem.isTripped) {
        val res = textMeasurer.measure(
            text = "OVERLOAD",
            style = TextStyle(color = IndustrialStopRed, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        )
        drawText(res, topLeft = Offset(cx - res.size.width / 2f, y + h - 18f))
    }
}

private fun DrawScope.drawCoilSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    // Contactor Coil box
    val coilColor = if (elem.isEnergized) ElectricCyan else TextMuted
    drawCircle(
        color = coilColor,
        radius = 18f,
        center = Offset(cx, cy),
        style = Stroke(2.5f)
    )

    if (elem.isEnergized) {
        drawCircle(
            color = ElectricCyan.copy(alpha = 0.25f),
            radius = 24f,
            center = Offset(cx, cy)
        )
    }

    val res = textMeasurer.measure(
        text = if (elem.isEnergized) "ON" else "KM",
        style = TextStyle(
            color = if (elem.isEnergized) ElectricCyan else TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    )
    drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f))
}

private fun DrawScope.drawAuxContactSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f
    val isNo = elem.type == ComponentType.AUX_CONTACT_NO

    // Draw standard ladder schematic contact lines ||
    drawLine(color = ElectricCyan, start = Offset(cx - 8f, cy - 14f), end = Offset(cx - 8f, cy + 14f), strokeWidth = 2.5f)
    drawLine(color = ElectricCyan, start = Offset(cx + 8f, cy - 14f), end = Offset(cx + 8f, cy + 14f), strokeWidth = 2.5f)

    if (!isNo) {
        // NC slash /
        drawLine(color = ElectricCyan, start = Offset(cx - 12f, cy + 14f), end = Offset(cx + 12f, cy - 14f), strokeWidth = 2.5f)
    }
}

private fun DrawScope.drawTimerSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    drawRect(
        color = if (elem.isTimingActive) ElectricCyan else TextMuted,
        topLeft = Offset(cx - 16f, cy - 14f),
        size = Size(32f, 28f),
        style = Stroke(2f)
    )

    val secondsLeft = max(0f, elem.delaySeconds - (elem.elapsedTimerMs / 1000f))
    val timeText = if (elem.isTimingActive) "${"%.1f".format(secondsLeft)}s" else "${elem.delaySeconds.toInt()}s"

    val res = textMeasurer.measure(
        text = timeText,
        style = TextStyle(
            color = if (elem.isTimingActive) ElectricAmber else TextSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    )
    drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f))
}

private fun DrawScope.drawSensorSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    val color = if (elem.isSensorTriggered) ElectricYellow else TextSecondary
    drawCircle(color = color, radius = 14f, center = Offset(cx, cy), style = Stroke(2f))

    val label = when (elem.type) {
        ComponentType.SENSOR_LIMIT -> "LIM"
        ComponentType.SENSOR_PROXIMITY -> "PRX"
        ComponentType.SENSOR_PHOTOELECTRIC -> "OPT"
        ComponentType.SENSOR_FLOAT -> "FLT"
        ComponentType.SENSOR_PRESSURE -> "BAR"
        else -> "SNS"
    }

    val res = textMeasurer.measure(
        text = label,
        style = TextStyle(color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    )
    drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f))
}

private fun DrawScope.drawMotor3PhaseSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 8f

    val isRunning = elem.motorRotation != MotorRotation.STOPPED
    val motorColor = when {
        elem.isMotorStarting -> ElectricAmber
        isRunning -> IndustrialRunGreen
        else -> TextSecondary
    }

    // Outer stator circle
    drawCircle(color = motorColor, radius = 26f, center = Offset(cx, cy), style = Stroke(3f))

    // Rotating rotor lines
    val angle = if (isRunning) (System.currentTimeMillis() % 1000) / 1000f * 360f else 0f
    val rad = Math.toRadians(angle.toDouble())
    val rx1 = 18f * cos(rad).toFloat()
    val ry1 = 18f * sin(rad).toFloat()
    val rx2 = 18f * cos(rad + Math.PI / 2).toFloat()
    val ry2 = 18f * sin(rad + Math.PI / 2).toFloat()

    drawLine(color = motorColor, start = Offset(cx - rx1, cy - ry1), end = Offset(cx + rx1, cy + ry1), strokeWidth = 2f)
    drawLine(color = motorColor, start = Offset(cx - rx2, cy - ry2), end = Offset(cx + rx2, cy + ry2), strokeWidth = 2f)

    // Center "M 3~"
    val res = textMeasurer.measure(
        text = "M 3~",
        style = TextStyle(color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    )
    drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - res.size.height / 2f))

    // RPM readout below
    val rpmText = "${elem.motorRpm.toInt()} RPM"
    val rpmRes = textMeasurer.measure(
        text = rpmText,
        style = TextStyle(color = motorColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    )
    drawText(rpmRes, topLeft = Offset(cx - rpmRes.size.width / 2f, cy + 28f))
}

private fun DrawScope.drawSolenoidSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    // Valve hourglass symbol
    val path = Path().apply {
        moveTo(cx - 16f, cy - 12f)
        lineTo(cx + 16f, cy + 12f)
        lineTo(cx + 16f, cy - 12f)
        lineTo(cx - 16f, cy + 12f)
        close()
    }
    drawPath(path, color = if (elem.isEnergized) IndustrialRunGreen else TextSecondary, style = Stroke(2f))
}

private fun DrawScope.drawPilotLightSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    val baseColor = Color(elem.pilotColor.hex)
    drawCircle(color = baseColor, radius = 16f, center = Offset(cx, cy), style = Stroke(2.5f))

    // Cross inside circle
    drawLine(color = baseColor, start = Offset(cx - 11f, cy - 11f), end = Offset(cx + 11f, cy + 11f), strokeWidth = 2f)
    drawLine(color = baseColor, start = Offset(cx - 11f, cy + 11f), end = Offset(cx + 11f, cy - 11f), strokeWidth = 2f)

    if (elem.isPilotLit) {
        // Glowing halo
        drawCircle(color = baseColor.copy(alpha = 0.35f), radius = 24f, center = Offset(cx, cy))
    }
}

private fun DrawScope.drawBuzzerSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    // Half dome horn
    drawArc(
        color = if (elem.isBuzzerSounding) IndustrialStopRed else TextSecondary,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = true,
        topLeft = Offset(cx - 14f, cy - 14f),
        size = Size(28f, 28f)
    )

    if (elem.isBuzzerSounding) {
        // Acoustic wave arcs
        drawArc(
            color = IndustrialStopRed,
            startAngle = 210f,
            sweepAngle = 120f,
            useCenter = false,
            topLeft = Offset(cx - 22f, cy - 22f),
            size = Size(44f, 44f),
            style = Stroke(2f)
        )
    }
}

private fun DrawScope.drawMeterSymbol(
    elem: CircuitElement,
    x: Float, y: Float, w: Float, h: Float,
    textMeasurer: TextMeasurer
) {
    val cx = x + w / 2f
    val cy = y + h / 2f + 4f

    drawCircle(color = ElectricCyan, radius = 20f, center = Offset(cx, cy), style = Stroke(2f))

    val valueText = "${"%.1f".format(elem.meterReading)}"
    val res = textMeasurer.measure(
        text = valueText,
        style = TextStyle(color = TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    )
    drawText(res, topLeft = Offset(cx - res.size.width / 2f, cy - 10f))

    val unitRes = textMeasurer.measure(
        text = elem.meterUnit,
        style = TextStyle(color = ElectricCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    )
    drawText(unitRes, topLeft = Offset(cx - unitRes.size.width / 2f, cy + 4f))
}

/**
 * Touch Magnifier Loupe: Floating zoomed circular loupe showing target terminal coordinates.
 */
@Composable
private fun MagnifierLoupe(
    terminalLabel: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .size(90.dp)
            .shadow(12.dp, CircleShape)
            .border(2.dp, ElectricCyan, CircleShape),
        shape = CircleShape,
        color = CadSurface
    ) {
        Box(contentAlignment = Alignment.Center) {
            // Crosshair lines
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                drawLine(color = ElectricCyan.copy(alpha = 0.4f), start = Offset(cx, 0f), end = Offset(cx, size.height), strokeWidth = 1f)
                drawLine(color = ElectricCyan.copy(alpha = 0.4f), start = Offset(0f, cy), end = Offset(size.width, cy), strokeWidth = 1f)
                drawCircle(color = ElectricAmber, radius = 4f, center = Offset(cx, cy))
            }
            Text(
                text = terminalLabel,
                color = TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(CadDarkBackground.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Live Volt / Amp Measurement Tooltip popup on wire long-press.
 */
@Composable
private fun WireMeasurementTooltip(
    wire: Wire,
    screenPos: Offset,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .offset(
                x = (screenPos.x - 90f).coerceAtLeast(16f).dp,
                y = (screenPos.y - 85f).coerceAtLeast(60f).dp
            )
            .shadow(10.dp, RoundedCornerShape(10.dp))
            .border(1.dp, CadSurfaceVariant, RoundedCornerShape(10.dp)),
        shape = RoundedCornerShape(10.dp),
        color = CadSurface
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        when (wire.state) {
                            WireState.LIVE_AC -> WireLive.copy(alpha = 0.2f)
                            WireState.SHORT_CIRCUIT -> WireShort.copy(alpha = 0.2f)
                            else -> ElectricCyan.copy(alpha = 0.2f)
                        },
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ElectricBolt,
                    contentDescription = null,
                    tint = when (wire.state) {
                        WireState.LIVE_AC -> WireLive
                        WireState.SHORT_CIRCUIT -> WireShort
                        else -> ElectricCyan
                    },
                    modifier = Modifier.size(18.dp)
                )
            }

            Column {
                Text(
                    text = "${wire.state.name.replace("_", " ")} CONDUCTOR",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = when (wire.state) {
                        WireState.LIVE_AC -> WireLive
                        WireState.SHORT_CIRCUIT -> WireShort
                        else -> ElectricCyan
                    }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "V: ${"%.1f".format(wire.voltage)} V",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "I: ${"%.2f".format(wire.current)} A",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricAmber
                    )
                }
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = TextMuted, modifier = Modifier.size(16.dp))
            }
        }
    }
}

/**
 * Geometric helper to determine if tap point is near wire polyline segments.
 */
private fun isPointNearWire(point: Offset, waypoints: List<Offset>, threshold: Float): Boolean {
    if (waypoints.size < 2) return false
    for (i in 1 until waypoints.size) {
        val p1 = waypoints[i - 1]
        val p2 = waypoints[i]
        val dist = distanceToSegment(point, p1, p2)
        if (dist <= threshold) return true
    }
    return false
}

private fun distanceToSegment(p: Offset, v: Offset, w: Offset): Float {
    val l2 = (v - w).getDistanceSquared()
    if (l2 == 0f) return (p - v).getDistance()
    val t = ((p.x - v.x) * (w.x - v.x) + (p.y - v.y) * (w.y - v.y)) / l2
    val clampedT = t.coerceIn(0f, 1f)
    val projection = Offset(v.x + clampedT * (w.x - v.x), v.y + clampedT * (w.y - v.y))
    return (p - projection).getDistance()
}
