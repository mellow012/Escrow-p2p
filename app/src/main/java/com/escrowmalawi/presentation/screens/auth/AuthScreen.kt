package com.escrowmalawi.presentation.screens.auth


import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.AuthUiState
import com.escrowmalawi.presentation.viewmodel.AuthViewModel

// ════════════════════════════════════════════════════════════════════════
//  REGISTER SCREEN
// ════════════════════════════════════════════════════════════════════════

@Composable
fun RegisterScreen(
    onRegistered: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is AuthUiState.Success -> { viewModel.resetState(); onRegistered() }
            is AuthUiState.NeedsVerification -> { /* Handled in UI below */ }
            is AuthUiState.Error   -> { errorMessage = s.message }
            else -> {}
        }
    }

    if (uiState is AuthUiState.NeedsVerification) {
        VerificationDialog(
            email = email,
            onResend = { viewModel.resendVerification() },
            onCheck = { viewModel.checkVerification() },
            onDismiss = { viewModel.resetState() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Header gradient strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(EscrowGreenDark, BackgroundDark)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))

            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = EscrowGreen,
                modifier = Modifier.size(56.dp)
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "EscrowMalawi",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )
            Text(
                text = "Create your account",
                fontSize = 14.sp,
                color = OnSurfaceDark.copy(alpha = 0.6f)
            )

            Spacer(Modifier.height(40.dp))

            // ── Name ────────────────────────────────────────────────────
            EscrowTextField(
                value = name,
                onValueChange = { name = it },
                label = "Full Name",
                leadingIcon = Icons.Default.Person
            )

            Spacer(Modifier.height(12.dp))

            // ── Email ────────────────────────────────────────────────────
            EscrowTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email Address",
                leadingIcon = Icons.Default.Email,
                keyboardType = KeyboardType.Email
            )

            Spacer(Modifier.height(12.dp))

            // ── Phone ────────────────────────────────────────────────────
            EscrowTextField(
                value = phone,
                onValueChange = { phone = it },
                label = "Phone Number",
                leadingIcon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone
            )

            Spacer(Modifier.height(12.dp))

            // ── Password ─────────────────────────────────────────────────
            EscrowTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordToggle = { passwordVisible = !passwordVisible }
            )

            Spacer(Modifier.height(12.dp))

            // ── Confirm Password ──────────────────────────────────────────
            EscrowTextField(
                value = confirmPassword,
                onValueChange = { confirmPassword = it },
                label = "Confirm Password",
                leadingIcon = Icons.Default.LockOpen,
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordToggle = { passwordVisible = !passwordVisible }
            )

            Spacer(Modifier.height(8.dp))

            errorMessage?.let {
                Text(it, color = EscrowRed, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(24.dp))

            // ── Register Button ──────────────────────────────────────────
            Button(
                onClick = {
                    errorMessage = null
                    if (password != confirmPassword) {
                        errorMessage = "Passwords do not match"
                    } else {
                        viewModel.register(name, email, phone, password)
                    }
                },
                enabled = uiState !is AuthUiState.Loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Create Account", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = onNavigateToLogin) {
                Text("Already have an account? ", color = OnSurfaceDark.copy(0.6f))
                Text("Log in", color = EscrowGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  LOGIN SCREEN
// ════════════════════════════════════════════════════════════════════════

@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState) {
        when (val s = uiState) {
            is AuthUiState.Success -> { viewModel.resetState(); onLoggedIn() }
            is AuthUiState.NeedsVerification -> { /* Handled in UI */ }
            is AuthUiState.Error   -> errorMessage = s.message
            else -> {}
        }
    }

    if (uiState is AuthUiState.NeedsVerification) {
        VerificationDialog(
            email = email,
            onResend = { viewModel.resendVerification() },
            onCheck = { viewModel.checkVerification() },
            onDismiss = { viewModel.resetState() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = null,
                tint = EscrowGreen,
                modifier = Modifier.size(64.dp)
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Welcome back",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceDark
            )
            Text(
                text = "Sign in to your account",
                fontSize = 14.sp,
                color = OnSurfaceDark.copy(alpha = 0.55f)
            )

            Spacer(Modifier.height(40.dp))

            EscrowTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email Address",
                leadingIcon = Icons.Default.Email,
                keyboardType = KeyboardType.Email
            )

            Spacer(Modifier.height(12.dp))

            EscrowTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                leadingIcon = Icons.Default.Lock,
                isPassword = true,
                passwordVisible = passwordVisible,
                onPasswordToggle = { passwordVisible = !passwordVisible }
            )

            Spacer(Modifier.height(8.dp))

            errorMessage?.let {
                Text(it, color = EscrowRed, fontSize = 13.sp)
                Spacer(Modifier.height(4.dp))
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = { errorMessage = null; viewModel.login(email, password) },
                enabled = uiState !is AuthUiState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
            ) {
                if (uiState is AuthUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Sign In", fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(onClick = onNavigateToRegister) {
                Text("Don't have an account? ", color = OnSurfaceDark.copy(0.6f))
                Text("Register", color = EscrowGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  VERIFICATION DIALOG
// ════════════════════════════════════════════════════════════════════════

@Composable
fun VerificationDialog(
    email: String,
    onResend: () -> Unit,
    onCheck: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verify your Email") },
        text = {
            Column {
                Text("A verification link has been sent to $email. Please check your inbox and click the link to activate your account.")
                Spacer(Modifier.height(8.dp))
                Text("Once done, click 'I have verified' below.", fontSize = 12.sp, color = OnSurfaceDark.copy(0.6f))
            }
        },
        confirmButton = {
            Button(onClick = onCheck, colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)) {
                Text("I have verified")
            }
        },
        dismissButton = {
            TextButton(onClick = onResend) {
                Text("Resend Email", color = EscrowGreen)
            }
        },
        containerColor = SurfaceDark,
        titleContentColor = OnSurfaceDark,
        textContentColor = OnSurfaceDark.copy(0.8f)
    )
}

// ════════════════════════════════════════════════════════════════════════
//  SHARED COMPOSABLE: EscrowTextField
// ════════════════════════════════════════════════════════════════════════

@Composable
fun EscrowTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onPasswordToggle: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(leadingIcon, contentDescription = null, tint = EscrowGreen) },
        trailingIcon = if (isPassword && onPasswordToggle != null) {
            {
                IconButton(onClick = onPasswordToggle) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = "Toggle password",
                        tint = OnSurfaceDark.copy(0.5f)
                    )
                }
            }
        } else null,
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation()
        else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = EscrowGreen,
            unfocusedBorderColor = OnSurfaceDark.copy(0.2f),
            focusedLabelColor = EscrowGreen,
            cursorColor = EscrowGreen
        )
    )
}
