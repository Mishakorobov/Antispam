package ru.antispam.app.ui.antifraud

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.antispam.app.ui.theme.AppleRed
import ru.antispam.app.util.PhoneNormalizer

class AntiFraudAlertActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CALLER_NUMBER = "extra_caller_number"
        const val EXTRA_MESSAGE_SNIPPET = "extra_message_snippet"
        const val EXTRA_SENDER = "extra_sender"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ensure Activity shows above lock screen and ongoing call screen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val caller = intent.getStringExtra(EXTRA_CALLER_NUMBER) ?: "Неизвестный номер"
        val snippet = intent.getStringExtra(EXTRA_MESSAGE_SNIPPET) ?: ""
        val sender = intent.getStringExtra(EXTRA_SENDER)

        setContent {
            AntiFraudAlertScreen(
                callerNumber = caller,
                messageSnippet = snippet,
                sender = sender,
                onEndCall = {
                    endCurrentCall()
                    finish()
                },
                onDismiss = {
                    finish()
                }
            )
        }
    }

    private fun endCurrentCall() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val tm = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            try {
                @Suppress("MissingPermission")
                tm?.endCall()
            } catch (e: Exception) {
                // If permission missing, simply exit
            }
        }
    }
}

@Composable
fun AntiFraudAlertScreen(
    callerNumber: String,
    messageSnippet: String,
    sender: String?,
    onEndCall: () -> Unit,
    onDismiss: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val formattedCaller = PhoneNormalizer.formatPretty(callerNumber)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF140202) // Deep emergency crimson black
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF3B0000),
                            Color(0xFF120000),
                            Color(0xFF000000)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Pulsing Warning Icon
                Box(
                    modifier = Modifier
                        .scale(pulseScale)
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(AppleRed.copy(alpha = 0.2f))
                        .border(3.dp, AppleRed, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = "Fraud Warning",
                        tint = AppleRed,
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Urgent Headline
                Text(
                    text = "🛑 СТОП! ВАМ ЗВОНЯТ МОШЕННИКИ!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    lineHeight = 30.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Explanation
                Text(
                    text = "Во время разговора с неизвестным абонентом поступил секретный код подтверждения! Мошенники используют социальную инженерию, чтобы выманить его у вас.",
                    fontSize = 15.sp,
                    color = Color(0xFFFFD2D2),
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Threat Details Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color(0xFF260505))
                        .border(1.5.dp, AppleRed.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        .padding(18.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "ЗВОНЯЩИЙ:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AppleRed
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = formattedCaller,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        if (!sender.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "ОТПРАВИТЕЛЬ КОДА:",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFB300)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = sender,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                        }

                        if (messageSnippet.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "ПОЛУЧЕННЫЙ ТЕКСТ СМС:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "\"$messageSnippet\"",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFFFFEB3B), // Highlight code text in yellow
                                lineHeight = 19.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppleRed.copy(alpha = 0.25f))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "⚠️ НИКОМУ НЕ НАЗЫВАЙТЕ ЭТОТ КОД! Даже если собеседник представляется сотрудником Госуслуг, банка, оператора связи или полиции!",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                lineHeight = 17.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(30.dp))

                // End Call Primary Button
                Button(
                    onClick = onEndCall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppleRed),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "Положить трубку",
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "ПОЛОЖИТЬ ТРУБКУ",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dismiss Secondary Button
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Понятно",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Я понял, закрыть окно",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
