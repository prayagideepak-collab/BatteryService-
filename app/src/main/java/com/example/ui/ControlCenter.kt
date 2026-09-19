package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class HealthIssue(
    val id: String,
    val title: String,
    val priority: String, // P0 to P5
    val severityColor: Color,
    val diagnosis: String,
    val selfHealingStatus: String,
    val timestamp: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ControlCenter(onBack: () -> Unit) {
    var selectedIssue by remember { mutableStateOf<HealthIssue?>(null) }

    val mockIssues = remember {
        listOf(
            HealthIssue(
                id = "issue_01",
                title = "Charging telemetry calculation unavailable",
                priority = "P1",
                severityColor = Color(0xFFFF9800),
                diagnosis = "Insufficient authoritative current telemetry from system battery broadcast.",
                selfHealingStatus = "Automatic fallback engaged (Estimated Power Mode)",
                timestamp = "18:42"
            ),
            HealthIssue(
                id = "issue_02",
                title = "Database migration check",
                priority = "P0",
                severityColor = Color(0xFFE53935),
                diagnosis = "Schema version verified and auto-patched successfully.",
                selfHealingStatus = "Resolved via Verified Migration",
                timestamp = "12:15"
            ),
            HealthIssue(
                id = "issue_03",
                title = "Background sync pending",
                priority = "P3",
                severityColor = Color(0xFF2196F3),
                diagnosis = "Network connectivity temporarily restricted by Doze mode policy.",
                selfHealingStatus = "Scheduled for next connectivity window",
                timestamp = "09:30"
            )
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Netra Control Center", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Application Health & Self-Healing Hub", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Monitoring P0-P5 priority issues, autonomous mitigations, and secure verification logs.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            items(mockIssues) { issue ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("control_center_item_${issue.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, issue.severityColor.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .background(issue.severityColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(issue.priority, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = issue.severityColor)
                                }
                                Text(issue.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            Text(issue.timestamp, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Status: ${issue.selfHealingStatus}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                        Spacer(modifier = Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            OutlinedButton(
                                onClick = { selectedIssue = issue },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp)
                            ) {
                                Text("View Details", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    selectedIssue?.let { issue ->
        AlertDialog(
            onDismissRequest = { selectedIssue = null },
            title = { Text("[${issue.priority}] ${issue.title}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("AI Diagnosis Summary:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(issue.diagnosis, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Self-Healing Action:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(issue.selfHealingStatus, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Time Detected: ${issue.timestamp}", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedIssue = null }) {
                    Text("Close")
                }
            }
        )
    }
}
