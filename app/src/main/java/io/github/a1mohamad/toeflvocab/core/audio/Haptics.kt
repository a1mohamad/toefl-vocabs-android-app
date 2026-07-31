package io.github.a1mohamad.toeflvocab.core.audio

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Thin wrapper over the platform haptic constants.
 *
 * `View.performHapticFeedback` rather than `Vibrator` directly, because it is
 * what respects the user's system-wide haptics setting and needs no permission.
 * The three call sites mirror the iOS build's success / warning / impact
 * feedback generators.
 */
object Haptics {

    fun answer(view: View, correct: Boolean, enabled: Boolean) {
        if (!enabled) return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (correct) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.REJECT
        } else {
            if (correct) HapticFeedbackConstants.VIRTUAL_KEY else HapticFeedbackConstants.LONG_PRESS
        }
        view.performHapticFeedback(constant)
    }

    fun tap(view: View, enabled: Boolean) {
        if (!enabled) return
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun milestone(view: View, enabled: Boolean) {
        if (!enabled) return
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.performHapticFeedback(constant)
    }
}
