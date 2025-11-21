package com.example.aerotalk.feature.home

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.aerotalk.AppID
import com.example.aerotalk.AppSign
import com.example.aerotalk.MainActivity


import com.google.firebase.auth.FirebaseAuth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController,activity: MainActivity) {
    // ... (rest of the code remains the same until the ModalBottomSheet block)
    // val context = LocalContex as Activity

    // Init Zego
    LaunchedEffect(Unit) {
        FirebaseAuth.getInstance().currentUser?.let {
            activity.initZegoService(
                appID = AppID,
                appSign = AppSign,
                userID = it.uid,
                userName = it.email ?: "Unknown"
            )
        }
    }

    val viewModel = hiltViewModel<HomeViewModel>()
    val channels by viewModel.channels.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val addChannelDialog = remember { mutableStateOf(false) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ------------------------------
    // MAIN UI
    // ------------------------------
    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addChannelDialog.value = true },
                containerColor = Color(0xFF1976D2)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        },
        containerColor = Color(0xFF121212)
    ) { padding ->

        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            Text(
                "Messages",
                modifier = Modifier.padding(16.dp),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search channels", color = Color.Gray) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(50.dp)),
                textStyle = TextStyle(color = Color.White),
                trailingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF1E1E1E),
                    unfocusedContainerColor = Color.DarkGray,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            LazyColumn {
                items(channels.filter {
                    it.name.contains(searchQuery, ignoreCase = true)
                }) { channel ->

                    ChannelItem(
                        channelName = channel.name,
                        shouldShowCallButtons = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E1E1E))
                            .clickable {
                                navController.navigate("chat/${channel.id}&${channel.name}")
                            }
                            .padding(16.dp),
                        onCall = {},
                        onClick = {}
                    )
                }
            }
        }
    }

    // Bottom Sheet — Add Channel
    if (addChannelDialog.value) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { addChannelDialog.value = false },
            // Setting a container color for the bottom sheet content
            containerColor = Color(0xFF1E1E1E)
        ) {
            AddChannelDialog(
                onAddChannel = { newName ->
                    viewModel.addChannel(newName)
                    addChannelDialog.value = false
                },
                // Pass a dark color to the dialog so it matches the sheet background
                dialogBackgroundColor = Color(0xFF1E1E1E)
            )
        }
    }
}


@Composable
fun ChannelItem(
    channelName: String,
    modifier: Modifier = Modifier,
    shouldShowCallButtons: Boolean = true,
    onClick: () -> Unit,
    onCall: () -> Unit
) {
    // ... (ChannelItem implementation remains the same)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {

        Box(
            Modifier.size(50.dp).clip(CircleShape).background(Color(0xFF37474F)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                channelName.first().uppercase(),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(Modifier.weight(1f)) {
            Text(channelName, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Last message preview...", color = Color.Gray, fontSize = 13.sp)
        }

        if (shouldShowCallButtons) {
            IconButton(onClick = onCall) {
                Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF1E88E5))
            }
        }
    }
}

@Composable
fun AddChannelDialog(onAddChannel: (String) -> Unit, dialogBackgroundColor: Color = Color.White) {
    var channelName by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxWidth()
            .background(dialogBackgroundColor) // Use the passed color
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text("Add Channel", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Spacer(Modifier.height(16.dp))

        TextField(
            value = channelName,
            onValueChange = { channelName = it },
            label = { Text("Channel Name", color = Color.Gray) },
            singleLine = true,
            textStyle = TextStyle(color = Color.White),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF2C2C2C),
                unfocusedContainerColor = Color(0xFF2C2C2C),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            )
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (channelName.isNotBlank()) {
                    onAddChannel(channelName.trim())
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = channelName.isNotBlank()
        ) {
            Text("Add")
        }
    }
}