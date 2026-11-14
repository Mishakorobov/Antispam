package ru.antispam.app.antifraud

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/**
 * AntiFraudSmsReceiver:
 * BroadcastReceiver intercepting incoming SMS messages in real-time.
 * Evaluates whether an active phone call with an unknown number is underway and checks
 * for sensitive verification/2FA codes to prevent social engineering theft.
 */
class AntiFraudSmsReceiver : BroadcastReceiver() {

    private val TAG = "AntiFraudSmsReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Fast-path: Only process if an active phone conversation with an unknown caller is underway
        if (!AntiFraudCallSmsMonitor.isCallWithUnknownActive.value) {
            return
        }

        try {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            if (messages.isNullOrEmpty()) return

            val fullBody = buildString {
                for (sms in messages) {
                    append(sms.displayMessageBody ?: "")
                }
            }
            val sender = messages[0].displayOriginatingAddress

            Log.d(TAG, "Incoming SMS during active unknown call from $sender: length=${fullBody.length}")
            AntiFraudCallSmsMonitor.processIncomingSms(context, fullBody, sender)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing incoming SMS for Anti-Fraud: ${e.message}", e)
        }
    }
}
