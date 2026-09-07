package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.*
import com.example.ui.theme.*

enum class CatalogTab {
    CATALOG,
    TEMPLATES
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomDrawerCatalog(
    onSelectComponent: (ComponentType) -> Unit,
    onSelectTemplate: (WiringTemplates.Template) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(CatalogTab.CATALOG) }
    var selectedCategory by remember { mutableStateOf(ComponentCategory.POWER) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = CadSurface,
        dragHandle = {
            BottomSheetDefaults.DragHandle(color = TextMuted)
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Header with Tab selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedTab == CatalogTab.CATALOG) "Component Library" else "Standard Schematics",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                // Tab Switcher Pill
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = CadSurfaceVariant
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        FilterChip(
                            selected = selectedTab == CatalogTab.CATALOG,
                            onClick = { selectedTab = CatalogTab.CATALOG },
                            label = { Text("Catalog", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = AiGemini,
                                selectedLabelColor = Color.White
                            ),
                            border = null
                        )
                        FilterChip(
                            selected = selectedTab == CatalogTab.TEMPLATES,
                            onClick = { selectedTab = CatalogTab.TEMPLATES },
                            label = { Text("Templates", fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan,
                                selectedLabelColor = Color.Black
                            ),
                            border = null
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (selectedTab == CatalogTab.CATALOG) {
                // Category Filter Pills
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ComponentCategory.values().forEach { cat ->
                        val isSelected = cat == selectedCategory
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) AiGemini.copy(alpha = 0.2f) else CadSurfaceVariant)
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) AiGemini else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedCategory = cat }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = cat.title,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) AiGeminiLight else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Components Grid for chosen category
                val componentsInCategory = ComponentType.values().filter { it.category == selectedCategory }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                ) {
                    items(componentsInCategory) { compType ->
                        ComponentGridCard(
                            type = compType,
                            onClick = {
                                onSelectComponent(compType)
                                onDismiss()
                            }
                        )
                    }
                }
            } else {
                // Wiring Templates List
                val templates = WiringTemplates.getTemplates()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    templates.forEach { tmpl ->
                        TemplateCard(
                            template = tmpl,
                            onClick = {
                                onSelectTemplate(tmpl)
                                onDismiss()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ComponentGridCard(
    type: ComponentType,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = CadSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("catalog_item_${type.name}")
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(CadDarkBackground, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (type.category) {
                        ComponentCategory.POWER -> Icons.Default.Bolt
                        ComponentCategory.PROTECT -> Icons.Default.Security
                        ComponentCategory.LOGIC -> Icons.Default.AccountTree
                        ComponentCategory.SENSORS -> Icons.Default.Sensors
                        ComponentCategory.OUTPUTS -> Icons.Default.ElectricMeter
                        ComponentCategory.METERS -> Icons.Default.Speed
                    },
                    contentDescription = null,
                    tint = when (type.category) {
                        ComponentCategory.POWER -> ElectricAmber
                        ComponentCategory.PROTECT -> IndustrialStopRed
                        ComponentCategory.LOGIC -> ElectricCyan
                        ComponentCategory.SENSORS -> ElectricYellow
                        ComponentCategory.OUTPUTS -> IndustrialRunGreen
                        ComponentCategory.METERS -> AiGeminiLight
                    },
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.displayName,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Tap to place",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: WiringTemplates.Template,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = CadSurfaceVariant,
        border = BorderStroke(1.dp, CadSurfaceVariant.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(ElectricCyan.copy(alpha = 0.15f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Schema,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(24.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = template.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = template.description,
                    fontSize = 11.sp,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${template.elements.size} Components",
                        fontSize = 10.sp,
                        color = ElectricAmber
                    )
                    Text(
                        text = "${template.wires.size} Wires",
                        fontSize = 10.sp,
                        color = ElectricCyan
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextMuted
            )
        }
    }
}
