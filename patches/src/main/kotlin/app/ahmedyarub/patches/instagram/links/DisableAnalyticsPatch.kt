/*
 * Adapted from piko <https://github.com/crimera/piko>, GPLv3.
 *
 * Simplified: piko splits this into interceptUriPatch plus a settings-only DisableAnalytics
 * patch, and its Links.interceptUri multiplexes a dozen features behind preferences. Here the
 * injection and the analytics rules are one always-on patch.
 */

package app.ahmedyarub.patches.instagram.links

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.library.instagram.patches.instagramExtensionPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

/**
 * Every request the app makes passes through here, and the class name is not obfuscated.
 */
internal object TigonServiceLayerStartRequestFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/tigon/TigonServiceLayer;",
    name = "startRequest",
)

@Suppress("unused")
val disableAnalyticsPatch = bytecodePatch(
    name = "Disable analytics",
    description = "Blocks analytics requests sent to Instagram and Facebook servers.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    dependsOn(instagramExtensionPatch)

    execute {
        TigonServiceLayerStartRequestFingerprint.method.apply {
            val firstIfEqzIndex = indexOfFirstInstruction(Opcode.IF_EQZ)

            val uriInstruction = instructions.last {
                it.opcode == Opcode.IGET_OBJECT && it.location.index < firstIfEqzIndex
            }
            val uriRegister = uriInstruction.registersUsed[0]

            addInstructions(
                uriInstruction.location.index + 1,
                "invoke-static/range { v$uriRegister .. v$uriRegister }, " +
                    "$LINKS_CLASS->interceptUri(Ljava/net/URI;)V",
            )
        }
    }
}
