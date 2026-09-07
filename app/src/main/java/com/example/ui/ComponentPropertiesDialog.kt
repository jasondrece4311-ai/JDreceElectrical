package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun ComponentPropertiesDialog(
    element: CircuitElement,
    allElements: List<CircuitElement>,
    onSave: (CircuitElement) -> Unit,
    onDismiss: () -> Unit
) {
    var tag by remember { mutableStateOf(element.tag) }
    var motorKw by remember { mutableStateOf(element.motorKw) }
    var ratingAmps by remember { mutableStateOf(element.ratingAmps) }
    var timerSeconds by remember { mutableStateOf(element.delaySeconds) }
    var selectedColor by remember { mutableStateOf(element.pilotColor) }
    var linkedCoilId by remember { mutableStateOf(element.linkedCoilId) }

    val availableCoils = allElements.filter {
        it.type == ComponentType.CONTACTOR_COIL || it.type == ComponentType.CONTROL_RELAY
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadSurface,
        titleContentColor = TextPrimary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = ElectricCyan)
                Text("Configure ${element.tag}", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = tag,
                    onValueChange = { tag = it },
                    label = { Text("Component Tag / Identifier") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = CadSurfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Specific properties per component type
                when (element.type) {
                    ComponentType.MOTOR_3PHASE -> {
                        Text("Motor Power Rating (kW):", fontSize = 12.sp, color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(2.2f, 4.0f, 5.5f, 7.5f, 11.0f).forEach { kw ->
                                FilterChip(
                                    selected = motorKw == kw,
                                    onClick = { motorKw = kw },
                                    label = { Text("${kw}kW", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricCyan,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                    ComponentType.BREAKER_1P, ComponentType.BREAKER_3P, ComponentType.FUSE -> {
                        Text("Rated Current (Amps):", fontSize = 12.sp, color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(10f, 16f, 25f, 32f, 63f).forEach { amps ->
                                FilterChip(
                                    selected = ratingAmps == amps,
                                    onClick = { ratingAmps = amps },
                                    label = { Text("${amps.toInt()}A", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = ElectricAmber,
                                        selectedLabelColor = Color.Black
                                    )
                                )
                            }
                        }
                    }
                    ComponentType.AUX_CONTACT_NO, ComponentType.AUX_CONTACT_NC -> {
                        Text("Link to Master Coil:", fontSize = 12.sp, color = TextSecondary)
                        if (availableCoils.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                availableCoils.forEach { coil ->
                                    FilterChip(
                                        selected = linkedCoilId == coil.id,
                                        onClick = { linkedCoilId = coil.id },
                                        label = { Text(coil.tag, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = AiGemini,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        } else {
                            Text("No contactor coils in circuit yet.", fontSize = 11.sp, color = TextMuted)
                        }
                    }
                    ComponentType.TIMER_ON_DELAY, ComponentType.TIMER_OFF_DELAY -> {
                        Text("Timer Delay: ${timerSeconds.toInt()}s", fontSize = 12.sp, color = TextSecondary)
                        Slider(
                            value = timerSeconds,
                            onValueChange = { timerSeconds = it },
                            valueRange = 1f..15f,
                            steps = 13,
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricCyan,
                                activeTrackColor = ElectricCyan
                            )
                        )
                    }
                    ComponentType.PILOT_LIGHT -> {
                        Text("Indicator Color:", fontSize = 12.sp, color = TextSecondary)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            LightColor.values().forEach { col ->
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(col.hex))
                                        .border(
                                            width = if (selectedColor == col) 3.dp else 1.dp,
                                            color = if (selectedColor == col) Color.White else CadSurfaceVariant,
                                            shape = CircleShape
                                        )
                                        .clickable { selectedColor = col }
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = element.copy(
                        tag = tag,
                        motorKw = motorKw,
                        motorRunningCurrentAmps = motorKw * 2.0f,
                        ratingAmps = ratingAmps,
                        delaySeconds = timerSeconds,
                        pilotColor = selectedColor,
                        linkedCoilId = linkedCoilId
                    )
                    onSave(updated)
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan)
            ) {
                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        }
    )
}
