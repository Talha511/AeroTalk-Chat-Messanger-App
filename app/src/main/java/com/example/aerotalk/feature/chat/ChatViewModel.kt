package com.example.aerotalk.feature.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.example.aerotalk.R
import com.example.aerotalk.SupabaseStorageUtils
import com.example.aerotalk.model.Message
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject
import com.google.auth.oauth2.GoogleCredentials

@HiltViewModel
class ChatViewModel @Inject constructor(
    @param:ApplicationContext val context: Context
) : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()

    private val _typing = MutableStateFlow(false)
    val typing = _typing.asStateFlow()

    private val _replyMessage = MutableStateFlow<Message?>(null)
    val replyMessage = _replyMessage.asStateFlow()

    fun setReply(message: Message?) {
        _replyMessage.value = message
    }

    fun setTyping(channelID: String, isTyping: Boolean) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        db.child("channels").child(channelID).child("typing").child(uid).setValue(isTyping)
    }

    fun listenTyping(channelID: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        db.child("channels").child(channelID).child("typing")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    var someoneTyping = false
                    snapshot.children.forEach {
                        if (it.key != uid && it.value == true) someoneTyping = true
                    }
                    _typing.value = someoneTyping
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun sendMessage(
        channelID: String,
        messageText: String?,
        replyMessage: Message? = null,
        image: String? = null
    ) {
        val msgId = db.push().key ?: UUID.randomUUID().toString()
        val currentUser = Firebase.auth.currentUser ?: return

        val message = Message(
            id = msgId,
            senderId = currentUser.uid,
            message = messageText,
            createdAt = System.currentTimeMillis(),
            senderName = currentUser.displayName ?: currentUser.email ?: "User",
            imageUrl = image,
            status = "sent",
            replyToId = replyMessage?.id,
            replyToMessage = replyMessage?.message
        )

        db.child("messages").child(channelID).child(msgId).setValue(message)
            .addOnSuccessListener {
                updateMessageStatusDelivered(channelID, msgId)
                postNotificationToUsers(channelID, message.senderName, messageText ?: "")
            }

        _replyMessage.value = null
    }

    fun markMessageSeen(channelID: String) {
        val uid = Firebase.auth.currentUser?.uid ?: return
        val time = System.currentTimeMillis()

        val ref = db.child("channels").child(channelID).child("lastSeen").child(uid)
        ref.setValue(time)
    }

    private fun updateMessageStatusDelivered(channelID: String, msgId: String) {
        db.child("messages").child(channelID).child(msgId).child("status").setValue("delivered")
    }

    fun listenForMessages(channelID: String) {
        db.child("messages").child(channelID)
            .orderByChild("createdAt")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = mutableListOf<Message>()
                    snapshot.children.forEach { data ->
                        val msg = data.getValue(Message::class.java)
                        msg?.let { list.add(it) }
                    }
                    _messages.value = list

                    markMessageSeen(channelID)
                }

                override fun onCancelled(error: DatabaseError) {}
            })

        listenTyping(channelID)
    }

    fun sendImageMessage(uri: Uri, channelID: String) {
        viewModelScope.launch {
            val storageUtils = SupabaseStorageUtils(context)
            val downloadUri = storageUtils.uploadImage(uri)
            if (downloadUri != null) {
                sendMessage(channelID, null, replyMessage.value, downloadUri)
            }
        }
    }

    private fun postNotificationToUsers(
        channelID: String,
        senderName: String,
        messageContent: String
    ) {
        val fcmUrl = "https://fcm.googleapis.com/v1/projects/aerotalk-58636/messages:send"

        val jsonBody = JSONObject().apply {
            put("message", JSONObject().apply {
                put("topic", "group_$channelID")
                put("notification", JSONObject().apply {
                    put("title", "New Message")
                    put("body", "$senderName: $messageContent")
                })
            })
        }

        val request = object : StringRequest(Method.POST, fcmUrl, Response.Listener {
        }, Response.ErrorListener {}) {
            override fun getBody() = jsonBody.toString().toByteArray()

            override fun getHeaders(): MutableMap<String, String> {
                return hashMapOf(
                    "Authorization" to "Bearer ${getAccessToken()}",
                    "Content-Type" to "application/json"
                )
            }
        }

        Volley.newRequestQueue(context).add(request)
    }

    private fun getAccessToken(): String {
        val inputStream = context.resources.openRawResource(R.raw.aerotalk_key)
        val googleCreds = GoogleCredentials.fromStream(inputStream)
            .createScoped(listOf("https://www.googleapis.com/auth/firebase.messaging"))
        return googleCreds.refreshAccessToken().tokenValue
    }
}