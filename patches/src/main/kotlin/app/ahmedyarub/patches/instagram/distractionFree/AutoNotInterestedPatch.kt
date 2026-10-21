package app.ahmedyarub.patches.instagram.distractionFree

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.library.instagram.patches.instagramExtensionPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference

private const val AUTO_NI_CLASS = "Lapp/ahmedyarub/extension/instagram/AutoNotInterested;"
private const val BOTTOM_SHEET_FRAGMENT =
    "Lcom/instagram/igds/components/bottomsheet/BottomSheetFragment;"

private object BottomSheetFragmentFingerprint : Fingerprint(
    definingClass = BOTTOM_SHEET_FRAGMENT,
    name = "onViewCreated",
)

@Suppress("unused")
val autoNotInterestedPatch = bytecodePatch(
    name = "Auto not interested",
    description = "Skips the reason popup and auto-submits when tapping Not Interested.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(instagramExtensionPatch)

    execute {
        // The reasons popup is titled with the label of the menu's "not_interested" option, so
        // that label is recorded from the overflow menu's options as the menu is presented.
        OverflowSheetPresenterFingerprint.method.apply {
            val linkedListRead = instructions.withIndex().firstOrNull { (_, instruction) ->
                instruction.opcode == Opcode.IGET_OBJECT &&
                    instruction.getReference<FieldReference>()?.type == "Ljava/util/LinkedList;"
            } ?: throw PatchException("No LinkedList field read found in the sheet presenter")

            val listRegister = getInstruction<TwoRegisterInstruction>(linkedListRead.index).registerA
            addInstructions(
                linkedListRead.index + 1,
                """
                invoke-static { v$listRegister }, $AUTO_NI_CLASS->onMenuOptions(Ljava/util/List;)V
                """.trimIndent(),
            )
        }

        BottomSheetFragmentFingerprint.method.apply {
            val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
            if (returnIndex < 0) throw PatchException("No return-void in onViewCreated")
            addInstructions(
                returnIndex,
                """
                invoke-static { p0 }, $AUTO_NI_CLASS->onActionSheetCreated(Ljava/lang/Object;)V
                """.trimIndent(),
            )
        }
    }
}
