package com.escrowmalawi.presentation.screens.auth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.presentation.screens.auth.EscrowTextField
import coil.compose.AsyncImage
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.AuthViewModel
import org.web3j.crypto.Keys
import java.security.SecureRandom

@Composable
fun CompleteProfileScreen(
    onComplete: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val user by viewModel.currentUser.collectAsState()

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var profileUri by remember { mutableStateOf<Uri?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var walletCreated by remember { mutableStateOf(false) }
    var generatedAddress by remember { mutableStateOf("") }
    var generatedKey by remember { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        profileUri = uri
    }

    LaunchedEffect(user) {
        user?.let {
            name = it.name
            phone = it.phone
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Complete Your Profile", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = OnSurfaceDark)
        Text("Set up your identity and wallet to start trading", fontSize = 14.sp, color = OnSurfaceDark.copy(0.6f))

        Spacer(Modifier.height(32.dp))

        // Profile Picture
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(SurfaceDark)
                .clickable { photoPicker.launch("image/*") },
            contentAlignment = Alignment.Center
        ) {
            if (profileUri != null) {
                AsyncImage(
                    model = profileUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.AddAPhoto, null, tint = EscrowGreen, modifier = Modifier.size(40.dp))
            }
        }

        Spacer(Modifier.height(32.dp))

        EscrowTextField(value = name, onValueChange = { name = it }, label = "Full Name", leadingIcon = Icons.Default.Person)
        Spacer(Modifier.height(16.dp))
        EscrowTextField(value = phone, onValueChange = { phone = it }, label = "Phone Number", leadingIcon = Icons.Default.Phone)

        Spacer(Modifier.height(32.dp))

        // Wallet Creation Section
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = EscrowGreen)
                    Spacer(Modifier.width(8.dp))
                    Text("P2P Wallet", fontWeight = FontWeight.Bold)
                }

                if (!walletCreated) {
                    Text("You need a blockchain wallet to lock/receive funds.", fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
                    Button(
                        onClick = {
                            val ecKeyPair = Keys.createEcKeyPair()
                            generatedAddress = "0x" + Keys.getAddress(ecKeyPair)
                            generatedKey = ecKeyPair.privateKey.toString(16)
                            walletCreated = true
                        },
                        modifier = Modifier.padding(top = 12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen.copy(0.2f), contentColor = EscrowGreen)
                    ) {
                        Text("Generate New Wallet")
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text("Address: $generatedAddress", fontSize = 11.sp, color = EscrowGreen)
                    Text(
                        "Your wallet pays a tiny gas fee for each trade step. Send it a little Sepolia test ETH.",
                        fontSize = 11.sp,
                        color = OnSurfaceDark.copy(0.5f)
                    )
                    Text("✔ Wallet generated successfully", fontSize = 11.sp, color = OnSurfaceDark.copy(0.5f))
                }
            }
        }

        Spacer(Modifier.height(40.dp))

        Button(
            onClick = {
                isLoading = true
                viewModel.updateProfile(context, name, phone, profileUri, generatedAddress, generatedKey) {
                    isLoading = false
                    onComplete()
                }
            },
            enabled = !isLoading && name.isNotBlank() && phone.isNotBlank() && walletCreated,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = OnSurfaceDark, modifier = Modifier.size(24.dp))
            } else {
                Text("Save and Continue", fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}
