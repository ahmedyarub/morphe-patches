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
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

private const val MENU_REORDER_CLASS = "Lapp/ahmedyarub/extension/instagram/MenuReorder;"

/**
 * The method that assembles the post menu options into an ArrayList.
 * Anchored on a string constant that only appears in this builder.
 */
private object MenuOptionsBuilderFingerprint : Fingerprint(
    strings = listOf("TEXT_POST_APP_INACTIVE"),
)

@Suppress("unused")
val reorderMenuOptionsPatch = bytecodePatch(
    name = "Reorder menu options",
    description = "Moves Interested and Not Interested to the top of the post menu.",
    default = true
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(instagramExtensionPatch)

    execute {
        MenuOptionsBuilderFingerprint.method.apply {
            val arrayListInstructions = instructions.filter {
                it.opcode == Opcode.NEW_INSTANCE &&
                    it.getReference<TypeReference>()?.type == "Ljava/util/ArrayList;"
            }

            if (arrayListInstructions.isEmpty()) {
                throw PatchException("No ArrayList found in the menu options builder")
            }

            val arrayListInstruction = arrayListInstructions.first()
            val arrayListRegister = arrayListInstruction.registersUsed[0]

            val returnIndices = instructions.mapIndexedNotNull { index, instruction ->
                if (instruction.opcode == Opcode.RETURN_VOID ||
                    instruction.opcode == Opcode.RETURN_OBJECT
                ) index else null
            }

            if (returnIndices.isEmpty()) {
                throw PatchException("No return instruction found in the menu options builder")
            }

            var offset = 0
            for (returnIndex in returnIndices) {
                addInstructions(
                    returnIndex + offset,
                    """
                    invoke-static { v$arrayListRegister }, $MENU_REORDER_CLASS->reorderMenuOptions(Ljava/util/List;)V
                    """.trimIndent(),
                )
                offset++
            }
        }
    }
}
