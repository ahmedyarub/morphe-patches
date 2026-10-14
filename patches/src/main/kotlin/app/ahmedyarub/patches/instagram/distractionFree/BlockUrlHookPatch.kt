/*
 * Repo reimplementation of the instagram-morphe-patches-library request-blocking hook.
 *
 * The library's blockUrlBasePatch looks for the request URI read in TigonServiceLayer.startRequest
 * by name. From 451 startRequest only wraps startRequestInternal in a trace section, so the read
 * is no longer there and the hook throws "Could not find instruction index", taking Hide suggested
 * content down with it.
 *
 * This version hooks the method Disable analytics hooks, the one that checks the URI has a host,
 * and hands the URI to the library's checkAndBlockUrl as before. blockUrl(...), which fills the
 * blocked-URL set checkAndBlockUrl reads, is still the library helper and is unchanged.
 */
package app.ahmedyarub.patches.instagram.distractionFree

import app.ahmedyarub.patches.instagram.links.TigonServiceLayerStartRequestFingerprint
import app.morphe.library.instagram.patches.instagramExtensionPatch
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val BLOCK_URL_CLASS = "Lapp/morphe/library/extension/instagram/patches/BlockUrlRequestPatch;"

val blockUrlHook =
    bytecodePatch(
        description = "Hooks Instagram's request layer to drop requests to blocked URLs.",
    ) {
        dependsOn(instagramExtensionPatch)

        execute {
            TigonServiceLayerStartRequestFingerprint.method.apply {
                val uriIndex = instructions.indexOfFirst { instruction ->
                    instruction.opcode == Opcode.IGET_OBJECT &&
                        instruction.getReference<FieldReference>()?.type == "Ljava/net/URI;"
                }
                if (uriIndex < 0) throw PatchException("No URI field read in the request method")

                val uriRegister = instructions.elementAt(uriIndex).registersUsed[0]

                addInstructions(
                    uriIndex + 1,
                    "invoke-static/range { v$uriRegister .. v$uriRegister }, " +
                        "$BLOCK_URL_CLASS->checkAndBlockUrl(Ljava/net/URI;)V",
                )
            }
        }
    }
