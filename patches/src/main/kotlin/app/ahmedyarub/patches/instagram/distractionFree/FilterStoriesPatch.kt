/*
 * Adapted from piko <https://github.com/crimera/piko>, GPLv3.
 *
 * Simplified: piko's FilterStory decodes each reel item through its entity layer so it can
 * also filter on whether the poster is verified and on how many items a story has. Both of
 * those need the obfuscated model classes resolved at patch time, which is the part that
 * breaks on newer app versions. Only the reel type filtering is ported, using the story tray
 * filter already available in the Instagram patch library, so no entity decoding is involved.
 */

package app.ahmedyarub.patches.instagram.distractionFree

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.library.instagram.patches.blockUrl
import app.morphe.library.instagram.patches.filterStories
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.patch.booleanOption
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.returnEarly

private object OnlyOrganicStoriesFingerprint : Fingerprint(
    definingClass = "Lapp/ahmedyarub/extension/instagram/StoryTray;",
    name = "onlyOrganicStories",
)

@Suppress("unused")
val filterStoriesPatch = bytecodePatch(
    name = "Filter stories",
    description = "Hides categories of stories from the story tray.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    dependsOn(filterStoriesTrayHook, filterStoriesStoreHook, blockUrlHook)

    val onlyOrganic by booleanOption(
        key = "onlyOrganicStories",
        default = true,
        title = "Only organic stories",
        description = "Shows only stories people posted: their own stories, close friends, shared and " +
            "collab stories, lives and subscriber stories. Hides every suggestion, prompt, music and " +
            "ad card Instagram adds to the tray or injects while watching, whatever the options below say.",
    )

    val hideAds by booleanOption(
        key = "hideAdsStories",
        default = true,
        title = "Hide ad stories",
        description = "Hides advertisements from the story tray.",
    )

    val hideSuggested by booleanOption(
        key = "hideSuggestedStories",
        default = true,
        title = "Hide suggested stories",
        description = "Hides suggested users and creators from the story tray.",
    )

    // On by default: Hide suggested content used to hide highlights too, with this default, and
    // both patches are on by default.
    val hideHighlights by booleanOption(
        key = "hideHighlightStories",
        default = true,
        title = "Hide highlights",
        description = "Hides resurfaced highlights, which carry no expiry, from the story tray.",
    )

    val hideYourTurn by booleanOption(
        key = "hideYourTurnStories",
        default = true,
        title = "Hide \"Your Turn\" stories",
        description = "Hides \"Add Yours\" / \"Your Turn\" stories from the story tray.",
    )

    execute {
        if (onlyOrganic == true) {
            OnlyOrganicStoriesFingerprint.method.returnEarly(true)
            // Ads and suggestion cards inserted between stories while watching are fetched
            // separately from the tray.
            blockUrl("/feed/injected_reels_media/")
            // Midcards the app itself fetches and injects into the tray.
            ReelStoreFetchMidcardsFingerprint.method.returnEarly()
        }

        if (hideAds == true) filterStories("ads_reel")

        if (hideSuggested == true) filterStories(
            "suggested_user_reel",
            "suggested_user",
            "suggested_creator_reel",
        )

        if (hideHighlights == true) filterStories("highlight_rewind_reel")

        if (hideYourTurn == true) filterStories("prompt_sticker_reel")
    }
}
