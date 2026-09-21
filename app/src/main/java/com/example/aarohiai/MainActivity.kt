@file:Suppress("DEPRECATION")

package com.example.aarohiai

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil.compose.AsyncImage
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.result.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// --- Custom Dark Cyberpunk Theme Palette ---
val DarkBase = Color(0xFF070B14)
val DarkSurface = Color(0xFF0D1527)
val DarkCard = Color(0xFF131E36)
val DarkElevated = Color(0xFF1B2A4A)
val CardBorder = Color(0x1F38BDF8)
val CyanAccent = Color(0xFF00F7FF)
val CyanGlow = Color(0xFF22D3EE)
val EmeraldAccent = Color(0xFF34D399)
val RoseAccent = Color(0xFFF43F5E)
val AmberAccent = Color(0xFFF59E0B)

// --- Light Theme Colors ---
val LightBg = Color(0xFFF8FAFC)
val PrimaryBlue = Color(0xFF2563EB)

// --- Navigation Screens ---
sealed class AppScreen {
    object UserAgreement : AppScreen()
    object Welcome : AppScreen()
    object Login : AppScreen()
    object Register : AppScreen()
    object ForgotPassword : AppScreen()
    object MainNav : AppScreen()
    object AddDevice : AppScreen()
    object TankDashboard : AppScreen()
}

// --- Bottom Bar Tabs ---
enum class BottomTab {
    HOME, SMART, EXPLORE, ME
}

// --- Country Data Model ---
data class Country(val flag: String, val name: String, val code: String)

val countryList = listOf(
    Country("🇮🇳", "India", "+91"),
    Country("🇺🇸", "United States", "+1"),
    Country("🇬🇧", "United Kingdom", "+44"),
    Country("🇦🇪", "UAE", "+971"),
    Country("🇨🇦", "Canada", "+1"),
    Country("🇦🇺", "Australia", "+61"),
    Country("🇸🇬", "Singapore", "+65"),
    Country("🇩🇪", "Germany", "+49")
)

// --- Helper Notification Utility ---
object NotificationUtils {
    private const val CHANNEL_ID = "aarohi_alerts_channel"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Aarohi System Alerts"
            val descriptionText = "Notifications for tank levels and motor events"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showTankAlert(context: Context, isFull: Boolean) {
        val title = if (isFull) "Tank Full Alert!" else "Low Water Level!"
        val message =
            if (isFull) "Overhead tank reached 100% capacity." else "Water level dropped below 10%."
        sendNotification(context, 1001, title, message)
    }

    fun showMotorRunningAlert(context: Context, minutes: Int) {
        sendNotification(
            context, 1002, "Motor Notice",
            "Motor pump has been running continuously for $minutes minutes."
        )
    }

    fun showPowerAlert(context: Context, isAvailable: Boolean) {
        val title = if (isAvailable) "Mains Line Restored" else "Mains Line Disconnected"
        val message =
            if (isAvailable) "230V AC Power is now available." else "AC Power line is off."
        sendNotification(context, 1003, title, message)
    }

    private fun sendNotification(context: Context, id: Int, title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(id, builder.build())
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("FIREBASE", "FirebaseInit: ${e.message}")
        }

        NotificationUtils.createNotificationChannel(this)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    AarohiAppContent()
                }
            }
        }
    }
}

@Composable
fun AarohiAppContent() {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    val prefs = remember { context.getSharedPreferences("aarohi_prefs", Context.MODE_PRIVATE) }
    val userAgreed = remember { mutableStateOf(prefs.getBoolean("user_agreed", false)) }
    val isGuestSession = remember { mutableStateOf(prefs.getBoolean("is_guest_session", false)) }

    val currentUser = auth.currentUser
    val isGoogleUser = currentUser?.providerData?.any { it.providerId == "google.com" } == true
    val isVerifiedUser = currentUser != null && (currentUser.isEmailVerified || isGoogleUser)

    val isLoggedInPref = prefs.getBoolean("is_logged_in", false)
    val isLoggedInState = isLoggedInPref && (currentUser == null || isVerifiedUser)
    val isGuestSessionState = prefs.getBoolean("is_guest_session", false)

    // Persistent Device State
    var savedDeviceName by remember { mutableStateOf(prefs.getString("device_name", "") ?: "") }
    var savedMacAddress by remember { mutableStateOf(prefs.getString("device_mac", "") ?: "") }

    var currentScreen by remember {
        mutableStateOf(
            when {
                !userAgreed.value -> AppScreen.UserAgreement
                isLoggedInState || isGuestSessionState -> AppScreen.MainNav
                else -> AppScreen.Welcome
            }
        )
    }

    var selectedTab by remember { mutableStateOf(BottomTab.HOME) }
    var showServerDialog by remember { mutableStateOf(false) }

    if (showServerDialog) {
        ServerSettingsDialog(onDismiss = { showServerDialog = false })
    }

    when (currentScreen) {
        AppScreen.UserAgreement -> {
            BackHandler { (context as? Activity)?.finish() }
            UserAgreementDialogScreen(
                onAgree = {
                    prefs.edit { putBoolean("user_agreed", true) }
                    userAgreed.value = true
                    currentScreen = if (prefs.getBoolean("is_logged_in", false) || prefs.getBoolean("is_guest_session", false)) {
                        AppScreen.MainNav
                    } else {
                        AppScreen.Welcome
                    }
                },
                onDisagree = {
                    (context as? Activity)?.finish()
                }
            )
        }

        AppScreen.Welcome -> {
            BackHandler { (context as? Activity)?.finish() }
            WelcomeChoiceScreen(
                onLogin = { currentScreen = AppScreen.Login },
                onSignUp = { currentScreen = AppScreen.Register },
                onGuest = {
                    prefs.edit {
                        putBoolean("is_logged_in", false)
                        putBoolean("is_guest_session", true)
                        remove("user_name")
                        remove("user_email")
                        remove("user_photo_url")
                    }
                    isGuestSession.value = true
                    currentScreen = AppScreen.MainNav
                }
            )
        }

        AppScreen.Login -> {
            BackHandler { currentScreen = AppScreen.Welcome }
            PasswordLoginScreen(
                onNavigateToRegister = { currentScreen = AppScreen.Register },
                onNavigateToForgotPassword = { currentScreen = AppScreen.ForgotPassword },
                onLoginSuccess = {
                    isGuestSession.value = false
                    currentScreen = AppScreen.MainNav
                },
                onGuest = {
                    prefs.edit {
                        putBoolean("is_logged_in", false)
                        putBoolean("is_guest_session", true)
                        remove("user_name")
                        remove("user_email")
                        remove("user_photo_url")
                    }
                    isGuestSession.value = true
                    currentScreen = AppScreen.MainNav
                },
                onOpenServerConfig = { showServerDialog = true },
                onBack = { currentScreen = AppScreen.Welcome }
            )
        }

        AppScreen.Register -> {
            BackHandler { currentScreen = AppScreen.Welcome }
            RegisterScreen(
                onNavigateToLogin = { currentScreen = AppScreen.Login },
                onRegisterSuccess = { currentScreen = AppScreen.Login },
                onLoginSuccess = {
                    isGuestSession.value = false
                    currentScreen = AppScreen.MainNav
                },
                onBack = { currentScreen = AppScreen.Welcome }
            )
        }

        AppScreen.ForgotPassword -> {
            BackHandler { currentScreen = AppScreen.Login }
            ForgotPasswordScreen(
                onNavigateToLogin = { currentScreen = AppScreen.Login }
            )
        }

        AppScreen.MainNav -> {
            BackHandler { (context as? Activity)?.finish() }
            val tempMqtt = remember { AarohiMqttManager(context, {}, {}, {}) }
            val isMqttConnected = tempMqtt.isServerVerified()

            MainTabShellScreen(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                savedDeviceName = savedDeviceName,
                savedMacAddress = savedMacAddress,
                isMqttConnected = isMqttConnected,
                onAddDeviceClick = {
                    if (!tempMqtt.isServerVerified()) {
                        Toast.makeText(context, "MQTT Server is NOT connected! Please verify Aarohi Server first.", Toast.LENGTH_LONG).show()
                        showServerDialog = true
                    } else {
                        currentScreen = AppScreen.AddDevice
                    }
                },
                onDeleteDevice = {
                    prefs.edit { remove("device_name"); remove("device_mac") }
                    savedDeviceName = ""
                    savedMacAddress = ""
                    Toast.makeText(context, "Device Unpaired", Toast.LENGTH_SHORT).show()
                },
                onOpenDashboard = {
                    if (!tempMqtt.isServerVerified()) {
                        Toast.makeText(context, "MQTT Server is NOT connected! Please verify Aarohi Server first.", Toast.LENGTH_LONG).show()
                        showServerDialog = true
                    } else {
                        currentScreen = AppScreen.TankDashboard
                    }
                },
                onLogout = {
                    auth.signOut()
                    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                    GoogleSignIn.getClient(context, gso).signOut()
                    prefs.edit {
                        putBoolean("is_logged_in", false)
                        putBoolean("is_guest_session", false)
                        remove("user_name")
                        remove("user_email")
                        remove("user_photo_url")
                    }
                    isGuestSession.value = false
                    currentScreen = AppScreen.Welcome
                },
                onOpenServerConfig = { showServerDialog = true }
            )
        }

        AppScreen.AddDevice -> {
            val tempMqtt = remember { AarohiMqttManager(context, {}, {}, {}) }
            if (!tempMqtt.isServerVerified()) {
                LaunchedEffect(Unit) {
                    Toast.makeText(context, "MQTT Server is NOT connected! Please verify Aarohi Server first.", Toast.LENGTH_LONG).show()
                    showServerDialog = true
                    currentScreen = AppScreen.MainNav
                }
            } else {
                LaunchedEffect(Unit) {
                    tempMqtt.connect()
                }
                DisposableEffect(Unit) {
                    onDispose { tempMqtt.disconnect() }
                }
                AddDeviceScreen(
                    initialName = "",
                    initialMac = "",
                    mqttManager = tempMqtt,
                    onBack = { currentScreen = AppScreen.MainNav },
                    onSaveDevice = { name, mac ->
                        prefs.edit {
                            putString("device_name", name)
                            putString("device_mac", mac)
                        }
                        savedDeviceName = name
                        savedMacAddress = mac
                        currentScreen = AppScreen.TankDashboard
                    }
                )
            }
        }

        AppScreen.TankDashboard -> {
            BackHandler { currentScreen = AppScreen.MainNav }
            AarohiDashboardScreen(
                onBack = { currentScreen = AppScreen.MainNav },
                onLogout = {
                    auth.signOut()
                    currentScreen = AppScreen.Welcome
                },
                onOpenServerConfig = { showServerDialog = true }
            )
        }
    }
}

// ==================== 1. USER AGREEMENT DIALOG SCREEN ====================
@Composable
fun UserAgreementDialogScreen(
    onAgree: () -> Unit,
    onDisagree: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = "User Agreement and Privacy Policy",
                    color = Color.Black,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Your privacy is of great importance, thus we have updated our Privacy Policy according to the latest laws and regulations to keep you fully informed. Before you consent to use our services, please kindly read through and comprehend what we present to you.\nMore detailed information, please check",
                    color = Color(0xFF4B5563),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Privacy Policy",
                        color = PrimaryBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { }
                    )

                    Text(
                        text = "User Agreement",
                        color = PrimaryBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { }
                    )
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = onAgree,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    Text("Agree", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Spacer(Modifier.height(10.dp))

                TextButton(
                    onClick = onDisagree,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Disagree", color = Color(0xFF6B7280), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

// ==================== AAROHI BRANDING LOGO COMPONENT ====================
@Composable
fun AarohiBrandingLogo(
    modifier: Modifier = Modifier,
    iconSize: Dp = 85.dp
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.aarohi_icon),
            contentDescription = "Aarohi Logo Icon",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(iconSize)
                .scale(1.15f) // 15% Zoom on Graphic Icon
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "AAROHI",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 4.sp,
            color = Color(0xFF0F2027)
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = "AI HOME AUTOMATION",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.5.sp,
            color = Color(0xFF4A5568)
        )

        Text(
            text = "by Sunny Pandit",
            fontSize = 9.sp,
            fontWeight = FontWeight.Normal,
            color = Color(0xFF64748B),
            modifier = Modifier
                .offset(y = (-4).dp)
                .padding(start = 80.dp)
        )
    }
}

// ==================== 2. WELCOME CHOICE SCREEN ====================
@Composable
fun WelcomeChoiceScreen(
    onLogin: () -> Unit,
    onSignUp: () -> Unit,
    onGuest: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(28.dp)
    ) {
        // Balanced Premium Logo Branding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            AarohiBrandingLogo(iconSize = 105.dp)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onLogin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                Text("Log In", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onSignUp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB))
            ) {
                Text("Sign Up", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Try as Guest",
                color = Color(0xFF4B5563),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable { onGuest() }
            )
        }
    }
}

// ==================== 3. LOGIN SCREEN (Password login UI matching screenshots) ====================
@Composable
fun PasswordLoginScreen(
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: () -> Unit,
    onLoginSuccess: () -> Unit,
    onGuest: () -> Unit,
    onOpenServerConfig: () -> Unit,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val prefs = remember { context.getSharedPreferences("aarohi_prefs", Context.MODE_PRIVATE) }

    var emailOrPhone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConsentChecked by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedCountry by remember { mutableStateOf(countryList[0]) }
    var showCountryDropdown by remember { mutableStateOf(false) }

    val webClientId = "703522021767-uehh90vmi75ab66pcm33ugh850hmiuol.apps.googleusercontent.com"
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    isLoading = true
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { authTask ->
                            isLoading = false
                            if (authTask.isSuccessful) {
                                val fbUser = auth.currentUser
                                val gName = account.displayName.orEmpty().ifBlank { fbUser?.displayName.orEmpty() }
                                val gEmail = account.email.orEmpty().ifBlank { fbUser?.email.orEmpty() }
                                val gPhoto = account.photoUrl?.toString().orEmpty().ifBlank { fbUser?.photoUrl?.toString().orEmpty() }

                                prefs.edit {
                                    putBoolean("is_logged_in", true)
                                    putBoolean("is_guest_session", false)
                                    putString("user_name", gName)
                                    putString("user_email", gEmail)
                                    putString("user_photo_url", gPhoto)
                                }
                                Toast.makeText(context, "Google Sign-In Successful!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } else {
                                Toast.makeText(context, "Google Auth Error: ${authTask.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Toast.makeText(context, "Google Auth: Token null", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.e("GOOGLE_AUTH", "Error: ${e.statusCode} | ${e.message}")
                Toast.makeText(context, "Google Sign-In failed (${e.statusCode})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(16.dp))

            // Title "Password login"
            Text(
                text = "Password login",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(22.dp))

            // Country Selector Dropdown
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable { showCountryDropdown = true }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedCountry.name,
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF)
                    )
                }

                DropdownMenu(
                    expanded = showCountryDropdown,
                    onDismissRequest = { showCountryDropdown = false },
                    modifier = Modifier
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E7EB))
                ) {
                    countryList.forEach { country ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${country.flag} ${country.name} (${country.code})",
                                    color = Color.Black,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                selectedCountry = country
                                showCountryDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Input Field: "Phone or email"
            TextField(
                value = emailOrPhone,
                onValueChange = { emailOrPhone = it },
                placeholder = { Text("Phone or email", color = Color(0xFF9CA3AF), fontSize = 14.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F4F6),
                    unfocusedContainerColor = Color(0xFFF3F4F6),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Input Field: "Password"
            TextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Password", color = Color(0xFF9CA3AF), fontSize = 14.sp) },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF)
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F4F6),
                    unfocusedContainerColor = Color(0xFFF3F4F6),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Checkbox: "I have read and consent to the terms Privacy Policy and User Agreement"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isConsentChecked,
                    onCheckedChange = { isConsentChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryBlue,
                        uncheckedColor = Color(0xFFD1D5DB)
                    )
                )
                Text(
                    text = "I have read and consent to the terms Privacy Policy and User Agreement",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 14.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // Primary Button: "Log In"
            Button(
                onClick = {
                    if (emailOrPhone.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please enter phone/email and password.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!isConsentChecked) {
                        Toast.makeText(context, "Please consent to the terms.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    auth.signInWithEmailAndPassword(emailOrPhone.trim(), password)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null && !user.isEmailVerified) {
                                    user.sendEmailVerification()
                                    Toast.makeText(
                                        context,
                                        "Email not verified! Sent link to email.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                } else {
                                    prefs.edit {
                                        putBoolean("is_logged_in", true)
                                        putBoolean("is_guest_session", false)
                                        putString("user_name", user?.displayName.orEmpty().ifBlank { emailOrPhone.trim() })
                                        putString("user_email", user?.email.orEmpty().ifBlank { emailOrPhone.trim() })
                                        putString("user_photo_url", user?.photoUrl?.toString().orEmpty())
                                    }
                                    Toast.makeText(context, "Welcome back!", Toast.LENGTH_SHORT).show()
                                    onLoginSuccess()
                                }
                            } else {
                                Toast.makeText(context, "Login Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFBFDBFE),
                    disabledContainerColor = Color(0xFFE5E7EB)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = "Log In",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // Forgot Password Link
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Forgot Password",
                    color = PrimaryBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onNavigateToForgotPassword() }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Bottom Center Circular Google Sign In Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            googleSignInClient.signOut().addOnCompleteListener {
                                val signInIntent = googleSignInClient.signInIntent
                                googleLauncher.launch(signInIntent)
                            }
                        },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Sign in with Google",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Don't have an account? ", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        text = "Register",
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateToRegister() }
                    )
                    Text(" | ", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        text = "Guest",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onGuest() }
                    )
                }
            }
        }
    }
}

// ==================== 4. REGISTER SCREEN (Matching screenshots) ====================
@Composable
fun RegisterScreen(
    onNavigateToLogin: () -> Unit,
    onRegisterSuccess: () -> Unit,
    onLoginSuccess: () -> Unit = {},
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val prefs = remember { context.getSharedPreferences("aarohi_prefs", Context.MODE_PRIVATE) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var isConsentChecked by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }

    var selectedCountry by remember { mutableStateOf(countryList[0]) }
    var showCountryDropdown by remember { mutableStateOf(false) }

    val webClientId = "703522021767-uehh90vmi75ab66pcm33ugh850hmiuol.apps.googleusercontent.com"
    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                val idToken = account.idToken
                if (idToken != null) {
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    isLoading = true
                    auth.signInWithCredential(credential)
                        .addOnCompleteListener { authTask ->
                            isLoading = false
                            if (authTask.isSuccessful) {
                                val fbUser = auth.currentUser
                                val gName = account.displayName.orEmpty().ifBlank { fbUser?.displayName.orEmpty() }
                                val gEmail = account.email.orEmpty().ifBlank { fbUser?.email.orEmpty() }
                                val gPhoto = account.photoUrl?.toString().orEmpty().ifBlank { fbUser?.photoUrl?.toString().orEmpty() }

                                prefs.edit {
                                    putBoolean("is_logged_in", true)
                                    putBoolean("is_guest_session", false)
                                    putString("user_name", gName)
                                    putString("user_email", gEmail)
                                    putString("user_photo_url", gPhoto)
                                }
                                Toast.makeText(context, "Google Sign-In Successful!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess()
                            } else {
                                Toast.makeText(context, "Google Auth Error: ${authTask.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Toast.makeText(context, "Google Auth: Token null", Toast.LENGTH_SHORT).show()
                }
            } catch (e: ApiException) {
                Log.e("GOOGLE_AUTH", "Error: ${e.statusCode} | ${e.message}")
                Toast.makeText(context, "Google Sign-In failed (${e.statusCode})", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.82f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(Modifier.height(16.dp))

            // Title "Register"
            Text(
                text = "Register",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(22.dp))

            // Country Selector Dropdown
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .clickable { showCountryDropdown = true }
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = selectedCountry.name,
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = Color(0xFF9CA3AF)
                    )
                }

                DropdownMenu(
                    expanded = showCountryDropdown,
                    onDismissRequest = { showCountryDropdown = false },
                    modifier = Modifier
                        .background(Color.White)
                        .border(1.dp, Color(0xFFE5E7EB))
                ) {
                    countryList.forEach { country ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${country.flag} ${country.name} (${country.code})",
                                    color = Color.Black,
                                    fontSize = 13.sp
                                )
                            },
                            onClick = {
                                selectedCountry = country
                                showCountryDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Input Field: "Email Address"
            TextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Email Address", color = Color(0xFF9CA3AF), fontSize = 14.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F4F6),
                    unfocusedContainerColor = Color(0xFFF3F4F6),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Input Field: "Password"
            TextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Password", color = Color(0xFF9CA3AF), fontSize = 14.sp) },
                singleLine = true,
                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color(0xFF9CA3AF)
                        )
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F4F6),
                    unfocusedContainerColor = Color(0xFFF3F4F6),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // Checkbox: "I have read and consent to the terms Privacy Policy and User Agreement"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Checkbox(
                    checked = isConsentChecked,
                    onCheckedChange = { isConsentChecked = it },
                    colors = CheckboxDefaults.colors(
                        checkedColor = PrimaryBlue,
                        uncheckedColor = Color(0xFFD1D5DB)
                    )
                )
                Text(
                    text = "I have read and consent to the terms Privacy Policy and User Agreement",
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 14.sp
                )
            }

            Spacer(Modifier.height(20.dp))

            // Primary Button: "Get"
            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        Toast.makeText(context, "Please enter email and password.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (!isConsentChecked) {
                        Toast.makeText(context, "Please consent to the terms.", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isLoading = true
                    auth.createUserWithEmailAndPassword(email.trim(), password)
                        .addOnCompleteListener { task ->
                            isLoading = false
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                user?.sendEmailVerification()
                                Toast.makeText(
                                    context,
                                    "Account created! Verification email sent to ${email.trim()}.",
                                    Toast.LENGTH_LONG
                                ).show()
                                onRegisterSuccess()
                            } else {
                                Toast.makeText(context, "Registration Failed: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                },
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFBFDBFE),
                    disabledContainerColor = Color(0xFFE5E7EB)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = PrimaryBlue, modifier = Modifier.size(20.dp))
                } else {
                    Text(
                        text = "Get",
                        color = PrimaryBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Bottom Center Circular Google Sign In Button
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            googleSignInClient.signOut().addOnCompleteListener {
                                val signInIntent = googleSignInClient.signInIntent
                                googleLauncher.launch(signInIntent)
                            }
                        },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 3.dp,
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = "Sign in with Google",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Already have an account? ", color = Color.Gray, fontSize = 12.sp)
                    Text(
                        text = "Password login",
                        color = PrimaryBlue,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateToLogin() }
                    )
                }
            }
        }
    }
}

// ==================== 5. FORGOT PASSWORD SCREEN ====================
@Composable
fun ForgotPasswordScreen(
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }

    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBase)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            border = BorderStroke(1.dp, CardBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onNavigateToLogin) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Reset Password", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "Enter your registered email address below. We will send you a password reset link.",
                    color = Color.Gray,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )

                Spacer(Modifier.height(20.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address", color = Color.Gray) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = CyanGlow,
                        unfocusedBorderColor = CardBorder
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (email.isBlank()) {
                            Toast.makeText(context, "Please enter your email address.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isLoading = true
                        auth.sendPasswordResetEmail(email.trim())
                            .addOnCompleteListener { task ->
                                isLoading = false
                                if (task.isSuccessful) {
                                    Toast.makeText(
                                        context,
                                        "Password reset link sent to ${email.trim()}. Please check your inbox.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onNavigateToLogin()
                                } else {
                                    Toast.makeText(context, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                    },
                    enabled = !isLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(22.dp))
                    } else {
                        Text("SEND RESET LINK", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ==================== 6. ADD NEW DEVICE SCREEN ====================
@Composable
fun AddDeviceScreen(
    initialName: String,
    initialMac: String,
    mqttManager: AarohiMqttManager,
    onBack: () -> Unit,
    onSaveDevice: (String, String) -> Unit
) {
    var deviceName by remember { mutableStateOf(initialName) }
    var macAddress by remember { mutableStateOf(initialMac) }
    var isVerifying by remember { mutableStateOf(false) }
    var verificationStatusMessage by remember { mutableStateOf<String?>(null) }
    var isVerificationSuccess by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        containerColor = LightBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, enabled = !isVerifying) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.Black)
                }
                Spacer(Modifier.width(8.dp))
                Text("Add New Device", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Button(
                    onClick = {
                        val finalName = deviceName.trim().ifBlank { "Aarohi Tank Device" }
                        val finalMac = macAddress.trim()

                        if (finalMac.isBlank()) {
                            Toast.makeText(context, "Please enter MAC Address", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        isVerifying = true
                        verificationStatusMessage = "Verifying Device with ESP32..."
                        isVerificationSuccess = false

                        mqttManager.verifyDevice(finalMac) { success, message ->
                            isVerifying = false
                            verificationStatusMessage = message
                            isVerificationSuccess = success

                            if (success) {
                                Toast.makeText(context, "Device Verified Successfully! ✅", Toast.LENGTH_SHORT).show()
                                onSaveDevice(finalName, finalMac)
                            } else {
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    if (isVerifying) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text("Verifying Device...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else {
                        Text("Verify & Save Device", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text("Device Setup", color = Color(0xFF1E293B), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)

            Spacer(Modifier.height(24.dp))

            Text("Device Name", color = Color(0xFF334155), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = deviceName,
                onValueChange = { deviceName = it },
                singleLine = true,
                enabled = !isVerifying,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            Text("MAC Address", color = Color(0xFF334155), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = macAddress,
                onValueChange = { macAddress = it },
                singleLine = true,
                enabled = !isVerifying,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                    focusedTextColor = Color.Black,
                    unfocusedTextColor = Color.Black,
                    focusedBorderColor = PrimaryBlue,
                    unfocusedBorderColor = Color(0xFFCBD5E1)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (verificationStatusMessage != null) {
                Spacer(Modifier.height(20.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when {
                        isVerifying -> Color(0xFFFEF3C7)
                        isVerificationSuccess -> Color(0xFFDCFCE7)
                        else -> Color(0xFFFEE2E2)
                    },
                    border = BorderStroke(
                        1.dp,
                        when {
                            isVerifying -> AmberAccent
                            isVerificationSuccess -> EmeraldAccent
                            else -> RoseAccent
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isVerifying) {
                            CircularProgressIndicator(color = AmberAccent, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = if (isVerificationSuccess) Icons.Default.CheckCircle else Icons.Default.Error,
                                contentDescription = null,
                                tint = if (isVerificationSuccess) EmeraldAccent else RoseAccent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = verificationStatusMessage ?: "",
                            color = when {
                                isVerifying -> Color(0xFF92400E)
                                isVerificationSuccess -> Color(0xFF166534)
                                else -> Color(0xFF991B1B)
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

// ==================== 7. DYNAMIC MQTT BROKER SETTINGS DIALOG ====================
@Composable
fun ServerSettingsDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val tempMqttManager = remember {
        AarohiMqttManager(
            context = context,
            onConnectionChanged = {},
            onTelemetryReceived = {},
            onNotificationReceived = {}
        )
    }

    var brokerUri by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    var isVerifying by remember { mutableStateOf(false) }
    var verificationStatus by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    // Auto-Clear on Failure (2-Second Reset)
    LaunchedEffect(verificationStatus, isSuccess, isVerifying) {
        if (verificationStatus != null && !isSuccess && !isVerifying) {
            delay(2.seconds)
            verificationStatus = null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        shape = RoundedCornerShape(24.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Aarohi Server", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Text("Broker URI", color = Color(0xFF334155), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = brokerUri,
                    onValueChange = { brokerUri = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Text("Username", color = Color(0xFF334155), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                Text("Password", color = Color(0xFF334155), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.Black,
                        unfocusedTextColor = Color.Black,
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (verificationStatus != null && !isSuccess) {
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFFEE2E2),
                        border = BorderStroke(1.dp, RoseAccent)
                    ) {
                        Text(
                            text = verificationStatus!!,
                            color = Color(0xFFB91C1C),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        if (brokerUri.isBlank() || username.isBlank() || password.isBlank()) {
                            isSuccess = false
                            verificationStatus = "Verification Failed!"
                            return@Button
                        }
                        isVerifying = true
                        verificationStatus = "Verifying..."
                        isSuccess = false

                        tempMqttManager.testConnection(brokerUri, username, password) { success, _ ->
                            isVerifying = false
                            isSuccess = success
                            if (success) {
                                tempMqttManager.saveCredentials(brokerUri, username, password)
                                Toast.makeText(context, "Connected", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                verificationStatus = "Verification Failed!"
                            }
                        }
                    },
                    enabled = !isVerifying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                ) {
                    if (isVerifying) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Saving...", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    } else {
                        Text("Save Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    )
}

// ==================== 8. MAIN TAB SHELL ====================
@Composable
fun MainTabShellScreen(
    selectedTab: BottomTab,
    onTabSelected: (BottomTab) -> Unit,
    savedDeviceName: String,
    savedMacAddress: String,
    isMqttConnected: Boolean = false,
    onAddDeviceClick: () -> Unit,
    onDeleteDevice: () -> Unit,
    onOpenDashboard: () -> Unit,
    onLogout: () -> Unit,
    onOpenServerConfig: () -> Unit
) {
    Scaffold(
        containerColor = LightBg,
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 6.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == BottomTab.HOME,
                    onClick = { onTabSelected(BottomTab.HOME) },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                    label = { Text("Home", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.12f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == BottomTab.SMART,
                    onClick = { onTabSelected(BottomTab.SMART) },
                    icon = { Icon(Icons.Default.CheckBox, contentDescription = "Smart") },
                    label = { Text("Smart", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.12f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == BottomTab.EXPLORE,
                    onClick = { onTabSelected(BottomTab.EXPLORE) },
                    icon = { Icon(Icons.Default.Hexagon, contentDescription = "Explore") },
                    label = { Text("Explore", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.12f)
                    )
                )

                NavigationBarItem(
                    selected = selectedTab == BottomTab.ME,
                    onClick = { onTabSelected(BottomTab.ME) },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Me") },
                    label = { Text("Me", fontSize = 11.sp) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                BottomTab.HOME -> HomeTabContent(
                    savedDeviceName = savedDeviceName,
                    savedMacAddress = savedMacAddress,
                    isMqttConnected = isMqttConnected,
                    onAddDeviceClick = onAddDeviceClick,
                    onDeleteDevice = onDeleteDevice,
                    onOpenDashboard = onOpenDashboard
                )
                BottomTab.SMART -> SmartTabContent(onAddRule = onAddDeviceClick)
                BottomTab.EXPLORE -> ExploreTabContent(onAddFeature = onAddDeviceClick)
                BottomTab.ME -> MeTabContent(
                    onLogout = onLogout,
                    onOpenServerConfig = onOpenServerConfig,
                    onAddDeviceClick = onAddDeviceClick
                )
            }
        }
    }
}

// --- Home Tab (Matching Uploaded Image: Paired Controllers View) ---
@Composable
fun HomeTabContent(
    savedDeviceName: String,
    savedMacAddress: String,
    isMqttConnected: Boolean = false,
    onAddDeviceClick: () -> Unit,
    onDeleteDevice: () -> Unit,
    onOpenDashboard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Home, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("My AI Apps", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }

            IconButton(onClick = onAddDeviceClick) {
                Icon(
                    imageVector = if (isMqttConnected) Icons.Default.Add else Icons.Default.Lock,
                    contentDescription = "Add Device",
                    tint = if (isMqttConnected) Color.Black else RoseAccent,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (savedMacAddress.isNotBlank()) {
            // Sub-header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Paired Controllers", color = Color(0xFF1E293B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("1 Device Active", color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))

            // Paired Controller Card (Matching Image Mockup Exactly)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, PrimaryBlue.copy(alpha = 0.25f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenDashboard() }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFF0F172A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, tint = CyanGlow, modifier = Modifier.size(24.dp))
                        }

                        Spacer(Modifier.width(14.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldAccent)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    savedDeviceName.ifBlank { "Aarohi Tank Device" },
                                    color = Color.Black,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onDeleteDevice) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RoseAccent, modifier = Modifier.size(20.dp))
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = "Open", tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                    }
                }
            }
        } else {
            // Empty State matching uploaded image media_1789952238757.jpg exactly
            Spacer(Modifier.weight(1f))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Router,
                    contentDescription = null,
                    tint = PrimaryBlue,
                    modifier = Modifier.size(56.dp)
                )

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Add your first device",
                    color = Color.Black,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(20.dp))

                Button(
                    onClick = onAddDeviceClick,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMqttConnected) Color.Black else Color(0xFF334155)
                    ),
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (!isMqttConnected) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Locked",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = if (isMqttConnected) "Add Now" else "Add Now (Locked)",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1.5f))
        }
    }
}

// --- Smart Tab ---
@Composable
fun SmartTabContent(onAddRule: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckBox, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Smart Automation", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.SmartToy, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(34.dp))
            }

            Spacer(Modifier.height(16.dp))

            Text("Smart Rules & Automation", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Automatic motor schedules and level alerts active.", color = Color(0xFF6B7280), fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1.5f))
    }
}

// --- Explore Tab ---
@Composable
fun ExploreTabContent(onAddFeature: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Hexagon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Explore Features", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Explore, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(34.dp))
            }

            Spacer(Modifier.height(16.dp))

            Text("Explore Aarohi Features", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Discover AI telemetry insights and power efficiency logs.", color = Color(0xFF6B7280), fontSize = 13.sp)
        }

        Spacer(Modifier.weight(1.5f))
    }
}

// --- Me & Settings Tab ---
@Composable
fun MeTabContent(
    onLogout: () -> Unit,
    onOpenServerConfig: () -> Unit,
    onAddDeviceClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val auth = remember { FirebaseAuth.getInstance() }
    val prefs = remember { context.getSharedPreferences("aarohi_prefs", Context.MODE_PRIVATE) }

    val isGuest = prefs.getBoolean("is_guest_session", false)
    var storedName by remember { mutableStateOf(prefs.getString("user_name", "") ?: "") }
    var storedDob by remember { mutableStateOf(prefs.getString("user_dob", "") ?: "") }
    var storedAge by remember { mutableStateOf(prefs.getString("user_age", "") ?: "") }
    var storedPhotoUrl by remember { mutableStateOf(prefs.getString("user_photo_url", "") ?: (auth.currentUser?.photoUrl?.toString() ?: "")) }

    val userEmail = prefs.getString("user_email", "") ?: (auth.currentUser?.email ?: "")

    var showGuestProfileSetup by remember { mutableStateOf(false) }

    val tempMqttManager = remember {
        AarohiMqttManager(
            context = context,
            onConnectionChanged = {},
            onTelemetryReceived = {},
            onNotificationReceived = {}
        )
    }
    val isServerVerified = tempMqttManager.isServerVerified()

    if (showGuestProfileSetup && isGuest) {
        GuestProfileSetupDialog(
            currentName = storedName,
            currentDob = storedDob,
            currentAge = storedAge,
            currentPhotoUrl = storedPhotoUrl,
            onDismiss = { showGuestProfileSetup = false },
            onSave = { name, dob, age, photoUrl ->
                prefs.edit {
                    putString("user_name", name)
                    putString("user_dob", dob)
                    putString("user_age", age)
                    putString("user_photo_url", photoUrl)
                }
                storedName = name
                storedDob = dob
                storedAge = age
                storedPhotoUrl = photoUrl
                showGuestProfileSetup = false
                Toast.makeText(context, "Profile updated successfully! ✅", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("Me & Settings", color = Color.Black, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Profile Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isGuest) showGuestProfileSetup = true
                }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDBEAFE)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (storedPhotoUrl.isNotBlank()) {
                            AsyncImage(
                                model = storedPhotoUrl,
                                contentDescription = "Profile Photo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(28.dp))
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        val titleText = when {
                            isGuest && storedName.isNotBlank() -> storedName
                            isGuest -> "Guest User"
                            storedName.isNotBlank() -> storedName
                            userEmail.isNotBlank() -> userEmail
                            else -> "Aarohi User"
                        }
                        val subtitleText = when {
                            isGuest && storedDob.isNotBlank() -> "DOB: $storedDob ${if (storedAge.isNotBlank()) "($storedAge yrs)" else ""}"
                            isGuest -> "Profile Not Set"
                            userEmail.isNotBlank() -> userEmail
                            else -> "Google Account"
                        }

                        Text(
                            text = titleText,
                            color = Color.Black,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitleText,
                            color = Color.Gray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isGuest) {
                    Spacer(Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = { showGuestProfileSetup = true },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PrimaryBlue)
                    ) {
                        Text(
                            if (storedName.isNotBlank() || storedDob.isNotBlank()) "Edit Profile" else "Set Up Profile",
                            color = PrimaryBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "SETTINGS & CONFIGURATION",
            color = Color(0xFF64748B),
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(Modifier.height(10.dp))

        // Aarohi Server Setting Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    if (isServerVerified) {
                        Toast.makeText(context, "Connected", Toast.LENGTH_SHORT).show()
                    } else {
                        onOpenServerConfig()
                    }
                }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE0F2FE)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text("Aarohi Server", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (isServerVerified) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                ) {
                    Text(
                        text = if (isServerVerified) "Connected ✅" else "Not Configured ⚠️",
                        color = if (isServerVerified) Color(0xFF16A34A) else Color(0xFFB91C1C),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(Modifier.width(6.dp))

                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
            }
        }

        Spacer(Modifier.height(20.dp))

        // Logout Button Card
        Button(
            onClick = onLogout,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFEE2E2)),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Text("Log Out", color = RoseAccent, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

// ==================== GUEST PROFILE SETUP DIALOG (CLEAN LIGHT THEME) ====================
@Composable
fun GuestProfileSetupDialog(
    currentName: String,
    currentDob: String,
    currentAge: String,
    currentPhotoUrl: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var fullName by remember { mutableStateOf(currentName) }
    var dobText by remember { mutableStateOf(currentDob) }
    var ageText by remember { mutableStateOf(currentAge) }
    var photoUriString by remember { mutableStateOf(currentPhotoUrl) }

    var nameError by remember { mutableStateOf<String?>(null) }
    var dobError by remember { mutableStateOf<String?>(null) }
    var showImageSourceMenu by remember { mutableStateOf(false) }

    // Launcher for Photo Gallery Selection
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            photoUriString = it.toString()
        }
    }

    // Launcher for Camera Photo Capture
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        bitmap?.let {
            try {
                val file = File(context.cacheDir, "guest_avatar_${System.currentTimeMillis()}.jpg")
                FileOutputStream(file).use { out ->
                    it.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
                photoUriString = Uri.fromFile(file).toString()
            } catch (e: Exception) {
                Log.e("GuestProfile", "Failed to save camera bitmap", e)
            }
        }
    }

    fun openDatePicker() {
        val cal = Calendar.getInstance()
        if (dobText.isNotBlank()) {
            try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.parse(dobText)?.let { cal.time = it }
            } catch (_: Exception) {}
        }

        val datePickerDialog = android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                dobText = sdf.format(selectedCal.time)
                dobError = null

                val today = Calendar.getInstance()
                var age = today.get(Calendar.YEAR) - selectedCal.get(Calendar.YEAR)
                if (today.get(Calendar.DAY_OF_YEAR) < selectedCal.get(Calendar.DAY_OF_YEAR)) {
                    age--
                }
                ageText = if (age >= 0) age.toString() else "0"
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        datePickerDialog.show()
    }

    // Photo Option Dialog (Gallery vs Camera)
    if (showImageSourceMenu) {
        AlertDialog(
            onDismissRequest = { showImageSourceMenu = false },
            containerColor = Color.White,
            title = {
                Text(
                    text = "Profile Photo Option",
                    color = Color(0xFF0F172A),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            showImageSourceMenu = false
                            galleryLauncher.launch("image/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = PrimaryBlue)
                            Spacer(Modifier.width(12.dp))
                            Text("Choose from Gallery", color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    TextButton(
                        onClick = {
                            showImageSourceMenu = false
                            cameraLauncher.launch(null)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PrimaryBlue)
                            Spacer(Modifier.width(12.dp))
                            Text("Take a New Photo", color = Color(0xFF0F172A), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (photoUriString.isNotBlank()) {
                        TextButton(
                            onClick = {
                                showImageSourceMenu = false
                                photoUriString = ""
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = RoseAccent)
                                Spacer(Modifier.width(12.dp))
                                Text("Remove Photo", color = RoseAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showImageSourceMenu = false }) {
                    Text("Cancel", color = Color(0xFF64748B))
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (currentName.isNotBlank()) "Edit Guest Profile" else "Set Up Guest Profile",
                    color = Color(0xFF0F172A),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Circular Avatar Placeholder Component with Camera Badge
                Box(
                    modifier = Modifier.padding(vertical = 4.dp),
                    contentAlignment = Alignment.BottomEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE2E8F0))
                            .border(2.dp, PrimaryBlue, CircleShape)
                            .clickable { showImageSourceMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUriString.isNotBlank()) {
                            AsyncImage(
                                model = photoUriString,
                                contentDescription = "Guest Profile Photo",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = "Default Avatar",
                                tint = PrimaryBlue,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    // Camera Badge Button
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(PrimaryBlue)
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { showImageSourceMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = "Change Photo",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    "Tap avatar to select photo or take picture",
                    color = Color(0xFF64748B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )

                // Full Name Input Field
                OutlinedTextField(
                    value = fullName,
                    onValueChange = {
                        fullName = it
                        if (it.isNotBlank()) nameError = null
                    },
                    label = { Text("Full Name *", color = Color(0xFF475569)) },
                    placeholder = { Text("Enter your full name", color = Color(0xFF94A3B8)) },
                    isError = nameError != null,
                    supportingText = {
                        nameError?.let { Text(it, color = RoseAccent, fontSize = 11.sp) }
                    },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color(0xFFCBD5E1),
                        errorBorderColor = RoseAccent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Date of Birth Field with Calendar Icon
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = dobText,
                        onValueChange = { },
                        readOnly = true,
                        label = { Text("Date of Birth (DOB) *", color = Color(0xFF475569)) },
                        placeholder = { Text("Select date from calendar", color = Color(0xFF94A3B8)) },
                        isError = dobError != null,
                        supportingText = {
                            dobError?.let { Text(it, color = RoseAccent, fontSize = 11.sp) }
                        },
                        trailingIcon = {
                            IconButton(onClick = { openDatePicker() }) {
                                Icon(Icons.Default.DateRange, contentDescription = "Pick Date", tint = PrimaryBlue)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF0F172A),
                            unfocusedTextColor = Color(0xFF0F172A),
                            focusedBorderColor = PrimaryBlue,
                            unfocusedBorderColor = Color(0xFFCBD5E1),
                            errorBorderColor = RoseAccent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openDatePicker() }
                    )
                }

                // Age Field (Auto-calculated from DOB)
                OutlinedTextField(
                    value = ageText,
                    onValueChange = { ageText = it.filter { char -> char.isDigit() } },
                    label = { Text("Age (Years)", color = Color(0xFF475569)) },
                    placeholder = { Text("Auto-calculated from DOB", color = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF0F172A),
                        unfocusedTextColor = Color(0xFF0F172A),
                        focusedBorderColor = PrimaryBlue,
                        unfocusedBorderColor = Color(0xFFCBD5E1)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    var isValid = true
                    if (fullName.trim().isBlank()) {
                        nameError = "Full name is required"
                        isValid = false
                    }
                    if (dobText.trim().isBlank()) {
                        dobError = "Date of Birth is required"
                        isValid = false
                    }
                    if (isValid) {
                        onSave(fullName.trim(), dobText.trim(), ageText.trim(), photoUriString.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Save Profile", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF64748B))
            }
        }
    )
}

// ==================== 9. AAROHI DASHBOARD SCREEN ====================
@Composable
fun AarohiDashboardScreen(
    onBack: () -> Unit = {},
    onLogout: () -> Unit = {},
    onOpenServerConfig: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val consoleListState = rememberLazyListState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        Log.d("NOTIFICATION", if (isGranted) "Permission Granted" else "Permission Denied")
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionCheck = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            )
            if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ==================== STATE (ESP32 authoritative) ====================
    var waterLevel by remember { mutableFloatStateOf(0f) }
    var isMotorOn by remember { mutableStateOf(false) }
    var autoLogicEnabled by remember { mutableStateOf(true) }
    var isLightAvailable by remember { mutableStateOf(false) }
    var hasReceivedTelemetry by remember { mutableStateOf(false) }
    var isEsp32Online by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var bridgeStatus by remember { mutableStateOf("Disconnected") }

    // ==================== LOGS ====================
    val logs = remember {
        mutableStateListOf(
            "[--:--:--] [ESP32] Aarohi 2.0 firmware initialized.",
            "[--:--:--] [MQTT] Dynamic memory reference loaded.",
            "[--:--:--] [POWER] Waiting for telemetry...",
            "[--:--:--] [RELAY] Waiting for telemetry..."
        )
    }

    fun addLog(tag: String, msg: String) {
        Log.d(tag, msg)
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        logs.add("[$time] [$tag] $msg")
        coroutineScope.launch {
            if (logs.isNotEmpty()) consoleListState.animateScrollToItem(logs.size - 1)
        }
    }

    fun showToast(msg: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    // ==================== MQTT MANAGER ====================
    val mqttManager = remember {
        AarohiMqttManager(
            context = context,
            onConnectionChanged = { status ->
                bridgeStatus = status
                addLog("MQTT", "Broker status: $status")
            },
            onTelemetryReceived = { data ->
                hasReceivedTelemetry = true
                waterLevel = data.level
                isMotorOn = data.motorRunning
                isLightAvailable = data.mainsAvailable
                autoLogicEnabled = data.autoMode
                addLog(
                    "STATUS",
                    "Lvl: ${data.level.toInt()}% | Motor: ${if (data.motorRunning) "ON" else "OFF"} | Mains: ${if (data.mainsAvailable) "ON" else "OFF"} | Auto: ${if (data.autoMode) "ON" else "OFF"}"
                )
            },
            onNotificationReceived = { alert ->
                showToast(alert)
                addLog("NOTIF", alert)
            },
            onDeviceStatusChanged = { online ->
                isEsp32Online = online
                addLog(
                    "ESP32",
                    if (online) "Device ONLINE" else "Device OFFLINE"
                )
            }
        )
    }

    LaunchedEffect(Unit) {
        mqttManager.connect()
    }

    DisposableEffect(Unit) {
        onDispose { mqttManager.disconnect() }
    }

    // ==================== NOTIFICATIONS ONLY ====================
    LaunchedEffect(waterLevel) {
        if (waterLevel >= 99.5f) {
            NotificationUtils.showTankAlert(context, isFull = true)
            addLog("ALERT", "Tank FULL detected")
        } else if (waterLevel <= 10f && waterLevel > 0f) {
            NotificationUtils.showTankAlert(context, isFull = false)
            addLog("ALERT", "Low water detected")
        }
    }

    LaunchedEffect(isMotorOn) {
        if (isMotorOn) {
            delay(10.minutes)
            if (isMotorOn) NotificationUtils.showMotorRunningAlert(context, 10)
        }
    }

    LaunchedEffect(isLightAvailable, hasReceivedTelemetry) {
        if (hasReceivedTelemetry && !isLightAvailable) {
            NotificationUtils.showPowerAlert(context, isAvailable = false)
        }
    }

    // ==================== ANIMATIONS ====================
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(if (isMotorOn) 1400 else 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val syncRotation by animateFloatAsState(
        targetValue = if (isSyncing) 360f else 0f,
        animationSpec = tween(durationMillis = 700, easing = LinearOutSlowInEasing),
        label = "syncRot"
    )

    // ✨ ✨ ESP32 Device ONLINE/OFFLINE 3D Pulse Animation (Green / Red Blinking) ✨ ✨
    val esp32PulseTransition = rememberInfiniteTransition(label = "esp32Pulse")
    val esp32Scale by esp32PulseTransition.animateFloat(
        initialValue = 0.82f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "esp32Scale"
    )
    val esp32Glow by esp32PulseTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "esp32Glow"
    )

    val esp32Color = if (isEsp32Online) EmeraldAccent else RoseAccent
    val esp32DarkColor = if (isEsp32Online) Color(0xFF064E3B) else Color(0xFF7F1D1D)

    Scaffold(
        containerColor = DarkBase,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF083344),
                        border = BorderStroke(1.dp, CyanGlow.copy(alpha = 0.5f)),
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                data.visuals.message,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ===== Header Bar (Frosted Glassmorphism UI) =====
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.45f))
                    .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isMqttConnected = bridgeStatus.equals("Connected", ignoreCase = true)
                            // Green Status Dot (MQTT Broker state)
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isMqttConnected -> EmeraldAccent
                                            bridgeStatus.contains("Connect", ignoreCase = true) -> AmberAccent
                                            else -> RoseAccent
                                        }
                                    )
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Aarohi 2.0",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )

                            Spacer(Modifier.width(8.dp))

                            // 3D Pulse Light (strictly for ESP32 Heartbeat)
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .graphicsLayer {
                                        scaleX = esp32Scale
                                        scaleY = esp32Scale
                                        shadowElevation = 8f
                                        alpha = 0.95f
                                    }
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                if (isEsp32Online) Color(0xFFA7F3D0) else Color(0xFFFF8A8A),
                                                esp32Color,
                                                esp32DarkColor
                                            )
                                        )
                                    )
                                    .border(1.dp, esp32Color.copy(alpha = esp32Glow), CircleShape)
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        // MQTT Status Text strictly depends on broker connection state
                        val isMqttConnected = bridgeStatus.equals("Connected", ignoreCase = true)
                        val statusText = if (isMqttConnected) "Connected" else bridgeStatus
                        Text(
                            text = statusText,
                            color = when {
                                isMqttConnected -> EmeraldAccent
                                bridgeStatus.contains("Connect", ignoreCase = true) -> AmberAccent
                                else -> RoseAccent
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Refresh / Restart Button
                    IconButton(
                        onClick = {
                            if (isSyncing) return@IconButton
                            isSyncing = true
                            coroutineScope.launch {
                                mqttManager.disconnect()
                                delay(200.milliseconds)
                                mqttManager.connect()
                                delay(1500.milliseconds)
                                isSyncing = false
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF1E293B).copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Refresh",
                            tint = CyanGlow,
                            modifier = Modifier
                                .size(22.dp)
                                .graphicsLayer { rotationZ = syncRotation }
                        )
                    }
                }
            }

            // ===== Reservoir Card =====
            val isDeviceOnline = isEsp32Online && bridgeStatus.equals("Connected", ignoreCase = true)
            ReservoirCard(
                waterLevel = waterLevel,
                wavePhase = wavePhase,
                isMotorOn = isMotorOn,
                isDeviceOnline = isDeviceOnline,
                onWaterLevelChange = { newLevel ->
                    if (!isDeviceOnline) {
                        waterLevel = newLevel
                    }
                }
            )

            // ===== Motor & Mains Status Bar =====
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                border = BorderStroke(1.dp, CardBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                val cmd = if (isMotorOn) "STOP" else "ON"
                                mqttManager.publishCommand(cmd)
                                addLog("MANUAL", "Motor command sent: $cmd")
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isMotorOn) Color(0xFF083344) else DarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isMotorOn) Icons.Default.WaterDrop else Icons.Default.PauseCircle,
                                contentDescription = null,
                                tint = if (isMotorOn) CyanAccent else Color.Gray,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "MOTOR",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                            Text(
                                text = if (isMotorOn) "Running" else "Standby",
                                color = if (isMotorOn) CyanAccent else Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(28.dp)
                            .background(Color.DarkGray.copy(alpha = 0.5f))
                    )

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isLightAvailable) Color(0xFF064E3B) else Color(0xFF450A0A)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isLightAvailable) Icons.Default.Bolt else Icons.Default.FlashOff,
                                contentDescription = null,
                                tint = if (isLightAvailable) EmeraldAccent else RoseAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "MAINS LINE",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 13.sp
                            )
                            Text(
                                text = if (isLightAvailable) "Available" else "Unavailable",
                                color = if (isLightAvailable) EmeraldAccent else RoseAccent,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // ===== Command Button Panel =====
            ButtonCard(
                autoLogicEnabled = autoLogicEnabled,
                onAutoLogicToggle = { enabled ->
                    val cmd = if (enabled) "AUTO_ENABLE" else "AUTO_DISABLE"
                    mqttManager.publishCommand(cmd)
                    addLog("AUTO-LOGIC", "Auto Mode Command Sent: $cmd")
                    showToast("Auto Mode: ${if (enabled) "ENABLED" else "DISABLED"}")
                },
                onCommand = { cmd, _ ->
                    mqttManager.publishCommand(cmd)
                    addLog("MQTT", "Tx → aarohi/cmd : \"$cmd\"")
                    showToast("Command: $cmd")
                }
            )
        }
    }
}

// --- Reservoir Card ---
@Composable
fun ReservoirCard(
    waterLevel: Float,
    wavePhase: Float,
    isMotorOn: Boolean,
    isDeviceOnline: Boolean = false,
    onWaterLevelChange: (Float) -> Unit = {}
) {
    val aquariumTransition = rememberInfiniteTransition(label = "NaturalAquarium")

    val naturalSwimTime by aquariumTransition.animateFloat(
        initialValue = 0f,
        targetValue = 62831.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 72_000_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "naturalSwimTime"
    )

    val pulsePhase by aquariumTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulsePhase"
    )

    val bubbleProgress by aquariumTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "bubbleProgress"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131E36).copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "OVERHEAD RESERVOIR",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Capacity 500L",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${(waterLevel * 5).toInt()} L",
                            color = CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Available",
                            color = Color.Gray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(220.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(32.dp))
                        .background(DarkSurface)
                        .border(2.dp, CyanGlow.copy(alpha = 0.35f), RoundedCornerShape(32.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 10.dp, top = 14.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf("100%", "75%", "50%", "25%", "0%").forEach {
                            Text(
                                it,
                                color = CyanGlow.copy(alpha = 0.5f),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val waterHeight = canvasHeight * (waterLevel / 100f)
                        val surfaceY = canvasHeight - waterHeight

                        val wavePath = Path()
                        wavePath.moveTo(0f, canvasHeight)
                        val waveAmplitude = if (isMotorOn) 8f else 4f
                        val waveFrequency = 0.04f
                        var wx = 0f
                        while (wx <= canvasWidth) {
                            val wy = surfaceY + sin(wx * waveFrequency + wavePhase) * waveAmplitude
                            wavePath.lineTo(wx, wy)
                            wx += 5f
                        }
                        wavePath.lineTo(canvasWidth, canvasHeight)
                        wavePath.close()

                        val waterColorStart = when {
                            waterLevel <= 20f -> RoseAccent
                            waterLevel >= 60f -> CyanAccent
                            else -> EmeraldAccent
                        }
                        val waterColorEnd = when {
                            waterLevel <= 20f -> Color(0xFF450A0A)
                            waterLevel >= 60f -> Color(0xFF05294E)
                            else -> Color(0xFF064E3B)
                        }

                        drawPath(
                            path = wavePath,
                            brush = Brush.verticalGradient(
                                colors = listOf(waterColorStart.copy(alpha = 0.85f), waterColorEnd),
                                startY = surfaceY,
                                endY = canvasHeight
                            )
                        )

                        fun getMarineState(
                            t: Float,
                            baseSpeedX: Float,
                            baseSpeedY: Float,
                            p1: Float,
                            p2: Float,
                            p3: Float,
                            minX: Float,
                            maxX: Float,
                            minY: Float,
                            maxY: Float
                        ): FloatArray {
                            val tx = t * baseSpeedX
                            val ty = t * baseSpeedY

                            val rawX = sin(tx + p1) +
                                    0.45f * sin(tx * 1.414f + p2) +
                                    0.30f * cos(tx * 0.618f + p3) +
                                    0.20f * sin(tx * 2.236f)
                            val rawY = sin(ty + p2) +
                                    0.45f * cos(ty * 1.732f + p1) +
                                    0.28f * sin(ty * 0.521f + p3)

                            val normX = (rawX / 1.95f).coerceIn(-1f, 1f)
                            val normY = (rawY / 1.73f).coerceIn(-1f, 1f)

                            val posX = minX + (maxX - minX) * (0.5f + 0.5f * normX)
                            val posY = minY + (maxY - minY) * (0.5f + 0.5f * normY)

                            val vx = baseSpeedX * (
                                    cos(tx + p1) +
                                            0.45f * 1.414f * cos(tx * 1.414f + p2) -
                                            0.30f * 0.618f * sin(tx * 0.618f + p3) +
                                            0.20f * 2.236f * cos(tx * 2.236f)
                                    )
                            val vy = baseSpeedY * (
                                    cos(ty + p2) -
                                            0.45f * 1.732f * sin(ty * 1.732f + p1) +
                                            0.28f * 0.521f * cos(ty * 0.521f + p3)
                                    )

                            val isFacingLeft = vx < 0f
                            val rawPitch = Math.toDegrees(
                                atan2(vy.toDouble(), kotlin.math.abs(vx).toDouble())
                            ).toFloat()
                            val pitch = rawPitch.coerceIn(-24f, 24f)
                            val curveBend = (-vx * 16f).coerceIn(-16f, 16f)

                            return floatArrayOf(
                                posX, posY,
                                if (isFacingLeft) -1f else 1f,
                                pitch, curveBend
                            )
                        }

                        if (waterLevel > 18f) {
                            val shark = getMarineState(
                                t = naturalSwimTime,
                                baseSpeedX = 0.11f, baseSpeedY = 0.06f,
                                p1 = 0.0f, p2 = 2.14f, p3 = 5.31f,
                                minX = canvasWidth * 0.04f, maxX = canvasWidth * 0.96f,
                                minY = surfaceY + 24f, maxY = surfaceY + waterHeight * 0.72f
                            )

                            val turtle = getMarineState(
                                t = naturalSwimTime,
                                baseSpeedX = 0.07f, baseSpeedY = 0.05f,
                                p1 = 3.41f, p2 = 0.89f, p3 = 2.65f,
                                minX = canvasWidth * 0.03f, maxX = canvasWidth * 0.97f,
                                minY = surfaceY + waterHeight * 0.35f, maxY = canvasHeight - 18f
                            )

                            val jelly = getMarineState(
                                t = naturalSwimTime,
                                baseSpeedX = 0.05f, baseSpeedY = 0.09f,
                                p1 = 1.77f, p2 = 4.12f, p3 = 1.05f,
                                minX = canvasWidth * 0.08f, maxX = canvasWidth * 0.92f,
                                minY = surfaceY + 20f, maxY = canvasHeight - 24f
                            )

                            val emitters = listOf(
                                Offset(shark[0], shark[1]),
                                Offset(turtle[0], turtle[1]),
                                Offset(jelly[0], jelly[1] - 12f)
                            )

                            for ((i, pos) in emitters.withIndex()) {
                                if (pos.y > surfaceY) {
                                    val progress = (bubbleProgress + i * 0.33f) % 1f
                                    val by = pos.y - progress * (pos.y - surfaceY)
                                    val bx =
                                        pos.x + sin(progress * (4 * Math.PI).toFloat() + i * 1.5f) * 6f
                                    val r = 2.5f + progress * 3.5f
                                    val alpha = when {
                                        progress < 0.1f -> (progress / 0.1f) * 0.7f
                                        progress > 0.85f -> ((1f - progress) / 0.15f) * 0.7f
                                        else -> 0.7f
                                    }
                                    if (by >= surfaceY && alpha > 0.05f) {
                                        drawCircle(
                                            color = CyanGlow.copy(alpha = alpha * 0.4f),
                                            radius = r,
                                            center = Offset(bx, by)
                                        )
                                        drawCircle(
                                            color = Color.White.copy(alpha = alpha * 0.8f),
                                            radius = r,
                                            center = Offset(bx, by),
                                            style = Stroke(width = 1.2f)
                                        )
                                        drawCircle(
                                            color = Color.White.copy(alpha = alpha),
                                            radius = r * 0.3f,
                                            center = Offset(bx - r * 0.35f, by - r * 0.35f)
                                        )
                                    }
                                }
                            }

                            // ============ SHARK ============
                            val isSharkFacingLeft = shark[2] < 0f
                            val sharkPitch = shark[3]
                            val sharkTailWiggle = sin(naturalSwimTime * 2.8f) * 13f
                            val sharkBend = shark[4]

                            withTransform({
                                translate(left = shark[0], top = shark[1])
                                scale(
                                    scaleX = if (isSharkFacingLeft) -1.3f else 1.3f,
                                    scaleY = 1.3f,
                                    pivot = Offset.Zero
                                )
                                rotate(
                                    degrees = if (isSharkFacingLeft) -sharkPitch else sharkPitch,
                                    pivot = Offset.Zero
                                )
                            }) {
                                val sharkBody = Path().apply {
                                    moveTo(42f, 0f)
                                    cubicTo(
                                        26f, -20f + sharkBend,
                                        -16f, -18f + sharkBend,
                                        -32f, sharkBend * 0.5f
                                    )
                                    cubicTo(-16f, 16f + sharkBend, 26f, 16f + sharkBend, 42f, 0f)
                                    close()
                                }
                                drawPath(
                                    path = sharkBody,
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(CyanGlow, Color(0xFF38BDF8), Color(0xFF1E293B)),
                                        startX = 42f, endX = -32f
                                    )
                                )

                                val bellyPath = Path().apply {
                                    moveTo(36f, 2f)
                                    cubicTo(
                                        24f, 13f + sharkBend,
                                        -12f, 13f + sharkBend,
                                        -28f, 1f + sharkBend * 0.5f
                                    )
                                    cubicTo(-12f, 5f + sharkBend, 24f, 5f + sharkBend, 36f, 2f)
                                    close()
                                }
                                drawPath(path = bellyPath, color = Color.White.copy(alpha = 0.7f))

                                val dorsalFin = Path().apply {
                                    moveTo(-4f, -14f + sharkBend)
                                    cubicTo(
                                        6f, -36f + sharkBend,
                                        16f, -38f + sharkBend,
                                        24f, -12f + sharkBend
                                    )
                                    close()
                                }
                                drawPath(
                                    path = dorsalFin,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color(0xFF0284C7), Color(0xFF1E293B)),
                                        startY = -38f, endY = -12f
                                    )
                                )

                                val pectoralFin = Path().apply {
                                    moveTo(6f, 6f + sharkBend)
                                    cubicTo(-6f, 26f + sharkBend, -18f, 26f + sharkBend, -6f, 8f + sharkBend)
                                    close()
                                }
                                drawPath(
                                    path = pectoralFin,
                                    color = Color(0xFF0369A1).copy(alpha = 0.9f)
                                )

                                withTransform({
                                    rotate(
                                        degrees = sharkTailWiggle + sharkBend,
                                        pivot = Offset(-32f, sharkBend * 0.5f)
                                    )
                                }) {
                                    val tailFin = Path().apply {
                                        moveTo(-32f, sharkBend * 0.5f)
                                        cubicTo(-44f, -18f, -58f, -34f, -62f, -24f)
                                        cubicTo(-48f, -6f, -42f, 0f, -54f, 18f)
                                        cubicTo(-48f, 24f, -36f, 12f, -32f, sharkBend * 0.5f)
                                        close()
                                    }
                                    drawPath(
                                        path = tailFin,
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xFF1E293B),
                                                Color(0xFF0284C7),
                                                Color.Transparent
                                            ),
                                            startX = -32f, endX = -62f
                                        )
                                    )
                                }

                                drawCircle(color = Color.Black, radius = 3.0f, center = Offset(28f, -4f))
                                drawCircle(color = CyanAccent, radius = 1.0f, center = Offset(29f, -4.5f))
                                for (g in 0..3) {
                                    val gx = 16f - g * 4.5f
                                    drawLine(
                                        color = CyanGlow.copy(alpha = 0.75f),
                                        start = Offset(gx, -6f + sharkBend),
                                        end = Offset(gx - 1.5f, 5f + sharkBend),
                                        strokeWidth = 1.5f
                                    )
                                }
                            }

                            // ============ TURTLE ============
                            val isTurtleFacingLeft = turtle[2] < 0f
                            val turtlePitch = turtle[3]
                            val flipperAngle = sin(naturalSwimTime * 1.5f) * 16f
                            val rearFlipperAngle = sin(naturalSwimTime * 1.5f + 1.2f) * 10f

                            withTransform({
                                translate(left = turtle[0], top = turtle[1])
                                scale(
                                    scaleX = if (isTurtleFacingLeft) -1.3f else 1.3f,
                                    scaleY = 1.3f,
                                    pivot = Offset.Zero
                                )
                                rotate(
                                    degrees = if (isTurtleFacingLeft) -turtlePitch else turtlePitch,
                                    pivot = Offset.Zero
                                )
                            }) {
                                withTransform({
                                    rotate(degrees = -rearFlipperAngle, pivot = Offset(-18f, -12f))
                                }) {
                                    val rearTopFlipper = Path().apply {
                                        moveTo(-18f, -12f)
                                        cubicTo(-30f, -22f, -34f, -14f, -26f, -6f)
                                        close()
                                    }
                                    drawPath(
                                        path = rearTopFlipper,
                                        color = EmeraldAccent.copy(alpha = 0.85f)
                                    )
                                }

                                withTransform({
                                    rotate(degrees = rearFlipperAngle, pivot = Offset(-18f, 12f))
                                }) {
                                    val rearBotFlipper = Path().apply {
                                        moveTo(-18f, 12f)
                                        cubicTo(-30f, 22f, -34f, 14f, -26f, 6f)
                                        close()
                                    }
                                    drawPath(
                                        path = rearBotFlipper,
                                        color = EmeraldAccent.copy(alpha = 0.85f)
                                    )
                                }

                                withTransform({
                                    rotate(degrees = -flipperAngle, pivot = Offset(12f, -10f))
                                }) {
                                    val topFlipper = Path().apply {
                                        moveTo(12f, -10f)
                                        cubicTo(22f, -30f, 4f, -34f, -8f, -16f)
                                        close()
                                    }
                                    drawPath(
                                        path = topFlipper,
                                        color = EmeraldAccent.copy(alpha = 0.95f)
                                    )
                                }

                                withTransform({
                                    rotate(degrees = flipperAngle, pivot = Offset(12f, 10f))
                                }) {
                                    val botFlipper = Path().apply {
                                        moveTo(12f, 10f)
                                        cubicTo(22f, 30f, 4f, 34f, -8f, 16f)
                                        close()
                                    }
                                    drawPath(
                                        path = botFlipper,
                                        color = EmeraldAccent.copy(alpha = 0.95f)
                                    )
                                }

                                val shell = Path().apply {
                                    addOval(Rect(left = -24f, top = -15f, right = 18f, bottom = 15f))
                                }
                                drawPath(
                                    path = shell,
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            EmeraldAccent,
                                            Color(0xFF047857),
                                            Color(0xFF064E3B)
                                        ),
                                        center = Offset(0f, 0f),
                                        radius = 22f
                                    )
                                )
                                drawPath(
                                    path = shell,
                                    color = Color(0xFF34D399).copy(alpha = 0.85f),
                                    style = Stroke(width = 1.8f)
                                )

                                for (hIdx in 0..2) {
                                    val hx = -12f + hIdx * 10f
                                    drawCircle(
                                        color = Color(0xFF064E3B).copy(alpha = 0.5f),
                                        radius = 4f,
                                        center = Offset(hx, 0f),
                                        style = Stroke(width = 1f)
                                    )
                                }

                                val head = Path().apply {
                                    addOval(Rect(left = 16f, top = -7f, right = 30f, bottom = 7f))
                                }
                                drawPath(path = head, color = EmeraldAccent)
                                drawCircle(color = Color.Black, radius = 1.6f, center = Offset(26f, -3f))
                            }

                            // ============ JELLYFISH ============
                            withTransform({
                                translate(left = jelly[0], top = jelly[1])
                                scale(scaleX = 1.3f, scaleY = 1.3f, pivot = Offset.Zero)
                            }) {
                                val pulseScale = 0.88f + 0.22f * sin(pulsePhase)

                                withTransform({
                                    scale(
                                        scaleX = pulseScale,
                                        scaleY = 1f / pulseScale,
                                        pivot = Offset(0f, 0f)
                                    )
                                }) {
                                    val cap = Path().apply {
                                        moveTo(-18f, 6f)
                                        cubicTo(-18f, -22f, 18f, -22f, 18f, 6f)
                                        cubicTo(10f, 3f, 0f, 8f, -8f, 3f)
                                        cubicTo(-13f, 8f, -16f, 3f, -18f, 6f)
                                        close()
                                    }
                                    drawPath(
                                        path = cap,
                                        brush = Brush.radialGradient(
                                            colors = listOf(
                                                CyanGlow.copy(alpha = 0.9f),
                                                RoseAccent.copy(alpha = 0.65f),
                                                Color.Transparent
                                            ),
                                            center = Offset(0f, -6f),
                                            radius = 18f
                                        )
                                    )
                                    drawPath(
                                        path = cap,
                                        color = CyanAccent,
                                        style = Stroke(width = 1.5f)
                                    )
                                }

                                for (tIdx in 0 until 5) {
                                    val tx = -14f + tIdx * 7.0f
                                    val tPath = Path().apply {
                                        moveTo(tx, 6f)
                                        val w1 = sin(pulsePhase + tIdx * 0.8f) * 6f
                                        val w2 = cos(pulsePhase + tIdx * 1.2f) * 7f
                                        cubicTo(tx + w1, 16f, tx + w2, 28f, tx + w1 * 0.5f, 38f)
                                    }
                                    drawPath(
                                        path = tPath,
                                        color = if (tIdx % 2 == 0) CyanGlow.copy(alpha = 0.8f)
                                        else RoseAccent.copy(alpha = 0.8f),
                                        style = Stroke(width = 1.5f)
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${waterLevel.toInt()}%",
                            color = Color.White,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace
                        )

                        if (waterLevel <= 20f && waterLevel > 0f || waterLevel >= 99.5f) {
                            Spacer(Modifier.height(4.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (waterLevel >= 99.5f) Color(0xFF083344) else Color(0xFF450A0A),
                                border = BorderStroke(
                                    1.dp,
                                    if (waterLevel >= 99.5f) CyanGlow else RoseAccent
                                )
                            ) {
                                Text(
                                    text = if (waterLevel >= 99.5f) "FULL" else "LOW LEVEL",
                                    color = if (waterLevel >= 99.5f) CyanGlow else RoseAccent,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ==================== DYNAMIC TANK LEVEL SLIDER ====================
            val glowAlpha by animateFloatAsState(
                targetValue = 0.65f,
                animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
                label = "glowAlpha"
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                val sliderWidth = this.maxWidth
                val thumbSize = 26.dp
                val thumbOffset = (sliderWidth - thumbSize) * (waterLevel / 100f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(50))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    CyanGlow.copy(alpha = 0.05f),
                                    CyanGlow.copy(alpha = glowAlpha * 0.35f),
                                    CyanGlow.copy(alpha = 0.05f)
                                )
                            )
                        )
                )

                Slider(
                    value = waterLevel,
                    onValueChange = { newValue ->
                        if (!isDeviceOnline) {
                            onWaterLevelChange(newValue)
                        }
                    },
                    onValueChangeFinished = { },
                    valueRange = 0f..100f,
                    enabled = !isDeviceOnline,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.Transparent,
                        activeTrackColor = CyanGlow,
                        inactiveTrackColor = DarkSurface,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent,
                        disabledActiveTrackColor = CyanGlow,
                        disabledInactiveTrackColor = DarkSurface,
                        disabledThumbColor = CyanGlow
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .offset(x = thumbOffset)
                        .size(thumbSize)
                        .shadow(elevation = 6.dp, shape = CircleShape)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color.White, CyanAccent, CyanGlow)
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = Color.White.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .align(Alignment.TopStart)
                            .offset(x = 5.dp, y = 4.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                    )
                }
            }
        }
    }
}

// --- Button Control Panel ---
@Composable
fun ButtonCard(
    autoLogicEnabled: Boolean,
    onAutoLogicToggle: (Boolean) -> Unit,
    onCommand: (String, String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        border = BorderStroke(1.dp, CardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (!autoLogicEnabled) {
                        val warningTransition = rememberInfiniteTransition(label = "warning3D")

                        val warningScale by warningTransition.animateFloat(
                            initialValue = 0.82f,
                            targetValue = 1.18f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "warningScale"
                        )

                        val warningGlow by warningTransition.animateFloat(
                            initialValue = 0.25f,
                            targetValue = 0.85f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(700, easing = FastOutSlowInEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "warningGlow"
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .graphicsLayer {
                                        scaleX = warningScale
                                        scaleY = warningScale
                                        shadowElevation = 10f
                                        alpha = 0.95f
                                    }
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                Color.White,
                                                RoseAccent,
                                                Color(0xFF7F1D1D)
                                            )
                                        )
                                    )
                                    .border(1.dp, RoseAccent.copy(alpha = warningGlow), CircleShape)
                            )

                            Spacer(Modifier.width(5.dp))

                            Text(
                                text = "USE BUTTON",
                                color = RoseAccent,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        Spacer(Modifier.height(2.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = CyanGlow,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "MOTOR CONTROLS",
                            color = CyanGlow,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AUTO MODE",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(Modifier.width(6.dp))

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (autoLogicEnabled) EmeraldAccent.copy(alpha = 0.15f)
                        else RoseAccent.copy(alpha = 0.15f),
                        border = BorderStroke(
                            1.dp,
                            if (autoLogicEnabled) EmeraldAccent.copy(alpha = 0.5f)
                            else RoseAccent.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(
                            text = if (autoLogicEnabled) "ENABLE" else "DISABLE",
                            color = if (autoLogicEnabled) EmeraldAccent else RoseAccent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    Switch(
                        checked = autoLogicEnabled,
                        onCheckedChange = onAutoLogicToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = EmeraldAccent,
                            uncheckedThumbColor = Color.LightGray,
                            uncheckedTrackColor = DarkSurface,
                            checkedBorderColor = EmeraldAccent,
                            uncheckedBorderColor = RoseAccent.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.scale(0.75f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommandTile("ON", "No Auto off", EmeraldAccent, Modifier.weight(1f)) {
                    onCommand("ON", "Motor started (no auto off).")
                }
                CommandTile("STOP", "Force Stop", RoseAccent, Modifier.weight(1f)) {
                    onCommand("STOP", "Motor force stopped.")
                }
                CommandTile(
                    label = "Smart ON",
                    subtitle = if (autoLogicEnabled) "Auto Mode" else "Disabled",
                    tint = if (autoLogicEnabled) CyanGlow else Color.Gray,
                    modifier = Modifier.weight(1f)
                ) {
                    if (autoLogicEnabled) {
                        onCommand("Smart ON", "Smart auto mode engaged.")
                    } else {
                        onCommand("Smart ON", "Auto Mode is currently disabled.")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CommandTile("1 Min", "Quick Run", AmberAccent, Modifier.weight(1f)) {
                    onCommand("1 Min", "Motor will run for 1 minute.")
                }
                CommandTile("Shower", "8 minute", CyanGlow, Modifier.weight(1f)) {
                    onCommand("Shower", "Shower water supply for 8 min.")
                }
            }
        }
    }
}

// --- 3D Press-Effect Command Tile ---
@Composable
fun CommandTile(
    label: String,
    subtitle: String,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "buttonScale"
    )

    val elevation by animateDpAsState(
        targetValue = if (isPressed) 2.dp else 8.dp,
        animationSpec = tween(120),
        label = "buttonElevation"
    )

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .shadow(
                elevation = elevation,
                shape = RoundedCornerShape(14.dp),
                ambientColor = tint.copy(alpha = 0.35f),
                spotColor = tint.copy(alpha = 0.55f)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        tint.copy(alpha = 0.22f),
                        DarkElevated.copy(alpha = 0.95f),
                        Color(0xFF0A1020)
                    )
                )
            )
            .border(
                width = 1.dp,
                color = tint.copy(alpha = if (isPressed) 0.9f else 0.45f),
                shape = RoundedCornerShape(14.dp)
            )
            .clickable {
                isPressed = true
                onClick()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = tint,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .height(2.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                tint.copy(alpha = 0.85f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(130L)
            isPressed = false
        }
    }
}