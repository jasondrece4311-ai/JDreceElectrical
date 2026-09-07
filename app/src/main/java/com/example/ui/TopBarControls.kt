package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun TopBarControls(
    isRunning: Boolean,
    onToggleRunStop: () -> Unit,
    onClear: () -> Unit,
    onAutoCleanLayout: () -> Unit,
    onOpenGenSizer: () -> Unit,
    activeFaultCount: Int = 0
) {
    val runButtonBg by animateColorAsState(
        targetValue = if (isRunning) IndustrialRunGreen else IndustrialStopRed,
        label = "runStopBg"
    )

    Surface(
        color = CadSurface,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Group: Clear & Auto-Clean Layout
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = onClear,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("clear_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = TextSecondary
                    ),
                    border = ButtonDefaults.outlinedButtonBorder.copy(width = 1.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Clear",
                        modifier = Modifier.size(16.dp),
                        tint = TextSecondary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onAutoCleanLayout,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("autoclean_button"),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ElectricCyan
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = "Auto-Clean",
                        modifier = Modifier.size(16.dp),
                        tint = ElectricCyan
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto-Clean", fontSize = 12.sp)
                }
            }

            // Right Group: Gen Sizer & RUN/STOP Toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Gen Sizer Button
                Button(
                    onClick = onOpenGenSizer,
                    modifier = Modifier
                        .height(38.dp)
                        .testTag("gen_sizer_button"),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CadSurfaceVariant,
                        contentColor = ElectricAmber
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = "Gen Sizer",
                        modifier = Modifier.size(18.dp),
                        tint = ElectricAmber
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Gen Sizer",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // RUN / STOP Master Toggle
                Box(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(runButtonBg)
                        .clickable { onToggleRunStop() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .testTag("run_stop_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isRunning) Icons.Default.PlayArrow else Icons.Default.Stop,
                            contentDescription = if (isRunning) "Run Active" else "Stop Active",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (isRunning) "RUN" else "STOP",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
