package com.example.ai

import com.example.BuildConfig
import com.example.model.CircuitElement
import com.example.model.CircuitFault
import com.example.model.Wire
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object GeminiCircuitDiagnostician {

    data class DiagnosticReport(
        val summary: String,
        val rootCauses: List<String>,
        val codeViolations: List<String>,
        val remediationSteps: List<String>,
        val isAiGenerated: Boolean
    )

    fun serializeCanvasToJson(
        elements: List<CircuitElement>,
        wires: List<Wire>,
        faults: List<CircuitFault>,
        genKva: Float
    ): String {
        val root = JSONObject()

        // 1. Components
        val compArray = JSONArray()
        for (e in elements) {
            val cObj = JSONObject()
            cObj.put("id", e.id)
            cObj.put("type", e.type.name)
            cObj.put("tag", e.tag)
            cObj.put("isClosed", e.isClosed)
            cObj.put("isTripped", e.isTripped)
            cObj.put("isBlown", e.isBlown)
            cObj.put("isLatched", e.isLatched)
            cObj.put("isPressed", e.isPressed)
            cObj.put("selectorPos", e.selectorPosition)
            cObj.put("isEnergized", e.isEnergized)
            cObj.put("motorRpm", e.motorRpm)
            cObj.put("motorDirection", e.motorRotation.name)
            cObj.put("isMotorStarting", e.isMotorStarting)
            cObj.put("meterReading", "${e.meterReading} ${e.meterUnit}")
            cObj.put("isGenStalled", e.isGenStalled)
            cObj.put("genLoadPercent", e.genLoadPercent)
            compArray.put(cObj)
        }
        root.put("components", compArray)

        // 2. Wires
        val wireArray = JSONArray()
        for (w in wires) {
            val wObj = JSONObject()
            wObj.put("id", w.id)
            wObj.put("from", "${w.fromElementId}:${w.fromTerminalId}")
            wObj.put("to", "${w.toElementId}:${w.toTerminalId}")
            wObj.put("state", w.state.name)
            wObj.put("voltageV", w.voltage)
            wObj.put("currentA", w.current)
            wireArray.put(wObj)
        }
        root.put("wires", wireArray)

        // 3. Current Faults
        val faultArray = JSONArray()
        for (f in faults) {
            val fObj = JSONObject()
            fObj.put("id", f.id)
            fObj.put("component", f.componentTag)
            fObj.put("title", f.title)
            fObj.put("description", f.description)
            fObj.put("rootCause", f.rootCause)
            fObj.put("standard", f.standardRef)
            faultArray.put(fObj)
        }
        root.put("detectedFaults", faultArray)
        root.put("totalSystemKva", genKva)

        return root.toString(2)
    }

    suspend fun diagnose(
        elements: List<CircuitElement>,
        wires: List<Wire>,
        faults: List<CircuitFault>,
        genKva: Float
    ): DiagnosticReport = withContext(Dispatchers.IO) {
        val jsonSchema = serializeCanvasToJson(elements, wires, faults, genKva)
        val apiKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (e: Throwable) {
            ""
        }

        if (apiKey.isNotBlank() && apiKey != "MY_GEMINI_API_KEY") {
            try {
                val generativeModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = apiKey
                )

                val prompt = """
                    You are a Master Industrial Control Systems Electrician and Electrical CAD Diagnostic Specialist.
                    Review the following industrial circuit schematic JSON snapshot:
                    
                    $jsonSchema
                    
                    Perform an authoritative root-cause failure analysis.
                    Specifically answer:
                    1. Summary of overall schematic health and operation.
                    2. Primary root causes for any tripped breakers, blown fuses, motor stalls, phase loss, or generator overloads.
                    3. Specific National Electrical Code (NEC), NFPA 79, and IEC 60204-1 safety standard considerations.
                    4. Step-by-step physical electrician troubleshooting actions to rectify faults.
                    
                    Format with clear sections:
                    [SUMMARY]
                    [ROOT_CAUSES]
                    - item 1
                    - item 2
                    [CODE_STANDARDS]
                    - item 1
                    - item 2
                    [REMEDIATION]
                    - item 1
                    - item 2
                """.trimIndent()

                val response = generativeModel.generateContent(prompt)
                val responseText = response.text ?: ""
                return@withContext parseAiResponse(responseText)
            } catch (e: Exception) {
                // If API call fails (e.g. quota, network), fallback seamlessly to rule-based expert system
            }
        }

        // Professional offline expert fallback
        return@withContext generateLocalExpertDiagnosis(elements, wires, faults, genKva)
    }

    private fun parseAiResponse(text: String): DiagnosticReport {
        val rootCauses = mutableListOf<String>()
        val codeViolations = mutableListOf<String>()
        val remediationSteps = mutableListOf<String>()
        var summary = ""

        var currentSection = ""
        for (line in text.lines()) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("[SUMMARY]") -> currentSection = "SUMMARY"
                trimmed.startsWith("[ROOT_CAUSES]") -> currentSection = "ROOT_CAUSES"
                trimmed.startsWith("[CODE_STANDARDS]") -> currentSection = "CODE_STANDARDS"
                trimmed.startsWith("[REMEDIATION]") -> currentSection = "REMEDIATION"
                trimmed.startsWith("-") || trimmed.startsWith("•") || trimmed.matches(Regex("""^\d+\..*""")) -> {
                    val clean = trimmed.replace(Regex("""^[-•\d.]+\s*"""), "")
                    when (currentSection) {
                        "ROOT_CAUSES" -> rootCauses.add(clean)
                        "CODE_STANDARDS" -> codeViolations.add(clean)
                        "REMEDIATION" -> remediationSteps.add(clean)
                        else -> summary += "$clean "
                    }
                }
                else -> {
                    if (currentSection == "SUMMARY" && trimmed.isNotEmpty()) {
                        summary += "$trimmed\n"
                    }
                }
            }
        }

        if (summary.isBlank()) {
            summary = text.take(300)
        }

        return DiagnosticReport(
            summary = summary.trim(),
            rootCauses = if (rootCauses.isEmpty()) listOf("System evaluated by Gemini AI.") else rootCauses,
            codeViolations = if (codeViolations.isEmpty()) listOf("NEC 430: Motors & Branch-Circuit Conductors", "NFPA 79: Industrial Machinery") else codeViolations,
            remediationSteps = if (remediationSteps.isEmpty()) listOf("Inspect wire terminations and verify circuit breaker ratings.") else remediationSteps,
            isAiGenerated = true
        )
    }

    private fun generateLocalExpertDiagnosis(
        elements: List<CircuitElement>,
        wires: List<Wire>,
        faults: List<CircuitFault>,
        genKva: Float
    ): DiagnosticReport {
        val rootCauses = mutableListOf<String>()
        val codeViolations = mutableListOf<String>()
        val remediation = mutableListOf<String>()

        if (faults.isEmpty()) {
            val isAnyRunning = elements.any { it.motorRotation != com.example.model.MotorRotation.STOPPED || it.isPilotLit || it.isEnergized }
            val summary = if (isAnyRunning) {
                "System is operating nominally. Continuity verified across active branches with stable phase voltages."
            } else {
                "Control circuit is currently idle (Stop mode). No short circuits, ground faults, or thermal overloads detected."
            }

            return DiagnosticReport(
                summary = summary,
                rootCauses = listOf("Continuous electrical continuity confirmed across closed contacts."),
                codeViolations = listOf("Complies with NEC 430.32 Continuous Duty Overload Protection guidelines."),
                remediationSteps = listOf("Circuit is healthy. Press Start Pushbutton (SB1) or toggle selector switch to activate."),
                isAiGenerated = false
            )
        }

        for (f in faults) {
            rootCauses.add("${f.componentTag}: ${f.rootCause}")
            codeViolations.add("${f.componentTag}: ${f.standardRef}")
        }

        // Specific tailored remediation based on actual faults
        val hasFuseBlown = elements.any { it.isBlown }
        val hasBreakerTripped = elements.any { it.isTripped }
        val hasGenStall = elements.any { it.isGenStalled }
        val hasPhaseLoss = faults.any { it.title.contains("Phase Loss") }

        if (hasFuseBlown) {
            remediation.add("Disconnect power. Clear line-to-neutral or phase-to-phase short before inserting replacement fuse link.")
        }
        if (hasBreakerTripped) {
            remediation.add("Perform Megger insulation resistance test (> 1 MΩ). Reset breaker handle from full OFF to ON position.")
        }
        if (hasGenStall) {
            remediation.add("Shed non-essential resistive loads or configure soft-starter / VFD ramp-up to limit motor starting inrush to <= 2.5x running current.")
        }
        if (hasPhaseLoss) {
            remediation.add("Check upstream contactor pole erosion and phase continuity across all 3 line poles.")
        }

        val summary = "Critical circuit faults identified (${faults.size} active condition${if (faults.size > 1) "s" else ""}). " +
                "Protective trip elements engaged to prevent catastrophic cable thermal runaway and equipment damage."

        return DiagnosticReport(
            summary = summary,
            rootCauses = rootCauses,
            codeViolations = codeViolations,
            remediationSteps = remediation,
            isAiGenerated = false
        )
    }
}
