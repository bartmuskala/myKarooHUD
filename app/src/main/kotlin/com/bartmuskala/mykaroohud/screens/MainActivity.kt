package com.bartmuskala.mykaroohud.screens

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bartmuskala.mykaroohud.extension.streamMyKarooHudConfig
import com.bartmuskala.mykaroohud.extension.saveMyKarooHudConfig
import com.bartmuskala.mykaroohud.extension.MyKarooHudConfig
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
        val config by streamMyKarooHudConfig().collectAsState(initial = MyKarooHudConfig())

        Column(modifier = Modifier.padding(16.dp)) {
            Text("myKarooHUD Settings", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(24.dp))

            Text("W' prime Capacity (Joules)")
            var wPrimeText by remember(config.wPrimeJoules) { mutableStateOf(config.wPrimeJoules.toString()) }
            OutlinedTextField(
                value = wPrimeText,
                onValueChange = { 
                    wPrimeText = it
                    it.toIntOrNull()?.let { joules ->
                        scope.launch { saveMyKarooHudConfig(config.copy(wPrimeJoules = joules)) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text("Critical Power (CP / FTP in Watts)")
            var cpText by remember(config.cp) { mutableStateOf(config.cp.toString()) }
            OutlinedTextField(
                value = cpText,
                onValueChange = { 
                    cpText = it
                    it.toIntOrNull()?.let { cp ->
                        scope.launch { saveMyKarooHudConfig(config.copy(cp = cp)) }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
