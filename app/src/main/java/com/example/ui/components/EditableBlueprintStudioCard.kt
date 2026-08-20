package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.blueprint.BlueprintArtifactManager
import com.example.data.blueprint.BlueprintLifecycleState
import com.example.util.ModularReportType
import com.example.ui.theme.*
import java.io.File

@Composable
fun EditableBlueprintStudioCard(
    manager: BlueprintArtifactManager,
    modifier: Modifier = Modifier,
    onExportSuccess: ((File) -> Unit)? = null,
    onPdfExportRequested: ((ModularReportType) -> Unit)? = null
) {
    val artifact by manager.currentArtifact.collectAsState()
    val validationResult by manager.validationResult.collectAsState()
    val rawJsonText by manager.rawJsonText.collectAsState()
    val statusMessage by manager.statusMessage.collectAsState()

    var isEditingJson by remember { mutableStateOf(false) }
    var editedText by remember(rawJsonText) { mutableStateOf(rawJsonText) }
    var showExecuteConfirmation by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = AuraSurfaceVariant),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, AuraBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = "Blueprint Artifact",
                        tint = AuraPurple,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EDITABLE BLUEPRINT ARTIFACT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = AuraPurple
                    )
                }

                val isValid = validationResult?.isValid == true
                Surface(
                    color = if (isValid) Color(0xFF1E3A1E) else Color(0xFF4A1212),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isValid) "VALIDATED" else "INVALID",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isValid) Color(0xFF81C784) else Color(0xFFE57373),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Lifecycle State Indicator Badges
            Text(
                text = "LIFECYCLE STATE:",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = AuraOnSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                BlueprintLifecycleState.entries.forEach { state ->
                    val isActive = artifact?.lifecycleState == state
                    Surface(
                        color = if (isActive) AuraPurple else AuraSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(vertical = 6.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                text = state.name,
                                fontSize = 9.sp,
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (isActive) Color.White else AuraOnSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Metadata summary
            if (artifact != null) {
                Surface(
                    color = AuraSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Title: ${artifact!!.strategyBlueprint.title}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AuraOnSurface)
                        Text("Schema Version: ${artifact!!.schemaVersion} | Blueprint Version: ${artifact!!.blueprintVersion}", fontSize = 10.sp, color = AuraOnSurfaceVariant)
                        Text("Blueprint ID: ${artifact!!.blueprintId.take(12)}...", fontSize = 10.sp, color = AuraOnSurfaceVariant)
                        Text("Parent ID: ${artifact!!.parentBlueprintId ?: "None (Root)"}", fontSize = 10.sp, color = AuraOnSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status message feedback
            Text(
                text = statusMessage,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = AuraOnSurface
            )

            // Validation Errors / Warnings List
            val errors = validationResult?.errors ?: emptyList()
            val warnings = validationResult?.warnings ?: emptyList()

            if (errors.isNotEmpty() || warnings.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = Color(0xFF2B1F1F),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (errors.isNotEmpty()) {
                            Text("Validation Errors (${errors.size}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE57373))
                            errors.forEach { err ->
                                Text("• [${err.field}] ${err.message}", fontSize = 10.sp, color = Color(0xFFFFCDD2))
                            }
                        }
                        if (warnings.isNotEmpty()) {
                            if (errors.isNotEmpty()) Spacer(modifier = Modifier.height(4.dp))
                            Text("Validation Warnings (${warnings.size}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFD54F))
                            warnings.forEach { warn ->
                                Text("• [${warn.field}] ${warn.message}", fontSize = 10.sp, color = Color(0xFFFFECB3))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Primary File / Repo Actions Row
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    onClick = { manager.loadFromAsset("blueprints/sample_blueprint_v1.json") },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Load Repo Asset", fontSize = 10.sp)
                }

                OutlinedButton(
                    onClick = { 
                        manager.exportCurrentArtifact()?.let { file ->
                            onExportSuccess?.invoke(file)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export File", fontSize = 10.sp)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expandable Builder Instructions (AI Studio Prompts Sequence)
            var showBuilderInstructions by remember { mutableStateOf(false) }
            val builderSet = artifact?.builderInstructions ?: artifact?.strategyBlueprint?.builderInstructions

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { showBuilderInstructions = !showBuilderInstructions },
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                ) {
                    Icon(if (showBuilderInstructions) Icons.Outlined.ExpandLess else Icons.Outlined.SmartToy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (showBuilderInstructions) "Hide Instructions" else "View Instructions", fontSize = 11.sp)
                }

                if (builderSet != null) {
                    OutlinedButton(
                        onClick = { onPdfExportRequested?.invoke(ModularReportType.BUILDER_INSTRUCTIONS) },
                        modifier = Modifier.weight(0.8f),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export PDF", fontSize = 11.sp)
                    }
                }
            }

            if (showBuilderInstructions && builderSet != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = AuraSurface,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "AURA BUILDER INSTRUCTION GENERATOR OUTPUT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuraPurple
                        )
                        Text(
                            text = "Executable 1-9 sequence for Google AI Studio / Builder session:",
                            fontSize = 10.sp,
                            color = AuraOnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        builderSet.prompts.forEach { prompt ->
                            Surface(
                                color = AuraSurfaceVariant,
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(
                                        text = "PROMPT ${prompt.stepNumber} — ${prompt.title}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AuraPurple
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = prompt.promptText,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = AuraOnSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Expandable JSON Code Editor
            OutlinedButton(
                onClick = { isEditingJson = !isEditingJson },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
            ) {
                Icon(if (isEditingJson) Icons.Outlined.ExpandLess else Icons.Outlined.Code, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isEditingJson) "Hide JSON Editor" else "Open & Edit Blueprint JSON", fontSize = 11.sp)
            }

            if (isEditingJson) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = editedText,
                    onValueChange = { editedText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = AuraOnSurface
                    ),
                    label = { Text("Raw Blueprint Artifact JSON", fontSize = 10.sp) },
                    maxLines = 100
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { manager.updateAndValidateJson(editedText) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = AuraPurple)
                ) {
                    Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Validate & Load Edited JSON", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = AuraOnSurfaceVariant.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(8.dp))

            // State Transition Actions
            Text("LIFECYCLE ACTIONS:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = AuraOnSurfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { manager.transitionLifecycleState(BlueprintLifecycleState.VALIDATED) },
                    enabled = artifact?.lifecycleState == BlueprintLifecycleState.LOADED && validationResult?.isValid == true,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Validate", fontSize = 10.sp)
                }

                Button(
                    onClick = { manager.transitionLifecycleState(BlueprintLifecycleState.PROPOSED) },
                    enabled = artifact?.lifecycleState == BlueprintLifecycleState.VALIDATED,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Propose", fontSize = 10.sp)
                }

                Button(
                    onClick = { manager.transitionLifecycleState(BlueprintLifecycleState.APPROVED) },
                    enabled = artifact?.lifecycleState == BlueprintLifecycleState.PROPOSED,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Approve", fontSize = 10.sp)
                }

                Button(
                    onClick = { showExecuteConfirmation = true },
                    enabled = artifact?.lifecycleState == BlueprintLifecycleState.APPROVED,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    Text("Execute", fontSize = 10.sp)
                }
            }
        }
    }

    if (showExecuteConfirmation) {
        AlertDialog(
            onDismissRequest = { showExecuteConfirmation = false },
            title = { Text("Confirm Safe Strategy Execution") },
            text = {
                Text("This will transition the approved blueprint artifact to EXECUTED state. No unverified modifications will be executed automatically.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        manager.transitionLifecycleState(BlueprintLifecycleState.EXECUTED)
                        showExecuteConfirmation = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32))
                ) {
                    Text("Confirm Execution")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExecuteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
