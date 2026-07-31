package io.github.a1mohamad.toeflvocab.core.localization

import android.app.Activity
import android.view.View

/**
 * Keeps the platform's idea of the writing direction in step with the app's
 * language setting.
 *
 * `LocalLayoutDirection` in Compose only describes the Compose tree. The View
 * hierarchy underneath — the decor view, and anything drawn in a platform
 * window such as a dropdown menu's anchor measurement — carries its own
 * `layoutDirection`, and it is resolved from the *device* locale, not from the
 * language the user picked in Settings.
 *
 * Leaving the two disagreeing is the same class of bug the iOS build hit with
 * `UISemanticContentAttribute`: the container mirrors while its contents do not,
 * and text ends up laid out backwards. Writing the direction onto the decor view
 * on every change is what makes the two layers agree.
 *
 * This is only needed because language is an in-app setting. An app that took
 * the device locale would get all of this for free from `supportsRtl`.
 */
object LayoutDirectionBridge {

    fun apply(activity: Activity, isRightToLeft: Boolean) {
        val direction = if (isRightToLeft) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR
        val decor = activity.window?.decorView ?: return
        if (decor.layoutDirection == direction) return
        decor.layoutDirection = direction
        // The attribute alone marks the hierarchy dirty; this is what makes
        // already-visible views redraw in the new direction instead of waiting
        // for the next unrelated layout pass.
        decor.requestLayout()
    }
}
