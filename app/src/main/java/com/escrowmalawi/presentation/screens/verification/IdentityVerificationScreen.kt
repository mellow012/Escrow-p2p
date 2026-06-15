package com.escrowmalawi.presentation.screens.verification

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.escrowmalawi.presentation.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityVerificationScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit
) {
    var currentStep by remember { mutableStateOf(1) }
    var isLoading by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identity Verification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BackgroundDark,
                    titleContentColor = OnSurfaceDark
                )
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ── Progress Header ──────────────────────────────────────────
            Text(
                "Step $currentStep of 3",
                color = EscrowGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { currentStep / 3f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(SurfaceDark, RoundedCornerShape(4.dp)),
                color = EscrowGreen,
                trackColor = OnSurfaceDark.copy(0.1f),
            )

            Spacer(Modifier.height(40.dp))

            when (currentStep) {
                1 -> VerificationStep1 { currentStep = 2 }
                2 -> VerificationStep2 { currentStep = 3 }
                3 -> VerificationStep3(isLoading) {
                    isLoading = true
                    // Simulate processing
                    onComplete()
                }
            }
        }
    }
}

@Composable
private fun VerificationStep1(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Badge,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = EscrowGreen
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Upload ID Document",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceDark
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "We need to verify your identity to ensure a safe trading environment. Please upload a clear photo of your National ID or Passport.",
            textAlign = TextAlign.Center,
            color = OnSurfaceDark.copy(0.7f),
            lineHeight = 20.sp
        )
        
        Spacer(Modifier.height(40.dp))

        VerificationOption(Icons.Default.PhotoCamera, "Take a Photo") { onNext() }
        Spacer(Modifier.height(12.dp))
        VerificationOption(Icons.Default.CloudUpload, "Upload from Gallery") { onNext() }
    }
}

@Composable
private fun VerificationStep2(onNext: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Face,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = EscrowGreen
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Liveness Check",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceDark
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Position your face within the frame and follow the instructions to confirm you are a real person.",
            textAlign = TextAlign.Center,
            color = OnSurfaceDark.copy(0.7f),
            lineHeight = 20.sp
        )
        
        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Start Camera", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VerificationStep3(isLoading: Boolean, onFinish: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = EscrowGreen
        )
        Spacer(Modifier.height(24.dp))
        Text(
            "Review & Submit",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurfaceDark
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "By submitting, you agree that the information provided is accurate and belongs to you.",
            textAlign = TextAlign.Center,
            color = OnSurfaceDark.copy(0.7f),
            lineHeight = 20.sp
        )
        
        Spacer(Modifier.height(40.dp))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = EscrowGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("ID Document Uploaded", color = OnSurfaceDark)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, null, tint = EscrowGreen, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("Face Scan Completed", color = OnSurfaceDark)
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen),
            shape = RoundedCornerShape(16.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
            } else {
                Text("Submit Verification", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VerificationOption(icon: ImageVector, title: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(64.dp),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OnSurfaceDark.copy(0.1f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceDark)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = EscrowGreen)
            Spacer(Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceDark.copy(0.3f))
        }
    }
}
