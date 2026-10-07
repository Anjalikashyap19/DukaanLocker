package com.iadv.dukaanlocker.ui.screens

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iadv.dukaanlocker.ui.theme.AppColors

/** Failed sign-in attempts allowed before the lockout starts. */
private const val LOGIN_MAX_ATTEMPTS = 5

/** Lockout length once LOGIN_MAX_ATTEMPTS failed attempts are used up. */
private const val LOGIN_LOCKOUT_MS = 2 * 60 * 1000L

private const val LOGIN_RATE_PREFS = "login_rate_limit"

/**
 * Client-side throttle for the sign-in forms: 5 failed attempts start a
 * 2-minute lockout during which the access button stays disabled. Attempt
 * budgets are tracked per sign-in type so the owner and manager forms lock
 * out independently, and they are persisted so leaving the screen (or
 * restarting the app) does not reset them.
 */
object LoginRateLimiter {
    const val OWNER = "owner"
    const val MANAGER = "manager"

    private fun prefs(context: Context) =
        context.getSharedPreferences(LOGIN_RATE_PREFS, Context.MODE_PRIVATE)

    private fun attemptsKey(type: String) = "attempts_$type"
    private fun lockoutKey(type: String) = "lockout_until_$type"

    /** Milliseconds left in the lockout for [type] (0 when not locked). */
    fun lockoutRemainingMs(context: Context, type: String): Long {
        val remaining = prefs(context).getLong(lockoutKey(type), 0L) - System.currentTimeMillis()
        return remaining.coerceAtLeast(0L)
    }

    /** Registers a failed attempt; returns the remaining lockout (0 when not locked). */
    fun recordFailure(context: Context, type: String): Long {
        val p = prefs(context)
        val active = p.getLong(lockoutKey(type), 0L) - System.currentTimeMillis()
        if (active > 0) return active

        val next = p.getInt(attemptsKey(type), 0) + 1
        if (next >= LOGIN_MAX_ATTEMPTS) {
            p.edit()
                .putInt(attemptsKey(type), 0)
                .putLong(lockoutKey(type), System.currentTimeMillis() + LOGIN_LOCKOUT_MS)
                .apply()
            return LOGIN_LOCKOUT_MS
        }
        p.edit().putInt(attemptsKey(type), next).apply()
        return 0L
    }

    /** Clears the failed-attempt budget after a successful sign-in. */
    fun recordSuccess(context: Context, type: String) {
        prefs(context).edit().putInt(attemptsKey(type), 0).apply()
    }
}

/** Renders a lockout duration as m:ss. */
fun formatLoginLockout(ms: Long): String {
    val totalSeconds = ((ms + 999) / 1000).toInt()
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/** Shown above the access button while a sign-in lockout is running. */
@Composable
fun LoginLockoutBanner(colors: AppColors, remainingMs: Long) {
    if (remainingMs <= 0L) return
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.error.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, colors.error.copy(alpha = 0.35f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(Icons.Default.AccessTime, contentDescription = null, tint = colors.error, modifier = Modifier.size(18.dp))
            Column {
                Text("Too many failed attempts.", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = colors.error)
                Text(
                    "Try again in ${formatLoginLockout(remainingMs)}",
                    fontSize = 12.sp,
                    color = colors.textPrimary
                )
            }
        }
    }
}
