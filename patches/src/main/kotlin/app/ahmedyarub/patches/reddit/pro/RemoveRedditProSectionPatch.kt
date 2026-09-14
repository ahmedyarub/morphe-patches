package app.ahmedyarub.patches.reddit.pro

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_REDDIT
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch

@Suppress("unused")
val removeRedditProSectionPatch = bytecodePatch(
    name = "Remove Reddit Pro section",
    description = "Removes the Reddit Pro promos: the post creation and subreddit join " +
            "upsell sheets, and the Reddit Pro banner on the profile feed.",
    default = true
) {
    compatibleWith(COMPATIBILITY_REDDIT)

    execute {
        // Report every viewer as ineligible for a Reddit Pro promo.
        //
        // This is a suspend function, so its return value is either the resolved value or
        // COROUTINE_SUSPENDED. Returning the value immediately completes the call without
        // suspending, which is what the function already does on its own early-out paths.
        ProPromoEligibilityFingerprint.method.addInstructions(
            0,
            """
                sget-object v0, $PRO_PROMO_ELIGIBILITY_CLASS->NONE:$PRO_PROMO_ELIGIBILITY_CLASS
                return-object v0
            """
        )

        // Drop the profile feed upsell element before it reaches the feed.
        //
        // Returning null here is an existing code path: the factory already returns null for
        // an unrecognised promo type or a disabled feature flag, and callers handle it.
        ProfileFeedUpsellElementFingerprint.method.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return-object v0
            """
        )
    }
}
