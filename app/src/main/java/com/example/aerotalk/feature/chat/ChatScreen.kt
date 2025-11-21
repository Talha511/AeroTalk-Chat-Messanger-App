package com.example.aerotalk.feature.chat

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.aerotalk.R
import com.example.aerotalk.model.Message
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import androidx.core.net.toUri

private val Teal = Color(0xFF33C2CE)
private val DeepBlue = Color(0xFF163D6F)

// local path to logo you uploaded
private const val LOGO_FILE_URI = "file:///mnt/data/logo.png"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    navController: androidx.navigation.NavController,
    channelId: String,
    channelName: String,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val typing by viewModel.typing.collectAsState()
    val replyMessage by viewModel.replyMessage.collectAsState()

    // Launch listeners
    LaunchedEffect(channelId) {
        viewModel.listenForMessages(channelId)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = LOGO_FILE_URI.toUri(),
                            contentDescription = "logo",
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = channelName, color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(painter = painterResource(R.drawable.ic_arrow_back), contentDescription = "Back", tint = Color.White)
                    }
                }
            )
        },
        containerColor = Color.Transparent,
        modifier = Modifier.fillMaxSize()
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Teal, DeepBlue)))
                .padding(inner)
        ) {

            Column(modifier = Modifier.fillMaxSize()) {

                // Messages list
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    reverseLayout = false
                ) {
                    // optional header / channel info
                    item {
                        // Channel header card
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Group: $channelName", color = Color.White)
                            }
                        }
                    }

                    items(messages) { message ->
                        ChatBubble(
                            message = message,
                            onReply = { viewModel.setReply(it) },
                            onMarkSeen = { viewModel.markMessageSeen(channelId) }
                        )
                    }

                    // spacer bottom for input
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }

                // Typing indicator
                AnimatedVisibility(visible = typing) {
                    Row(modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)) {
                        Text(text = "Typing...", color = Color.White.copy(alpha = 0.9f))
                    }
                }

                // Reply preview (if any)
                replyMessage?.let { rp ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.9f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Replying to ${rp.senderName}: ", fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = rp.message ?: "(image)", maxLines = 1)
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(onClick = { viewModel.setReply(null) }) {
                                Text("Cancel")
                            }
                        }
                    }
                }

                // Bottom input bar
                ChatInputBar(
                    onSend = { text ->
                        viewModel.sendMessage(channelId, text, replyMessage)
                    },
                    onAttach = { viewModel.sendImageMessage(it, channelId) },
                    onTyping = { isTyping -> viewModel.setTyping(channelId, isTyping) },
                    clearReply = { viewModel.setReply(null) },
                    currentReply = replyMessage
                )
            }
        }
    }
}

/**
 * Input bar with attach + text + send.
 * onAttach receives a Uri - you should handle image picker in the caller and send the Uri here
 */
@Composable
fun ChatInputBar(
    onSend: (String) -> Unit,
    onAttach: (Uri) -> Unit,
    onTyping: (Boolean) -> Unit,
    clearReply: () -> Unit,
    currentReply: Message?
) {
    val keyboard = LocalSoftwareKeyboardController.current
    var text by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }

    // typing debounce: send typing true then false after timeout
    LaunchedEffect(text) {
        val typingNow = text.isNotBlank()
        if (typingNow != isTyping) {
            isTyping = typingNow
            onTyping(isTyping)
        }
        if (!typingNow) {
            // short delay to ensure other side sees stop typing
            delay(300)
            onTyping(false)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.06f))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            // caller should handle launching pickers; here we show a placeholder behavior
            // Example: open gallery/camera -> then call onAttach(uri)
        }) {
            Icon(painter = painterResource(R.drawable.attach), contentDescription = "Attach", tint = Color.White)
        }

        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Type a message", color = Color.White.copy(alpha = 0.6f)) },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .animateContentSize(),
            // CORRECTED: Use TextFieldDefaults.colors() function with required parameters
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                disabledTextColor = Color.White.copy(alpha = 0.5f) // Added for completeness
            ),
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                if (text.isNotBlank()) {
                    onSend(text.trim())
                    text = ""
                    clearReply()
                }
                keyboard?.hide()
            })
        )

        IconButton(onClick = {
            if (text.isNotBlank()) {
                onSend(text.trim())
                text = ""
                clearReply()
            }
        }) {
            Icon(painter = painterResource(R.drawable.send), contentDescription = "Send", tint = Color.White)
        }
    }
}

/**
 * ChatBubble showing message content, image, timestamp, ticks and swipe-to-reply
 */
@Composable
fun ChatBubble(
    message: Message,
    onReply: (Message) -> Unit,
    onMarkSeen: () -> Unit
) {
    val isCurrentUser = message.senderId == Firebase.auth.currentUser?.uid
    val bubbleColor = if (isCurrentUser) Teal else Color(0xFF2C2C2C)

    // pointer-based swipe detection
    val dragThreshold = 60f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        // optional avatar for others
        if (!isCurrentUser) {
            AsyncImage(
                model = message.senderImage ?: LOGO_FILE_URI.toUri(),
                contentDescription = "avatar",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        } else {
            Spacer(modifier = Modifier.width(48.dp))
        }

        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dragAmount ->
                        // right swipe on incoming messages => reply
                        if (!isCurrentUser && dragAmount > dragThreshold) {
                            onReply(message)
                        }
                        // left swipe on own message => quick reply to self (optional)
                        if (isCurrentUser && dragAmount < -dragThreshold) {
                            onReply(message)
                        }
                    }
                }
                .widthIn(max = 320.dp)
        ) {
            Column(
                modifier = Modifier
                    .background(color = bubbleColor, shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                // reply preview (if present)
                message.replyToMessage?.let { replyPreview ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.08f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = replyPreview, modifier = Modifier.padding(8.dp), color = Color.White.copy(alpha = 0.9f), maxLines = 1)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (message.imageUrl != null) {
                    AsyncImage(
                        model = message.imageUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(200.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(text = message.message.orEmpty(), color = Color.White)
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    Text(text = message.createdAt.toPrettyTime(), color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.bodySmall)

                    Spacer(modifier = Modifier.width(8.dp))

                    // ticks
                    if (isCurrentUser) {
                        when (message.status) {
                            "sent" -> Icon(Icons.Default.Check, contentDescription = "sent", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                            "delivered" -> Icon(Icons.Default.DoneAll, contentDescription = "delivered", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                            "seen" -> Icon(Icons.Default.DoneAll, contentDescription = "seen", tint = Color(0xFF0A84FF), modifier = Modifier.size(16.dp))
                            else -> Icon(Icons.Default.Check, contentDescription = "sent", tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }

    // mark seen when bubble is composed and is not current user's message
    LaunchedEffect(message) {
        if (!isCurrentUser) {
            // small delay to simulate reading (or add visibility detection)
            delay(300)
            onMarkSeen()
        }
    }
}

/** Utility extension: format epoch millis to hh:mm a (e.g., 09:23 PM) */
fun Long.toPrettyTime(): String {
    return try {
        val df = SimpleDateFormat("hh:mm a", Locale.getDefault())
        df.format(Date(this))
    } catch (e: Exception) {
        ""
    }
}