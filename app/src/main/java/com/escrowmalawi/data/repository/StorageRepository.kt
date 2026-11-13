package com.escrowmalawi.data.repository



import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/**
 * Handles all image uploads via Cloudinary (free tier).
 *
 * Setup (one-time):
 *  1. Sign up free at https://cloudinary.com
 *  2. Dashboard → copy your "Cloud name"
 *  3. Settings → Upload → "Add upload preset" → set Mode = Unsigned → save
 *  4. Paste both values into AppModule.kt
 *
 * Images land at: res.cloudinary.com/{cloudName}/image/upload/{publicId}
 */
@Singleton
class StorageRepository @Inject constructor(
    @Named("cloudinaryCloudName")   private val cloudName    : String,
    @Named("cloudinaryUploadPreset") private val uploadPreset: String
) {
    companion object {
        private const val TAG = "StorageRepository"
        private const val TIMEOUT_MS = 30_000L
        private const val MAX_PX     = 1024
        private const val JPEG_Q     = 80
    }

    /**
     * Upload an image URI to Cloudinary and return the public secure URL.
     * The URL can be stored directly in Firestore (it's just a short string).
     */
    suspend fun uploadListingImage(
        context : Context,
        uid     : String,          // used as folder prefix for organisation
        imageUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                // 1. Read bytes from URI
                val raw = context.contentResolver
                    .openInputStream(imageUri)
                    ?.use { it.readBytes() }
                    ?: return@withTimeout Result.failure(Exception("Cannot read image"))

                // 2. Compress
                val bytes = compressImage(raw)
                Log.d(TAG, "Uploading ${bytes.size} bytes to Cloudinary (folder: listings/$uid)")

                // 3. Multipart POST to Cloudinary
                val secureUrl = postToCloudinary(bytes, "listings/$uid")
                Log.d(TAG, "Upload OK → $secureUrl")
                Result.success(secureUrl)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloudinary upload failed", e)
            Result.failure(e)
        }
    }

    /** Upload one dispute evidence photo (folder disputes/{tradeId}) and return its public URL. */
    suspend fun uploadEvidenceImage(
        context : Context,
        tradeId : String,
        imageUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            withTimeout(TIMEOUT_MS) {
                val raw = context.contentResolver
                    .openInputStream(imageUri)
                    ?.use { it.readBytes() }
                    ?: return@withTimeout Result.failure(Exception("Cannot read image"))
                val bytes = compressImage(raw)
                Result.success(postToCloudinary(bytes, "disputes/$tradeId"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Evidence upload failed", e)
            Result.failure(e)
        }
    }

    /** Delete a Cloudinary image by public ID (best-effort, non-fatal). */
    suspend fun deleteImage(imageUrl: String): Result<Unit> {
        // Cloudinary deletion via unsigned API requires a signed request (needs API secret).
        // For now, images are effectively orphaned on delete — acceptable for a free-tier app.
        // To enable: use Admin API with your API Secret server-side.
        Log.d(TAG, "deleteImage: $imageUrl (no-op on free unsigned preset)")
        return Result.success(Unit)
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private fun postToCloudinary(imageBytes: ByteArray, folder: String): String {
        val boundary = "----Boundary${UUID.randomUUID().toString().replace("-", "")}"
        val endpoint = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"

        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            doOutput        = true
            requestMethod   = "POST"
            connectTimeout  = 15_000
            readTimeout     = 25_000
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        // Build multipart body
        val body = ByteArrayOutputStream()
        val writer = PrintWriter(OutputStreamWriter(body, Charsets.UTF_8), true)

        fun field(name: String, value: String) {
            writer.append("--$boundary\r\n")
            writer.append("Content-Disposition: form-data; name=\"$name\"\r\n\r\n")
            writer.append("$value\r\n")
            writer.flush()
        }

        field("upload_preset", uploadPreset)
        field("folder",        folder)

        // Binary image field
        writer.append("--$boundary\r\n")
        writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"img.jpg\"\r\n")
        writer.append("Content-Type: image/jpeg\r\n\r\n")
        writer.flush()
        body.write(imageBytes)
        writer.append("\r\n--$boundary--\r\n")
        writer.flush()

        conn.outputStream.use { it.write(body.toByteArray()) }

        val code     = conn.responseCode
        val response = (if (code == 200) conn.inputStream else conn.errorStream)
            .bufferedReader().readText()
        conn.disconnect()

        if (code != 200) throw Exception("Cloudinary HTTP $code: $response")

        return JSONObject(response).getString("secure_url")
    }

    private fun compressImage(bytes: ByteArray): ByteArray {
        return try {
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                ?: return bytes
            val scaled = scaleBitmap(bmp, MAX_PX)
            ByteArrayOutputStream().also {
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, JPEG_Q, it)
            }.toByteArray()
        } catch (e: Exception) {
            bytes
        }
    }

    private fun scaleBitmap(bmp: android.graphics.Bitmap, max: Int): android.graphics.Bitmap {
        val w = bmp.width; val h = bmp.height
        if (w <= max && h <= max) return bmp
        val ratio = max.toFloat() / maxOf(w, h)
        return android.graphics.Bitmap.createScaledBitmap(bmp, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }
}