/*
 * Adapted from piko <https://github.com/crimera/piko>, GPLv3.
 * Original credits in piko: MyInsta, InstaPro.
 *
 * Simplified: piko gates each injection on a preference read
 * (Pref.disableScreenshotDetection()Z) backed by its settings UI. This port drops the
 * settings layer and substitutes a constant true, so detection is always disabled.
 */

package app.ahmedyarub.patches.instagram.privacy

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.indexOfFirstInstruction
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode

internal object ScreenshotDetectorFingerprint : Fingerprint(
    strings = listOf("ig_android_story_screenshot_directory", "screenshot_detector"),
)

internal object AddFlagsToWindowFingerprint : Fingerprint(
    strings = listOf("Inconsistency in window FLAG_SECURE state detected! window state: "),
)

internal object DirectScreenshotCaptureTriggerFingerprint : Fingerprint(
    strings = listOf("igd_screenshot_capture"),
)

internal object ChatRecyclerViewRelatedFingerprint : Fingerprint(
    strings = listOf("Removed holder should be bound and it should come here only in pre-layout. Holder: "),
)

/**
 * Replaces piko's two instruction preference read with a single constant, so no extension
 * class and no settings screen are needed. const/4 carries a 4 bit register, so anything
 * higher falls back to const/16.
 */
private fun alwaysTrue(register: Int) = if (register <= MAX_CONST_4_REGISTER) {
    "const/4 v$register, 0x1"
} else {
    "const/16 v$register, 0x1"
}

private const val MAX_CONST_4_REGISTER = 15

@Suppress("unused")
val disableScreenshotDetectionPatch = bytecodePatch(
    name = "Disable screenshot detection",
    description = "Disables screenshot detection in direct messages and stories.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)

    execute {
        // Skip starting the screenshot observer.
        ScreenshotDetectorFingerprint.apply {
            val stringIndex = stringMatches[0].index

            method.apply {
                val observerStartIndex = instructions.last {
                    it.location.index < stringIndex && it.opcode == Opcode.INVOKE_VIRTUAL
                }.location.index

                val nextInstruction = getInstruction(observerStartIndex + 1)
                val register = nextInstruction.registersUsed[0]

                addInstructionsWithLabels(
                    observerStartIndex,
                    """
                        ${alwaysTrue(register)}
                        if-nez v$register, :skip
                    """,
                    ExternalLabel("skip", nextInstruction),
                )
            }
        }

        // Do not re-apply FLAG_SECURE to the window.
        AddFlagsToWindowFingerprint.method.apply {
            val lastFlagIndex = instructions.indexOfLast { it.opcode == Opcode.CONST_16 }

            addInstructionsWithLabels(
                lastFlagIndex + 1,
                """
                    ${alwaysTrue(1)}
                    if-eqz v1, :skip
                    return-void
                """,
                ExternalLabel("skip", getInstruction(lastFlagIndex + 1)),
            )
        }

        // Do not fire the direct message screenshot capture event.
        DirectScreenshotCaptureTriggerFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                    ${alwaysTrue(0)}
                    if-eqz v0, :skip
                    return-void
                """,
                ExternalLabel("skip", getInstruction(0)),
            )
        }

        // Skip the chat view's screenshot related flag handling.
        ChatRecyclerViewRelatedFingerprint.method.apply {
            val firstAndIntIndex = indexOfFirstInstruction(Opcode.AND_INT_2ADDR)
            val flagIntIndex = firstAndIntIndex - 2
            val register = getInstruction(flagIntIndex).registersUsed[0]
            val resumeIndex = indexOfFirstInstruction(firstAndIntIndex, Opcode.IGET_OBJECT)

            addInstructionsWithLabels(
                flagIntIndex,
                """
                    ${alwaysTrue(register)}
                    if-nez v$register, :skip
                """,
                ExternalLabel("skip", getInstruction(resumeIndex)),
            )
        }
    }
}
