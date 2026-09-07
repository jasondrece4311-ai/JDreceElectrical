package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CircuitElement
import com.example.model.ComponentType
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun RadialMenu(
    element: CircuitElement?,
    canvasScale: Float,
    canvasPanX: Float,
    canvasPanY: Float,
    onDelete: (CircuitElement) -> Unit,
    onRotate: (CircuitElement) -> Unit,
    onToggle: (CircuitElement) -> Unit,
    onEditProperties: (CircuitElement) -> Unit,
    onDismiss: () -> Unit
) {
    if (element == null) return

    // Position of element on screen
    val screenX = (element.x + element.width / 2f) * canvasScale + canvasPanX
    val screenY = (element.y - 45f) * canvasScale + canvasPanY

    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            // Radial pill container above element
            Surface(
                modifier = Modifier
                    .offset { IntOffset(screenX.roundToInt() - 120, screenY.roundToInt() - 60) }
                    .shadow(12.dp, CircleShape)
                    .border(1.dp, CadSurfaceVariant, CircleShape),
                shape = CircleShape,
                color = CadSurface
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Action 1: Toggle / Trigger / Reset
                    val toggleLabel = when (element.type) {
                        ComponentType.BREAKER_1P, ComponentType.BREAKER_3P -> if (element.isTripped) "Reset" else if (element.isClosed) "Open" else "Close"
                        ComponentType.FUSE -> if (element.isBlown) "Replace" else "Test"
                        ComponentType.ESTOP -> if (element.isLatched) "Release" else "Latch"
                        ComponentType.PUSHBUTTON_NO, ComponentType.PUSHBUTTON_NC -> "Press"
                        ComponentType.SELECTOR_2POS, ComponentType.SELECTOR_3POS -> "Switch"
                        ComponentType.THERMAL_OVERLOAD -> if (element.isTripped) "Reset" else "Trip"
                        ComponentType.SENSOR_LIMIT, ComponentType.SENSOR_PROXIMITY,
                        ComponentType.SENSOR_PHOTOELECTRIC, ComponentType.SENSOR_FLOAT,
                        ComponentType.SENSOR_PRESSURE -> "Trigger"
                        else -> "Toggle"
                    }

                    RadialActionButton(
                        icon = Icons.Default.PowerSettingsNew,
                        color = ElectricAmber,
                        label = toggleLabel,
                        onClick = { onToggle(element) }
                    )

                    // Action 2: Rotate 90°
                    RadialActionButton(
                        icon = Icons.Default.RotateRight,
                        color = ElectricCyan,
                        label = "Rotate",
                        onClick = { onRotate(element) }
                    )

                    // Action 3: Edit / Link Properties
                    RadialActionButton(
                        icon = Icons.Default.Settings,
                        color = AiGeminiLight,
                        label = "Configure",
                        onClick = { onEditProperties(element) }
                    )

                    // Action 4: Delete
                    RadialActionButton(
                        icon = Icons.Default.Delete,
                        color = IndustrialStopRed,
                        label = "Delete",
                        onClick = { onDelete(element) }
                    )

                    // Close menu
                    RadialActionButton(
                        icon = Icons.Default.Close,
                        color = TextMuted,
                        label = "Close",
                        onClick = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun RadialActionButton(
    icon: ImageVector,
    color: Color,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(CircleShape)
            .clickable { onClick() }
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(color.copy(alpha = 0.15f), CircleShape)
                .border(1.dp, color.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = label,
            fontSize = 10.sp,
            color = TextSecondary,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
