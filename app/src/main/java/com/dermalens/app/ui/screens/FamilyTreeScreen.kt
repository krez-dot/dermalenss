package com.dermalens.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.dermalens.app.ui.LocalAppSettings
import kotlin.math.cos
import kotlin.math.sin

/**
 * A small, original, Canvas-drawn schematic representing one [LesionIconType] -- not a real
 * clinical photo. See LesionIconType's own doc for why: almost every dermatology reference photo
 * available is copyrighted commercial stock, not something to embed in a shipped app without a
 * license. This conveys just the one key visual trait each relative is actually distinguished by,
 * on a plain skin-tone circle.
 */
@Composable
private fun LesionSchematicIcon(type: LesionIconType, modifier: Modifier = Modifier) {
    val skinTone = Color(0xFFE8C4A0)
    Canvas(modifier = modifier.size(44.dp)) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = skinTone, radius = r, center = center)

        when (type) {
            LesionIconType.DARK_DOT -> {
                drawCircle(color = Color(0xFF3E2723), radius = r * 0.16f, center = center)
            }
            LesionIconType.PALE_BUMP -> {
                drawCircle(color = Color(0xFFFFF3E0), radius = r * 0.42f, center = center)
                drawCircle(color = Color(0xFFE0B896), radius = r * 0.42f, center = center, style = Stroke(width = 1.5f))
            }
            LesionIconType.RED_BUMP -> {
                drawCircle(color = Color(0xFFE57373), radius = r * 0.42f, center = center)
            }
            LesionIconType.PUS_BUMP -> {
                drawCircle(color = Color(0xFFE57373), radius = r * 0.46f, center = center)
                drawCircle(color = Color(0xFFFFF9C4), radius = r * 0.20f, center = center)
            }
            LesionIconType.DEEP_BUMP -> {
                drawCircle(color = Color(0xFFAD4E42), radius = r * 0.58f, center = center)
                drawCircle(color = Color(0xFF7B241C), radius = r * 0.58f, center = center, style = Stroke(width = 1.5f))
            }
            LesionIconType.SCALE_PATCH -> {
                // Redesigned after live-testing showed the first version (two same-tone circles)
                // was too subtle against the skin-tone base to read as anything. Dusty-rose color
                // gives real contrast (inflamed patches genuinely do redden), an irregular jittered
                // outline reads as "patch" rather than "bump," and thick pale flake strokes on top
                // give visible scaling/crust texture instead of faint hairline marks.
                val points = 8
                val path = androidx.compose.ui.graphics.Path()
                for (i in 0 until points) {
                    val angle = (i.toFloat() / points) * 2 * Math.PI.toFloat()
                    val jitterR = r * (0.55f + if (i % 3 == 0) 0.1f else -0.05f)
                    val point = Offset(center.x + cos(angle) * jitterR, center.y + sin(angle) * jitterR)
                    if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                path.close()
                drawPath(path, color = Color(0xFFD98B7A))
                repeat(5) { i ->
                    val angle = i * 1.26f
                    val start = Offset(center.x + cos(angle) * r * 0.15f, center.y + sin(angle) * r * 0.15f)
                    val end = Offset(center.x + cos(angle) * r * 0.42f, center.y + sin(angle) * r * 0.42f)
                    drawLine(color = Color(0xFFFDF3E7), start = start, end = end, strokeWidth = 3.5f)
                }
            }
            LesionIconType.RING -> {
                drawCircle(color = Color(0xFFE57373), radius = r * 0.55f, center = center, style = Stroke(width = r * 0.16f))
            }
            LesionIconType.ROUGH_BUMP -> {
                val points = 9
                val path = androidx.compose.ui.graphics.Path()
                for (i in 0 until points) {
                    val angle = (i.toFloat() / points) * 2 * Math.PI.toFloat()
                    val jitterR = r * (0.38f + if (i % 2 == 0) 0.08f else 0f)
                    val point = Offset(center.x + cos(angle) * jitterR, center.y + sin(angle) * jitterR)
                    if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
                }
                path.close()
                drawPath(path, color = Color(0xFFC49A78))
            }
            LesionIconType.FLAT_PATCH -> {
                drawCircle(color = Color(0xFF8D6E63).copy(alpha = 0.55f), radius = r * 0.6f, center = center)
            }
        }
    }
}

/**
 * Static reference screen showing a detected condition's real clinical relatives/subtypes --
 * education, not a detection capability. Nothing here implies the app can tell these apart on a
 * scan; that distinction matters since it's shown right after a real detection result.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyTreeScreen(navController: NavController, condition: String) {
    val settings = LocalAppSettings.current
    val tree = familyTrees[condition]
    // Same accent color Scan Result and Progress Tracker already use for this exact condition
    // (mockDetectionResults is the shared source of truth for it) -- previously this screen used
    // a fixed generic blue/purple regardless of which condition brought you here, breaking the
    // color-coding thread that ties the rest of the app together.
    val conditionColor = mockDetectionResults.find { it.condition == condition }?.color ?: DermaGreen

    Scaffold(
        topBar = {
            DermaGlassTopBar(
                title = "$condition Family Tree",
                onBack = { navController.popBackStack() },
                titleColor = settings.textPrimary
            )
        }
    ) { innerPadding ->
        if (tree == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No family tree reference is available for $condition yet.",
                    fontSize = settings.textMd.sp,
                    color = Color(0xFF6B7280),
                    modifier = Modifier.padding(24.dp)
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
                .background(if (settings.highContrast) Color.White else Color(0xFFF8F9FA)),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                ResultCard(icon = Icons.Default.Info, iconBg = conditionColor.copy(alpha = 0.1f), iconTint = conditionColor, title = "Why these are grouped together") {
                    Text(tree.groupingNote, fontSize = settings.textMd.sp, color = Color(0xFF374151), lineHeight = 22.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Reference only -- this app doesn't detect which of these a photo shows. See a dermatologist for an actual diagnosis.",
                    fontSize = settings.textSm.sp,
                    color = Color(0xFF9CA3AF),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            items(tree.relatives) { relative ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) {
                    // A simple branch indicator -- a dot and a short stem -- gives the "tree" a
                    // visual identity without needing a full custom-drawn diagram.
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp).padding(top = 18.dp)) {
                        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(conditionColor))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(2.dp)
                    ) {
                        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                            LesionSchematicIcon(
                                type = relative.icon,
                                modifier = Modifier.semantics { contentDescription = "Schematic illustration, not a photo" }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(relative.name, fontSize = settings.textMd.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF111827))
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(relative.description, fontSize = settings.textSm.sp, color = Color(0xFF4B5563), lineHeight = 18.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row {
                                    Icon(Icons.Default.AccountTree, contentDescription = null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(relative.distinguishingFeature, fontSize = settings.textSm.sp, color = Color(0xFF6B7280), lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
