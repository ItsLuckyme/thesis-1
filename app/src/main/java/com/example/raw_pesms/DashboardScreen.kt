package com.example.raw_pesms

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class DashboardScreen : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Compose UI goes here
        setContent {
            MaterialTheme {
                DashboardScreenUI()
            }
        }
    }
}

// Rename your Composable function to avoid conflict
@Composable
fun DashboardScreenUI() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to the Dashboard")
        Button(onClick = { /* TODO */ }) {
            Text("Mark Attendance")
        }
    }
}