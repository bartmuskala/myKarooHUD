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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
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
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        val config by streamMyKarooHudConfig().collectAsState(initial = MyKarooHudConfig())

        // Local editable copies — updated only on Save
        var wPrimeText by remember(config.wPrimeJoules) { mutableStateOf(config.wPrimeJoules.toString()) }
        var cpText     by remember(config.cp)           { mutableStateOf(config.cp.toString()) }
        var saveError  by remember { mutableStateOf<String?>(null) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // ---- Scrollable content ----
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 16.dp)
            ) {
                Text("myKarooHUD Settings", style = MaterialTheme.typography.headlineMedium)
                Spacer(modifier = Modifier.height(24.dp))

                // W' Prime
                Text("W′ Prime Capacity (Joules)")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = wPrimeText,
                    onValueChange = { wPrimeText = it; saveError = null },
                    label = { Text("Joules (e.g. 16000)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Critical Power
                Text("Critical Power / FTP (Watts)")
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = cpText,
                    onValueChange = { cpText = it; saveError = null },
                    label = { Text("Watts (e.g. 250)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                saveError?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error)
                }

                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Attribution
                Text("About", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                val links = listOf(
                    "Barberfish" to "https://github.com/hammerheadnav/barberfish",
                    "karoo-headwind" to "https://github.com/timklge/karoo-headwind",
                    "karoo-wprimebalance" to "https://github.com/hammerheadnav/karoo-wprimebalance"
                )

                Text(
                    "Built on the great work of the open-source cycling community ❤️",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                links.forEach { (name, url) ->
                    val annotated = buildAnnotatedString {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ) { append("→ $name") }
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                        },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(annotated)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ---- Sticky Save button at bottom ----
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    val newWPrime = wPrimeText.toIntOrNull()
                    val newCp    = cpText.toIntOrNull()
                    if (newWPrime == null || newCp == null) {
                        saveError = "Please enter valid numbers."
                        return@Button
                    }
                    scope.launch {
                        saveMyKarooHudConfig(config.copy(wPrimeJoules = newWPrime, cp = newCp))
                    }
                    saveError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text("Save")
            }
        }
    }
}
