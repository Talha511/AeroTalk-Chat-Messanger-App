package com.example.aerotalk.feature.home

import androidx.lifecycle.ViewModel
import com.example.aerotalk.model.Channel
import com.google.firebase.database.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

    private val db = FirebaseDatabase.getInstance().reference

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels = _channels.asStateFlow()

    init {
        listenForChannels()
    }

    private fun listenForChannels() {
        db.child("channels")
            .orderByChild("createdAt")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {
                    val list = snapshot.children.mapNotNull {
                        it.getValue(Channel::class.java)
                    }
                    _channels.value = list
                }

                override fun onCancelled(error: DatabaseError) {}
            })
    }

    fun addChannel(name: String) {
        val id = db.child("channels").push().key ?: UUID.randomUUID().toString()

        val channel = Channel(
            id = id,
            name = name,
            createdAt = System.currentTimeMillis()
        )

        db.child("channels").child(id).setValue(channel)
    }
}