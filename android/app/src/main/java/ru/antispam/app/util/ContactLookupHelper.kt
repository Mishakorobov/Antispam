package ru.antispam.app.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import android.util.LruCache

object ContactLookupHelper {

    // Fast in-memory LRU cache to achieve < 0.2ms lookups for frequently calling contacts
    private val contactCache = LruCache<Long, Boolean>(1000)

    fun isContact(contentResolver: ContentResolver, rawNumber: String, normalizedNumber: Long?): Boolean {
        if (normalizedNumber != null) {
            val cached = contactCache.get(normalizedNumber)
            if (cached != null) return cached
        }

        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(rawNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        val found = try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        } catch (_: SecurityException) {
            // Permission READ_CONTACTS not granted
            false
        } catch (_: Exception) {
            false
        }

        if (normalizedNumber != null && found) {
            contactCache.put(normalizedNumber, true)
        }

        return found
    }

    fun getContactDisplayName(contentResolver: ContentResolver, rawNumber: String, normalizedNumber: Long?): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(rawNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)

        return try {
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun invalidateCache() {
        contactCache.evictAll()
    }
}
