package com.example.aerotalk

import android.content.Context
import android.net.Uri
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.storage.Storage
import java.util.UUID

class SupabaseStorageUtils(val context: Context) {

    val supabase = createSupabaseClient(
        "https://fdeivjhixldzuqlsdgda.supabase.co",
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImZkZWl2amhpeGxkenVxbHNkZ2RhIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjM1MzY1NTIsImV4cCI6MjA3OTExMjU1Mn0.aRetXH3h0UzxWYHlHxgwKNVOxjMyZlFLe9Q8OOwQzIU"

    ) {
        install(Storage)
    }

    suspend fun uploadImage(uri: Uri): String? {
        try {
            val extension = uri.path?.substringAfterLast(".") ?: "jpg"
            val fileName = "${UUID.randomUUID()}.$extension"
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            supabase.storage.from(BUCKET_NAME).upload(fileName, inputStream.readBytes())
            val publicUrl = supabase.storage.from(BUCKET_NAME).publicUrl(fileName)
            return publicUrl
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }


    companion object {
        const val BUCKET_NAME = "aerotalk_images"
    }
}