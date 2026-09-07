package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GenSizingReport
import com.example.ui.theme.*

@Composable
fun GenSizerDialog(
    report: GenSizingReport,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CadSurface,
        titleContentColor = TextPrimary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(ElectricAmber.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = ElectricAmber,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text("Generator Sizing Engine", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Key Recommendation Highlight Box
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CadDarkBackground,
                    border = BorderStroke(1.dp, ElectricAmber.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "RECOMMENDED GENSET SIZE",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ElectricAmber
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${"%.1f".format(report.recommendedGenKva)} kVA",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                            Text(
                                text = "(${ "%.1f".format(report.recommendedGenKw) } kW)",
                                fontSize = 16.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(bottom = 3.dp)
                            )
                        }
                        Text(
                            text = "Includes 25% continuous safety headroom",
                            fontSize = 11.sp,
                            color = IndustrialRunGreen,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }

                // Electrical Load Breakdown Cards
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = "Continuous Load",
                        value = "${"%.2f".format(report.totalRunningKw)} kW",
                        subtext = "${"%.2f".format(report.totalRunningKva)} kVA",
                        modifier = Modifier.weight(1f),
                        accentColor = ElectricCyan
                    )
                    MetricBox(
                        title = "Peak Starting Inrush",
                        value = "${"%.1f".format(report.peakStartingKva)} kVA",
                        subtext = "6x Motor Inrush",
                        modifier = Modifier.weight(1f),
                        accentColor = IndustrialStopRed
                    )
                }

                // Engineering Standards & Calculation Notes
                Text(
                    text = "Load Profile & Calculations",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    report.notes.forEach { note ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("•", color = ElectricAmber, fontSize = 12.sp)
                            Text(
                                text = note,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = AiGemini)
            ) {
                Text("Close", color = Color.White)
            }
        }
    )
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    subtext: String,
    modifier: Modifier = Modifier,
    accentColor: Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = CadSurfaceVariant,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 11.sp, color = TextSecondary)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = accentColor)
            Text(subtext, fontSize = 10.sp, color = TextMuted)
        }
    }
}
