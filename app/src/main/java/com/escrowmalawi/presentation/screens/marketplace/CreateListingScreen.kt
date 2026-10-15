package com.escrowmalawi.presentation.screens.marketplace

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.escrowmalawi.data.model.ListingCategory
import com.escrowmalawi.presentation.theme.*
import com.escrowmalawi.presentation.viewmodel.ListingUiState
import com.escrowmalawi.presentation.viewmodel.MarketplaceViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListingScreen(
    onCreated: () -> Unit,
    onBack   : () -> Unit,
    viewModel: MarketplaceViewModel = hiltViewModel()
) {
    val context   = LocalContext.current
    val uiState   by viewModel.uiState.collectAsState()

    var title       by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priceText   by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var category    by remember { mutableStateOf(ListingCategory.OTHER) }
    var expanded    by remember { mutableStateOf(false) }
    var imageUris   by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var isLoading   by remember { mutableStateOf(false) }
    var uploadPercent by remember { mutableIntStateOf(0) }
    var errorMsg    by remember { mutableStateOf<String?>(null) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { imageUris = imageUris + it }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { imageUris = imageUris + it }
    }

    LaunchedEffect(Unit) {
        viewModel.uiState.collect { state ->
            when (state) {
                is ListingUiState.Loading        -> { isLoading = true; errorMsg = null }
                is ListingUiState.UploadProgress -> { isLoading = true; uploadPercent = state.percent }
                is ListingUiState.Success        -> { isLoading = false; viewModel.resetState(); onCreated() }
                is ListingUiState.Error          -> { isLoading = false; errorMsg = state.message }
                else                             -> isLoading = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Listing", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark, titleContentColor = OnSurfaceDark)
            )
        },
        containerColor = BackgroundDark
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {

            // ── Image section ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Photos", fontWeight = FontWeight.Bold, color = OnSurfaceDark, fontSize = 15.sp)
                        Text(
                            "${imageUris.size}/5 added",
                            fontSize = 11.sp,
                            color = OnSurfaceDark.copy(0.4f)
                        )
                    }
                    if (imageUris.isNotEmpty()) {
                        TextButton(
                            onClick = { imageUris = emptyList() },
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) {
                            Text("Clear all", color = EscrowRed.copy(0.8f), fontSize = 12.sp)
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(110.dp)
                ) {
                    // Existing image thumbnails
                    imageUris.forEach { uri ->
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(uri).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Remove button
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(Color.Black.copy(0.65f))
                                    .clickable { imageUris = imageUris.filter { it != uri } },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    // Add photo button
                    if (imageUris.size < 5) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(BackgroundDark)
                                .border(1.5.dp, OnSurfaceDark.copy(0.12f), RoundedCornerShape(14.dp))
                                .clickable(enabled = !isLoading) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    } else {
                                        galleryLauncher.launch("image/*")
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(EscrowGreen.copy(0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.AddPhotoAlternate, null, tint = EscrowGreen, modifier = Modifier.size(20.dp))
                                }
                                Text("Add photo", color = OnSurfaceDark.copy(0.4f), fontSize = 10.sp)
                            }
                        }
                    }
                }

                // Upload progress
                if (isLoading && uploadPercent > 0) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Uploading photos…", fontSize = 12.sp, color = EscrowGreen)
                            Text("$uploadPercent%", fontSize = 12.sp, color = OnSurfaceDark.copy(0.4f))
                        }
                        LinearProgressIndicator(
                            progress = { uploadPercent.toFloat() / 100f },
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
                            color = EscrowGreen,
                            trackColor = BackgroundDark
                        )
                    }
                }
            }

            HorizontalDivider(color = OnSurfaceDark.copy(0.06f))

            // ── Form ─────────────────────────────────────────────────────
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                SectionLabel("Item Details")

                FormField(
                    value = title,
                    onValueChange = { title = it; errorMsg = null },
                    label = "Title",
                    icon  = Icons.Default.Title,
                    hint  = "e.g. iPhone 13 Pro 256GB"
                )

                FormField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Description",
                    icon  = Icons.Default.Description,
                    hint  = "Describe the item's condition, features, location…",
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.height(130.dp)
                )

                FormField(
                    value = priceText,
                    onValueChange = { priceText = it.filter { c -> c.isDigit() || c == '.' }; errorMsg = null },
                    label = "Price (MWK)",
                    icon  = Icons.Default.AttachMoney,
                    hint  = "0",
                    keyboardType = KeyboardType.Decimal
                )

                FormField(
                    value = quantityText,
                    onValueChange = { quantityText = it.filter { c -> c.isDigit() }; errorMsg = null },
                    label = "Quantity",
                    icon  = Icons.Default.Inventory,
                    hint  = "1",
                    keyboardType = KeyboardType.Number
                )

                SectionLabel("Category")

                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = category.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        leadingIcon = { Icon(categoryIcon(category.name), null, tint = EscrowGreen) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        shape  = RoundedCornerShape(14.dp),
                        colors = fieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                        modifier = Modifier.background(SurfaceDark)
                    ) {
                        ListingCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Icon(categoryIcon(cat.name), null, tint = EscrowGreen, modifier = Modifier.size(18.dp))
                                        Text(cat.label, color = OnSurfaceDark)
                                    }
                                },
                                onClick = { category = cat; expanded = false }
                            )
                        }
                    }
                }

                // Error message
                errorMsg?.let { msg ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = EscrowRed.copy(0.08f)),
                        shape  = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = EscrowRed, modifier = Modifier.size(18.dp))
                            Text(msg, color = EscrowRed, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Submit button
                Button(
                    onClick = {
                        val price = priceText.toDoubleOrNull()
                        val quantity = quantityText.toIntOrNull() ?: 1
                        if (price == null || price <= 0) {
                            errorMsg = "Enter a valid price"
                            return@Button
                        }
                        if (quantity <= 0) {
                            errorMsg = "Quantity must be at least 1"
                            return@Button
                        }
                        viewModel.createListing(
                            context     = context,
                            title       = title,
                            description = description,
                            price       = price,
                            quantity    = quantity,
                            category    = category,
                            imageUris   = imageUris
                        )
                    },
                    enabled  = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape  = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = EscrowGreen)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (uploadPercent > 0) "Uploading $uploadPercent%…" else "Posting…",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Icon(Icons.Default.Publish, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Post Listing", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize   = 12.sp,
        color      = OnSurfaceDark.copy(0.4f),
        letterSpacing = 1.sp
    )
}

@Composable
private fun FormField(
    value        : String,
    onValueChange: (String) -> Unit,
    label        : String,
    icon         : androidx.compose.ui.graphics.vector.ImageVector,
    hint         : String        = "",
    keyboardType : KeyboardType  = KeyboardType.Text,
    minLines     : Int           = 1,
    maxLines     : Int           = 1,
    modifier     : Modifier      = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(hint, color = OnSurfaceDark.copy(0.25f)) },
        leadingIcon = { Icon(icon, null, tint = EscrowGreen) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        minLines = minLines,
        maxLines = maxLines,
        modifier = modifier.fillMaxWidth(),
        shape  = RoundedCornerShape(14.dp),
        colors = fieldColors()
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = EscrowGreen,
    unfocusedBorderColor    = OnSurfaceDark.copy(0.15f),
    focusedLabelColor       = EscrowGreen,
    cursorColor             = EscrowGreen,
    focusedContainerColor   = SurfaceDark,
    unfocusedContainerColor = SurfaceDark
)

private fun categoryIcon(category: String): androidx.compose.ui.graphics.vector.ImageVector =
    when (category) {
        ListingCategory.ELECTRONICS.name -> Icons.Default.PhoneAndroid
        ListingCategory.CLOTHING.name    -> Icons.Default.Checkroom
        ListingCategory.FOOD.name        -> Icons.Default.LocalGroceryStore
        ListingCategory.SERVICES.name    -> Icons.Default.Build
        ListingCategory.VEHICLES.name    -> Icons.Default.DirectionsCar
        ListingCategory.PROPERTY.name    -> Icons.Default.Home
        ListingCategory.AGRICULTURE.name -> Icons.Default.Grass
        else                             -> Icons.Default.Category
    }