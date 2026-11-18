package com.escrowmalawi.presentation.screens.verification

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.VerificationViewModel
import com.escrowmalawi.presentation.viewmodel.VerifyUiState

/**
 * Real identity verification: ID photo + selfie holding the ID, sent to a moderator who
 * approves or rejects it in the web console. Shows the request's live status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityVerificationScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
    viewModel: VerificationViewModel = hiltViewModel()
) {
    val context = LocalContext.current.applicationContext
    val verification by viewModel.verification.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    var step by remember { mutableIntStateOf(1) }
    var idPhoto by remember { mutableStateOf<Uri?>(null) }
    var selfie by remember { mutableStateOf<Uri?>(null) }

    val idPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) idPhoto = it }
    val selfiePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { if (it != null) selfie = it }
    val imageOnly = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Identity Verification", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark, titleContentColor = OnSurfaceDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        val v = verification
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                v == null -> CircularProgressIndicator(color = EscrowGreen)

                v.status == "approved" -> StatusPanel(
                    icon = Icons.Default.Verified, tint = EscrowGreen,
                    title = "You're verified",
                    body = "Your identity was confirmed. A verified badge now shows on your profile so buyers and sellers know you're a real person.",
                    action = "Done", onAction = onComplete
                )

                v.status == "pending" -> StatusPanel(
                    icon = Icons.Default.HourglassTop, tint = EscrowAmber,
                    title = "Under review",
                    body = "We received your photos. A moderator will review them soon. You'll get a notification when it's decided.",
                    action = "Back to profile", onAction = onComplete
                )

                else -> {
                    if (v.status == "rejected") {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = EscrowRed.copy(0.1f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Your last request was rejected", fontWeight = FontWeight.Bold, color = EscrowRed)
                                if (v.rejectReason.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(v.rejectReason, fontSize = 13.sp, color = OnSurfaceDark.copy(0.8f))
                                }
                                Spacer(Modifier.height(4.dp))
                                Text("You can submit new photos below.", fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
                            }
                        }
                    }

                    Text("Step $step of 3", color = EscrowGreen, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { step / 3f },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = EscrowGreen,
                        trackColor = OnSurfaceDark.copy(0.1f),
                    )
                    Spacer(Modifier.height(32.dp))

                    when (step) {
                        1 -> PhotoStep(
                            icon = Icons.Default.Badge,
                            title = "Photo of your ID",
                            body = "Upload a clear photo of your National ID or Passport. All four corners and the text must be readable.",
                            photo = idPhoto,
                            onPick = { idPicker.launch(imageOnly) },
                            nextLabel = "Next",
                            onNext = { step = 2 }
                        )
                        2 -> PhotoStep(
                            icon = Icons.Default.Face,
                            title = "Selfie holding your ID",
                            body = "Take a selfie in good light, holding the same ID next to your face. Take the photo with your camera first, then choose it here.",
                            photo = selfie,
                            onPick = { selfiePicker.launch(imageOnly) },
                            nextLabel = "Next",
                            onNext = { step = 3 },
                            onBackStep = { step = 1 }
                        )
                        else -> {
                            Text("Review and send", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurfaceDark)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Check both photos are clear. Only our moderators will see them, and they are used just to confirm who you are.",
                                textAlign = TextAlign.Center, color = OnSurfaceDark.copy(0.7f), lineHeight = 20.sp
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Thumb(idPhoto, "ID", Modifier.weight(1f))
                                Thumb(selfie, "Selfie", Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(24.dp))

                            (uiState as? VerifyUiState.Error)?.let {
                                Text(it.message, color = EscrowRed, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(12.dp))
                            }
                            (uiState as? VerifyUiState.Working)?.let {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = EscrowGreen)
                                    Spacer(Modifier.width(10.dp))
                                    Text(it.step, fontSize = 13.sp, color = OnSurfaceDark.copy(0.7f))
                                }
                                Spacer(Modifier.height(12.dp))
                            }

                            val working = uiState is VerifyUiState.Working
                            Button(
                                onClick = {
                                    val a = idPhoto; val b = selfie
                                    if (a != null && b != null) viewModel.submit(context, a, b)
                                },
                                enabled = !working && idPhoto != null && selfie != null,
                                modifier = Modifier.fillMaxWidth().height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                            ) { Text("Submit for review", fontWeight = FontWeight.Bold) }
                            TextButton(onClick = { viewModel.clearError(); step = 2 }, enabled = !working) {
                                Text("Back", color = OnSurfaceDark.copy(0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoStep(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    photo: Uri?,
    onPick: () -> Unit,
    nextLabel: String,
    onNext: () -> Unit,
    onBackStep: (() -> Unit)? = null
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (photo == null) {
            Icon(icon, null, modifier = Modifier.size(80.dp), tint = EscrowGreen)
        } else {
            AsyncImage(
                model = photo, contentDescription = title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(16.dp))
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = OnSurfaceDark)
        Spacer(Modifier.height(10.dp))
        Text(body, textAlign = TextAlign.Center, color = OnSurfaceDark.copy(0.7f), lineHeight = 20.sp)
        Spacer(Modifier.height(28.dp))

        OutlinedButton(
            onClick = onPick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (photo == null) "Choose a photo" else "Choose a different photo")
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onNext,
            enabled = photo != null,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
        ) { Text(nextLabel, fontWeight = FontWeight.Bold) }
        if (onBackStep != null) {
            TextButton(onClick = onBackStep) { Text("Back", color = OnSurfaceDark.copy(0.7f)) }
        }
    }
}

@Composable
private fun Thumb(uri: Uri?, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(
            model = uri, contentDescription = label, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().height(130.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceDark)
        )
        Spacer(Modifier.height(6.dp))
        Text(label, fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
    }
}

@Composable
private fun StatusPanel(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit
) {
    Spacer(Modifier.height(24.dp))
    Icon(icon, null, modifier = Modifier.size(88.dp), tint = tint)
    Spacer(Modifier.height(20.dp))
    Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OnSurfaceDark)
    Spacer(Modifier.height(10.dp))
    Text(body, textAlign = TextAlign.Center, color = OnSurfaceDark.copy(0.7f), lineHeight = 20.sp)
    Spacer(Modifier.height(32.dp))
    Button(
        onClick = onAction,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
    ) { Text(action, fontWeight = FontWeight.Bold) }
}
