package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Data model to track draggable component items on the screen safely
data class PlacedComponent(
    val id: String,
    val name: String,
    var position: Offset
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .systemBarsPadding(), // FIX 1: Pushes content down below system clock/corners
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainSimulationScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainSimulationScreen() {
    var isSimulating by remember { mutableStateOf(false) }
    val componentCatalog = listOf("Line (L1)", "Neutral (N)", "Generator", "Breaker 3P", "Fuse", "Pushbutton NO", "Contactor", "3Φ Motor")
    
    // Track active movable components placed on the circuit board grid
    val placedComponents = remember { mutableStateListOf<PlacedComponent>() }
    var nextId by remember { mutableStateOf(1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Electrical Sim", fontSize = 16.sp) },
                actions = {
                    IconButton(onClick = { placedComponents.clear() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Clear")
                    }
                    TextButton(onClick = { /* Open Gen sizing */ }) {
                        Text("GEN SIZE", color = Color.Cyan, fontSize = 14.sp)
                    }
                    Button(
                        onClick = { isSimulating = !isSimulating },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isSimulating) Color.Red else Color.Green),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Run", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(if (isSimulating) "STOP" else "RUN", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* AI Diagnosis */ },
                containerColor = Color(0xFF6200EE),
                contentColor = Color.White
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Build, contentDescription = "AI")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ask AI", fontSize = 12.sp)
                }
            }
        },
        bottomBar = {
            BottomAppBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentPadding = PaddingValues(4.dp),
                modifier = Modifier.height(90.dp)
            ) {
                Column {
                    Text("Catalog (Tap to Add)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        items(componentCatalog) { type ->
                            Card(
                                onClick = { 
                                    // Add item dynamically to the center of the viewport screen view
                                    placedComponents.add(PlacedComponent("id_$nextId", type, Offset(200f, 400f)))
                                    nextId++
                                },
                                shape = RoundedCornerShape(6.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(type, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(Color(0xFF121212))
        ) {
            // FIX 2: Custom grid layer that handles finger drag touch inputs cleanly
            InteractiveGridCanvas(
                components = placedComponents,
                onComponentMoved = { index, newOffset ->
                    placedComponents[index] = placedComponents[index].copy(position = newOffset)
                }
            )
        }
    }
}

@Composable
fun InteractiveGridCanvas(
    components: List<PlacedComponent>,
    onComponentMoved: (Int, Offset) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Draw Blueprint Grid Layout Background lines
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 60f
            for (x in 0..size.width.toInt() step gridSpacing.toInt()) {
                drawLine(Color(0xFF222222), Offset(x.toFloat(), 0f), Offset(x.toFloat(), size.height), 1f)
            }
            for (y in 0..size.height.toInt() step gridSpacing.toInt()) {
                drawLine(Color(0xFF222222), Offset(0f, y.toFloat()), Offset(size.width, y.toFloat()), 1f)
            }
        }

        // Render each component box dynamically onto the board
        components.forEachIndexed { index, component ->
            Box(
                modifier = Modifier
                    .offset(
                        x = (component.position.x / 3f).dp, // Translation scaling for mobile grids
                        y = (component.position.y / 3f).dp
                    )
                    .background(Color.DarkGray, RoundedCornerShape(4.dp))
                    .padding(8.dp)
                    .pointerInput(component.id) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val updatedPos = Offset(
                                component.position.x + dragAmount.x,
                                component.position.y + dragAmount.y
                            )
                            onComponentMoved(index, updatedPos)
                        }
                    }
            ) {
                Text(component.name, color = Color.White, fontSize = 11.sp)
            }
        }
    }
}
