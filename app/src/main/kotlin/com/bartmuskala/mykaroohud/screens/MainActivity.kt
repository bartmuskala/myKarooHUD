package com.bartmuskala.mykaroohud.screens

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.bartmuskala.mykaroohud.extension.MyKarooHudConfig
import com.bartmuskala.mykaroohud.extension.saveMyKarooHudConfig
import com.bartmuskala.mykaroohud.extension.streamMyKarooHudConfig
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SettingsScreen()
                }
            }
        }
    }

    @Composable
    fun SettingsScreen() {
        val scope       = rememberCoroutineScope()
        val context     = LocalContext.current
        val config by streamMyKarooHudConfig().collectAsState(initial = MyKarooHudConfig())

        // Local editable state — only written to DataStore on Save
        var cpText     by remember(config.cp)           { mutableStateOf(config.cp.toString()) }
        var wPrimeText by remember(config.wPrimeJoules) { mutableStateOf(config.wPrimeJoules.toString()) }
        var saveError  by remember { mutableStateOf<String?>(null) }
        var saved      by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {

            // ── Scrollable body ─────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                Text("myKarooHUD Settings", style = MaterialTheme.typography.headlineMedium)

                Spacer(Modifier.height(24.dp))

                // ── W′ Prime section ─────────────────────────────────────────
                Text("W′ Prime Balance", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "W′ prime models how much high-intensity energy (above Critical Power) you " +
                    "have left. It depletes when power > CP and recovers exponentially " +
                    "when power < CP — faster when you ride well below CP " +
                    "(Skiba 2012 model, ODE form).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )

                Spacer(Modifier.height(16.dp))

                // Critical Power
                Text("Critical Power / FTP (Watts)", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(
                    "Your 1-hour maximum sustainable power. W′ depletes at rate " +
                    "(P − CP) W/s above this threshold and recovers below it. " +
                    "Set to your FTP or your actual CP if you know it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = cpText,
                    onValueChange = { cpText = it; saveError = null; saved = false },
                    label = { Text("Watts (e.g. 250)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                // W′ Capacity
                Text("W′ Prime Capacity (Joules)", style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium)
                Text(
                    "Total anaerobic work capacity above CP in Joules. " +
                    "Typical values range from 10 000 J (trained sprinter) to 25 000 J. " +
                    "Recreational cyclists are often around 12 000–16 000 J. " +
                    "Changing this resets the field immediately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = wPrimeText,
                    onValueChange = { wPrimeText = it; saveError = null; saved = false },
                    label = { Text("Joules (e.g. 16000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(4.dp))
                Text(
                    "ℹ️ How the recovery time constant τ works: τ = 546 × e^(−0.01 × (CP − P̄sub)) + 316 s. " +
                    "When you ride far below CP (large CP − P̄sub), τ is smaller and recovery is faster. " +
                    "When you ride just below CP, τ is large (~860 s) and recovery is slow.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                saveError?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // ── Attribution ────────────────────────────────────────────
                Text("About", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Built on the great work of the open-source cycling community ❤️",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(4.dp))

                listOf(
                    "Barberfish (base extension framework)" to
                        "https://github.com/hammerheadnav/barberfish",
                    "karoo-headwind (wind data)" to
                        "https://github.com/timklge/karoo-headwind",
                    "karoo-wprimebalance (W′ research)" to
                        "https://github.com/hammerheadnav/karoo-wprimebalance",
                ).forEach { (name, url) ->
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(buildAnnotatedString {
                            withStyle(SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )) { append("→ $name") }
                        })
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            // ── Sticky Save button ───────────────────────────────────────────
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))

            if (saved) {
                Text("✓ Settings saved",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp))
            }

            Button(
                onClick = {
                    val newCp     = cpText.toIntOrNull()
                    val newWPrime = wPrimeText.toIntOrNull()
                    when {
                        newCp == null || newCp <= 0 ->
                            saveError = "Critical Power must be a positive number."
                        newWPrime == null || newWPrime < 1000 ->
                            saveError = "W′ Capacity must be at least 1000 J."
                        else -> {
                            scope.launch {
                                saveMyKarooHudConfig(
                                    config.copy(cp = newCp, wPrimeJoules = newWPrime)
                                )
                            }
                            saveError = null
                            saved = true
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            ) {
                Text("Save")
            }
        }
    }
}
