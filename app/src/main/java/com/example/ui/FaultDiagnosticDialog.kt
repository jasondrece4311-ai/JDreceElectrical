package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.GeminiCircuitDiagnostician
import com.example.model.CircuitFault
import com.example.ui.theme.*

@Composable
fun FaultDiagnosticDialog(
    activeFaults: List<CircuitFault>,
    jsonSchemaText: String,
    report: GeminiCircuitDiagnostician.DiagnosticReport?,
    isLoadingAi: Boolean,
    onRunAiDiagnosis: () -> Unit,
    onDismiss: () -> Unit
) {
    var showJsonSchema by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadSurface,
        titleContentColor = TextPrimary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(
                                if (activeFaults.isNotEmpty()) IndustrialStopRed.copy(alpha = 0.2f) else AiGemini.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeFaults.isNotEmpty()) Icons.Default.Warning else Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (activeFaults.isNotEmpty()) IndustrialStopRed else AiGemini,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = if (activeFaults.isNotEmpty()) "Fault Diagnosis (${activeFaults.size})" else "Circuit AI Assistant",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Active Faults List
                if (activeFaults.isNotEmpty()) {
                    Text(
                        text = "Active Trip & Hazard Conditions",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndustrialStopRed
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        activeFaults.forEach { fault ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = CadDarkBackground,
                                border = BorderStroke(1.dp, IndustrialStopRed.copy(alpha = 0.4f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ReportProblem,
                                            contentDescription = null,
                                            tint = IndustrialStopRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = fault.title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = TextPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = fault.description,
                                        fontSize = 11.sp,
                                        color = TextSecondary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Standard Ref: ${fault.standardRef}",
                                        fontSize = 10.sp,
                                        color = ElectricAmber
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = IndustrialRunGreen.copy(alpha = 0.1f),
                        border = BorderStroke(1.dp, IndustrialRunGreen.copy(alpha = 0.3f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = IndustrialRunGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "No active faults detected. Continuous electrical paths are balanced and protected.",
                                fontSize = 12.sp,
                                color = TextPrimary
                            )
                        }
                    }
                }

                // AI Diagnosis Action Trigger
                Button(
                    onClick = onRunAiDiagnosis,
                    enabled = !isLoadingAi,
                    colors = ButtonDefaults.buttonColors(containerColor = AiGemini),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_diagnose_button")
                ) {
                    if (isLoadingAi) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gemini 2.5 Analyzing Schema...", fontSize = 13.sp)
                    } else {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyze Root Cause with Gemini AI", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Gemini Report View
                if (report != null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = CadDarkBackground,
                        border = BorderStroke(1.dp, AiGemini.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Psychology,
                                    contentDescription = null,
                                    tint = AiGeminiLight,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (report.isAiGenerated) "Gemini Root-Cause Report" else "Local Expert Electrical Assessment",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = AiGeminiLight
                                )
                            }

                            Text(
                                text = report.summary,
                                fontSize = 12.sp,
                                color = TextPrimary
                            )

                            if (report.rootCauses.isNotEmpty()) {
                                Text(
                                    text = "Failure Root Causes:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricAmber
                                )
                                report.rootCauses.forEach { cause ->
                                    Text("• $cause", fontSize = 11.sp, color = TextSecondary)
                                }
                            }

                            if (report.codeViolations.isNotEmpty()) {
                                Text(
                                    text = "Standards & Compliance (NEC / NFPA / IEC):",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ElectricCyan
                                )
                                report.codeViolations.forEach { std ->
                                    Text("• $std", fontSize = 11.sp, color = TextSecondary)
                                }
                            }

                            if (report.remediationSteps.isNotEmpty()) {
                                Text(
                                    text = "Recommended Electrician Actions:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = IndustrialRunGreen
                                )
                                report.remediationSteps.forEach { step ->
                                    Text("✔ $step", fontSize = 11.sp, color = TextPrimary)
                                }
                            }
                        }
                    }
                }

                // JSON Schema Collapsible Viewer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { showJsonSchema = !showJsonSchema }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (showJsonSchema) "Hide Circuit JSON Schema" else "View Canvas-to-JSON Schema",
                        fontSize = 11.sp,
                        color = ElectricCyan
                    )
                    Icon(
                        imageVector = if (showJsonSchema) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }

                AnimatedVisibility(visible = showJsonSchema) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = CadDarkBackground,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = jsonSchemaText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 9.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss", color = TextSecondary)
            }
        }
    )
}
