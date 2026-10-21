/*
 * Filters the story tray where the reel store takes its items in, rather than only where the
 * tray's JSON is parsed. Every tray item reaches the store as a ReelResponseItemIntf, whichever
 * response or model it came from (the JSON parser, Pando models, later additions), so the filter
 * cannot be bypassed by Instagram delivering the tray another way.
 */
package app.ahmedyarub.patches.instagram.distractionFree

import app.ahmedyarub.patches.shared.loadedStrings
import app.ahmedyarub.patches.shared.stringPoolsPatch
import app.morphe.library.instagram.patches.instagramExtensionPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.AccessFlags

private const val STORY_TRAY_CLASS = "Lapp/ahmedyarub/extension/instagram/StoryTray;"

/** A reel store method, named by the trace label it logs, which may be pooled. */
internal class ReelStoreFingerprint(label: String) : Fingerprint(
    custom = { method, _ -> label in method.loadedStrings },
)

/** The store populating the tray from a tray response. */
private val ReelStorePopulateTrayFingerprint = ReelStoreFingerprint("ReelStore.populateReelsFromReelTrayResponse")

/** The store adding new items to the tray. */
private val ReelStoreAddTrayItemsFingerprint = ReelStoreFingerprint("ReelStore.maybeAddNewTrayReelResponseItemIntfs")

/** The store fetching the midcards Instagram injects into the tray on the client. */
internal val ReelStoreFetchMidcardsFingerprint = ReelStoreFingerprint("ReelStore.fetchClientInjectedMidcards")

val filterStoriesStoreHook =
    bytecodePatch(
        description = "Filters the tray items the reel store takes in.",
    ) {
        dependsOn(instagramExtensionPatch, stringPoolsPatch)

        execute {
            listOf(ReelStorePopulateTrayFingerprint, ReelStoreAddTrayItemsFingerprint).forEach { fingerprint ->
                fingerprint.method.apply {
                    // Each List parameter holds tray items; it is replaced with the filtered list
                    // before the method reads it.
                    var register = if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
                    val listRegisters = mutableListOf<Int>()
                    parameterTypes.forEach { type ->
                        if (type.toString() == "Ljava/util/List;") listRegisters += register
                        register += if (type.toString() == "J" || type.toString() == "D") 2 else 1
                    }
                    if (listRegisters.isEmpty()) throw PatchException("$definingClass->$name takes no tray item list")

                    addInstructions(
                        0,
                        listRegisters.joinToString("\n") { p ->
                            """
                            invoke-static/range { p$p .. p$p }, $STORY_TRAY_CLASS->filterTrayItems(Ljava/util/List;)Ljava/util/List;
                            move-result-object p$p
                            """.trimIndent()
                        },
                    )
                }
            }
        }
    }
