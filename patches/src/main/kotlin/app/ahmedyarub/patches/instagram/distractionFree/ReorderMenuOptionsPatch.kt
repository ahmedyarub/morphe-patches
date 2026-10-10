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

private const val MENU_REORDER_CLASS = "Lapp/ahmedyarub/extension/instagram/MenuReorder;"

/**
 * The method that presents the overflow bottom sheet. It reads the LinkedList from the
 * menu model and hands it to the sheet adapter. Anchored on the analytics string
 * "overflow_bottom_sheet" which appears only in this presenter.
 */
private object OverflowSheetPresenterFingerprint : Fingerprint(
    strings = listOf("overflow_bottom_sheet"),
)

@Suppress("unused")
val reorderMenuOptionsPatch = bytecodePatch(
    name = "Reorder menu options",
    description = "Moves Interested and Not Interested to the top of the post menu.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(instagramExtensionPatch)

    execute {
        OverflowSheetPresenterFingerprint.method.apply {
            // Find the iget-object that reads the LinkedList field (A0H) from the model.
            // This is the list that's about to be passed to the sheet adapter for rendering.
            val linkedListRead = instructions.withIndex().firstOrNull { (_, instruction) ->
                instruction.opcode == Opcode.IGET_OBJECT &&
                    instruction.getReference<FieldReference>()?.type == "Ljava/util/LinkedList;"
            } ?: throw PatchException("No LinkedList field read found in the sheet presenter")

            val twoReg = getInstruction<TwoRegisterInstruction>(linkedListRead.index)
            val listRegister = twoReg.registerA

            // Inject the reorder call right after the LinkedList is read, before it's
            // passed to the sheet adapter.
            addInstructions(
                linkedListRead.index + 1,
                """
                invoke-static { v$listRegister }, $MENU_REORDER_CLASS->reorderMenuOptions(Ljava/util/List;)V
                """.trimIndent(),
            )
        }
    }
}
