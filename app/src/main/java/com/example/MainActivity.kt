package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GeminiCircuitDiagnostician
import com.example.engine.CircuitSolver
import com.example.engine.GenSizerUtility
import com.example.model.*
import com.example.ui.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CircuitStudioApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircuitStudioApp() {
    val coroutineScope = rememberCoroutineScope()

    // 1. Core State
    val defaultTemplate = remember { WiringTemplates.getTemplates().first() }
    var elements: List<CircuitElement> by remember { mutableStateOf(defaultTemplate.elements) }
    var wires: List<Wire> by remember { mutableStateOf(defaultTemplate.wires) }

    var isRunning by remember { mutableStateOf(true) }
    var activeFaults by remember { mutableStateOf<List<CircuitFault>>(emptyList()) }
    var totalGenKva by remember { mutableStateOf(0f) }
    var genLoadPercent by remember { mutableStateOf(0f) }

    // Selected Element for Radial Menu & Configuration
    var selectedElementId by remember { mutableStateOf<String?>(null) }
    val selectedElement = elements.firstOrNull { it.id == selectedElementId }

    // Dialog & Drawer Visibility State
    var showBottomCatalog by remember { mutableStateOf(false) }
    var showGenSizerDialog by remember { mutableStateOf(false) }
    var showFaultsAiDialog by remember { mutableStateOf(false) }
    var editingElement by remember { mutableStateOf<CircuitElement?>(null) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    // AI State
    var aiDiagnosticReport by remember { mutableStateOf<GeminiCircuitDiagnostician.DiagnosticReport?>(null) }
    var isLoadingAi by remember { mutableStateOf(false) }

    // 2. Simulation Loop (Boolean Continuity & Load Physics Solver)
    LaunchedEffect(isRunning, elements, wires) {
        while (isRunning) {
            val result = CircuitSolver.solve(elements, wires, deltaTimeMs = 100L)
            elements = result.updatedElements
            wires = result.updatedWires
            activeFaults = result.activeFaults
            totalGenKva = result.totalGenKva
            genLoadPercent = result.genLoadPercent
            delay(100L)
        }
    }

    // 3. User Interaction Handlers
    fun toggleElementState(target: CircuitElement) {
        elements = elements.map { elem ->
            if (elem.id == target.id) {
                when (elem.type) {
                    ComponentType.BREAKER_1P, ComponentType.BREAKER_3P -> {
                        if (elem.isTripped) {
                            elem.copy(isTripped = false, isClosed = true)
                        } else {
                            elem.copy(isClosed = !elem.isClosed)
                        }
                    }
                    ComponentType.FUSE -> {
                        if (elem.isBlown) {
                            elem.copy(isBlown = false) // Replace blown fuse
                        } else elem
                    }
                    ComponentType.ESTOP -> {
                        elem.copy(isLatched = !elem.isLatched) // Latch or Twist-to-Reset
                    }
                    ComponentType.PUSHBUTTON_NO -> {
                        elem.copy(isPressed = !elem.isPressed)
                    }
                    ComponentType.PUSHBUTTON_NC -> {
                        elem.copy(isPressed = !elem.isPressed)
                    }
                    ComponentType.SELECTOR_2POS -> {
                        elem.copy(selectorPosition = if (elem.selectorPosition == 0) 1 else 0)
                    }
                    ComponentType.SELECTOR_3POS -> {
                        elem.copy(selectorPosition = (elem.selectorPosition + 1) % 3)
                    }
                    ComponentType.THERMAL_OVERLOAD -> {
                        if (elem.isTripped) {
                            elem.copy(isTripped = false)
                        } else {
                            elem.copy(isTripped = true) // Test trip
                        }
                    }
                    ComponentType.GENERATOR -> {
                        if (elem.isGenStalled) {
                            elem.copy(isGenStalled = false, isGenRunning = true)
                        } else {
                            elem.copy(isGenRunning = !elem.isGenRunning)
                        }
                    }
                    ComponentType.SENSOR_LIMIT, ComponentType.SENSOR_PROXIMITY,
                    ComponentType.SENSOR_PHOTOELECTRIC, ComponentType.SENSOR_FLOAT,
                    ComponentType.SENSOR_PRESSURE -> {
                        elem.copy(isSensorTriggered = !elem.isSensorTriggered)
                    }
                    else -> elem
                }
            } else elem
        }
    }

    fun deleteElement(target: CircuitElement) {
        elements = elements.filterNot { it.id == target.id }
        wires = wires.filterNot { it.fromElementId == target.id || it.toElementId == target.id }
        selectedElementId = null
    }

    fun rotateElement(target: CircuitElement) {
        elements = elements.map { elem ->
            if (elem.id == target.id) {
                elem.copy(rotation = (elem.rotation + 90) % 360)
            } else elem
        }
    }

    fun autoCleanLayout() {
        // Automatically arranges components into neat ladder diagram standard columns & rows
        val spacingY = 120f
        var currentY = 50f

        val power = elements.filter { it.type.category == ComponentCategory.POWER }
        val protection = elements.filter { it.type.category == ComponentCategory.PROTECT }
        val logic = elements.filter { it.type.category == ComponentCategory.LOGIC }
        val sensors = elements.filter { it.type.category == ComponentCategory.SENSORS }
        val outputs = elements.filter { it.type.category == ComponentCategory.OUTPUTS }
        val meters = elements.filter { it.type.category == ComponentCategory.METERS }

        val newPositions = mutableMapOf<String, Pair<Float, Float>>()

        fun placeRow(rowElements: List<CircuitElement>, y: Float) {
            var currentX = 60f
            for (e in rowElements) {
                newPositions[e.id] = Pair(currentX, y)
                currentX += e.width + 40f
            }
        }

        placeRow(power, currentY)
        currentY += spacingY
        placeRow(protection, currentY)
        currentY += spacingY
        placeRow(logic + sensors, currentY)
        currentY += spacingY
        placeRow(outputs, currentY)
        currentY += spacingY
        placeRow(meters, currentY)

        elements = elements.map { e ->
            val pos = newPositions[e.id]
            if (pos != null) e.copy(x = pos.first, y = pos.second) else e
        }
    }

    fun addWireBetween(fromElemId: String, fromTermId: String, toElemId: String, toTermId: String) {
        val newWire = Wire(
            id = "w_${System.currentTimeMillis()}",
            fromElementId = fromElemId,
            fromTerminalId = fromTermId,
            toElementId = toElemId,
            toTerminalId = toTermId
        )
        wires = wires + newWire
    }

    // 4. Mobile Scaffold Layout
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        topBar = {
            TopBarControls(
                isRunning = isRunning,
                onToggleRunStop = { isRunning = !isRunning },
                onClear = { showClearConfirmDialog = true },
                onAutoCleanLayout = { autoCleanLayout() },
                onOpenGenSizer = { showGenSizerDialog = true },
                activeFaultCount = activeFaults.size
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Floating Component Library Trigger
                FloatingActionButton(
                    onClick = { showBottomCatalog = true },
                    containerColor = CadSurfaceVariant,
                    contentColor = ElectricCyan,
                    modifier = Modifier.testTag("open_catalog_fab")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Component")
                }

                // Floating [💡 Ask AI] FAB with Fault Badge
                BadgedBox(
                    badge = {
                        if (activeFaults.isNotEmpty()) {
                            Badge(
                                containerColor = IndustrialStopRed,
                                contentColor = Color.White
                            ) {
                                Text("${activeFaults.size}")
                            }
                        }
                    }
                ) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            showFaultsAiDialog = true
                        },
                        containerColor = if (activeFaults.isNotEmpty()) IndustrialStopRed else AiGemini,
                        contentColor = Color.White,
                        icon = {
                            Icon(
                                imageVector = if (activeFaults.isNotEmpty()) Icons.Default.Warning else Icons.Default.AutoAwesome,
                                contentDescription = "Ask AI"
                            )
                        },
                        text = {
                            Text(
                                text = if (activeFaults.isNotEmpty()) "Faults (${activeFaults.size})" else "Ask AI",
                                fontWeight = FontWeight.Bold
                            )
                        },
                        modifier = Modifier.testTag("ask_ai_fab")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Interactive Electrical CAD Canvas
            CircuitCanvasView(
                elements = elements,
                wires = wires,
                selectedElementId = selectedElementId,
                onSelectElement = { elem -> selectedElementId = elem?.id },
                onToggleElement = { elem -> toggleElementState(elem) },
                onMoveElement = { id, dx, dy ->
                    elements = elements.map {
                        if (it.id == id) it.copy(x = it.x + dx, y = it.y + dy) else it
                    }
                },
                onAddWire = { fE, fT, tE, tT -> addWireBetween(fE, fT, tE, tT) }
            )

            // Canvas Element Radial Menu (Delete, Rotate, Toggle, Configure)
            if (selectedElement != null) {
                RadialMenu(
                    element = selectedElement,
                    canvasScale = 1.0f,
                    canvasPanX = 0f,
                    canvasPanY = 0f,
                    onDelete = { deleteElement(it) },
                    onRotate = { rotateElement(it) },
                    onToggle = { toggleElementState(it) },
                    onEditProperties = { editingElement = it },
                    onDismiss = { selectedElementId = null }
                )
            }

            // Bottom Drawer: Component Library Catalog & Wiring Templates
            if (showBottomCatalog) {
                BottomDrawerCatalog(
                    onSelectComponent = { compType ->
                        val newElem = ComponentFactory.createComponent(
                            type = compType,
                            x = 150f + (elements.size % 4) * 30f,
                            y = 150f + (elements.size % 4) * 30f
                        )
                        elements = elements + newElem
                    },
                    onSelectTemplate = { template ->
                        elements = template.elements
                        wires = template.wires
                        selectedElementId = null
                    },
                    onDismiss = { showBottomCatalog = false }
                )
            }

            // Generator Sizer Dialog
            if (showGenSizerDialog) {
                val sizerReport = remember(elements) {
                    GenSizerUtility.calculateSizing(elements)
                }
                GenSizerDialog(
                    report = sizerReport,
                    onDismiss = { showGenSizerDialog = false }
                )
            }

            // Fault Diagnostics & Gemini AI Modal
            if (showFaultsAiDialog) {
                val jsonSchema = remember(elements, wires, activeFaults, totalGenKva) {
                    GeminiCircuitDiagnostician.serializeCanvasToJson(elements, wires, activeFaults, totalGenKva)
                }
                FaultDiagnosticDialog(
                    activeFaults = activeFaults,
                    jsonSchemaText = jsonSchema,
                    report = aiDiagnosticReport,
                    isLoadingAi = isLoadingAi,
                    onRunAiDiagnosis = {
                        coroutineScope.launch {
                            isLoadingAi = true
                            aiDiagnosticReport = GeminiCircuitDiagnostician.diagnose(
                                elements, wires, activeFaults, totalGenKva
                            )
                            isLoadingAi = false
                        }
                    },
                    onDismiss = { showFaultsAiDialog = false }
                )
            }

            // Component Configuration Dialog
            editingElement?.let { target ->
                ComponentPropertiesDialog(
                    element = target,
                    allElements = elements,
                    onSave = { updated ->
                        elements = elements.map { if (it.id == updated.id) updated else it }
                        editingElement = null
                    },
                    onDismiss = { editingElement = null }
                )
            }

            // Clear Confirmation Dialog
            if (showClearConfirmDialog) {
                AlertDialog(
                    onDismissRequest = { showClearConfirmDialog = false },
                    containerColor = CadSurface,
                    title = { Text("Clear Canvas?", color = TextPrimary) },
                    text = { Text("All components and wiring will be removed.", color = TextSecondary) },
                    confirmButton = {
                        Button(
                            onClick = {
                                elements = emptyList<CircuitElement>()
                                wires = emptyList<Wire>()
                                selectedElementId = null
                                showClearConfirmDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = IndustrialStopRed)
                        ) {
                            Text("Clear All")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showClearConfirmDialog = false }) {
                            Text("Cancel", color = TextMuted)
                        }
                    }
                )
            }
        }
    }
}
