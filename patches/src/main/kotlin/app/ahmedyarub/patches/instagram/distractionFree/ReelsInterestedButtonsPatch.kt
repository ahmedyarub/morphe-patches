package app.ahmedyarub.patches.instagram.distractionFree

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.morphe.library.instagram.patches.instagramExtensionPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstruction
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

private const val REELS_BUTTONS_CLASS = "Lapp/ahmedyarub/extension/instagram/ReelsButtons;"

private object AppShellOnCreateFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/app/InstagramAppShell;",
    name = "onCreate",
    returnType = "V",
    parameters = listOf(),
)

private object ActionSheetViewCreatedFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/igds/components/bottomsheet/BottomSheetFragment;",
    name = "onViewCreated",
)

/** Finds a reel's ⋯ button by its integer tag, then by its name. */
private object FindMoreButtonFingerprint : Fingerprint(
    returnType = "Landroid/view/View;",
    parameters = listOf("Landroid/view/View;"),
    strings = listOf("clips_ufi_more_button_component"),
)

/**
 * The Reels ⋯ menu's option list. It offers Interested together with Not interested only for some
 * reels; for the rest it offers Not interested alone.
 */
private object ReelsMenuOptionsFingerprint : Fingerprint(
    returnType = "Ljava/util/List;",
    strings = listOf("interested", "not_interested", "why_am_i_seeing_this"),
)

private object MoreTagIdFingerprint : Fingerprint(
    definingClass = REELS_BUTTONS_CLASS,
    name = "moreTagId",
)

@Suppress("unused")
val reelsInterestedButtonsPatch = bytecodePatch(
    name = "Reels interested buttons",
    description = "Adds Interested and Not interested buttons above the like button in Reels.",
    default = true,
) {
    compatibleWith(COMPATIBILITY_INSTAGRAM)
    dependsOn(instagramExtensionPatch)

    execute {
        FindMoreButtonFingerprint.method.apply {
            val lookup = instructions.indexOfFirst {
                it.getReference<MethodReference>()?.name == "findViewWithTag"
            }
            val tagId = instructions.take(lookup).lastOrNull { it is NarrowLiteralInstruction && it.opcode != Opcode.CONST_4 }
                ?: throw PatchException("The ⋯ button lookup has no integer tag")
            MoreTagIdFingerprint.method.addInstructions(
                0,
                "const v0, ${(tagId as NarrowLiteralInstruction).narrowLiteral}\nreturn v0",
            )
        }

        // Whenever the menu offers Not interested, it offers Interested too, so the button has an
        // option to pick. The first flag adds both; failing it, a second adds Not interested alone.
        ReelsMenuOptionsFingerprint.method.apply {
            val notInterested = instructions.indexOfFirst {
                it.getReference<StringReference>()?.string == "not_interested"
            }
            val bothFlag = instructions.take(notInterested).last { it.opcode == Opcode.IGET_BOOLEAN } as TwoRegisterInstruction
            val branch = instructions.drop(notInterested).first { it.opcode == Opcode.IF_EQZ }
            val target = (branch as OffsetInstruction).let { offset ->
                // The branch's target: the instruction the offset lands on.
                val targetAddress = branch.location.index.let { index ->
                    instructions.take(index).sumOf { it.codeUnits } + offset.codeOffset
                }
                var address = 0
                instructions.first { instruction -> (address == targetAddress).also { address += instruction.codeUnits } }
            }
            if (target.opcode != Opcode.IGET_BOOLEAN) throw PatchException("Unexpected Reels menu options")
            val notInterestedFlag = target.getReference<FieldReference>()!!
            val scratch = (getInstruction(notInterested) as OneRegisterInstruction).registerA
            addInstructions(
                notInterested,
                """
                    iget-boolean v$scratch, v${bothFlag.registerB}, $notInterestedFlag
                    or-int/2addr v${bothFlag.registerA}, v$scratch
                """,
            )
        }

        // The ⋯ menu's options, for their labels.
        OverflowSheetPresenterFingerprint.method.apply {
            val linkedListRead = instructions.withIndex().firstOrNull { (_, instruction) ->
                instruction.opcode == Opcode.IGET_OBJECT &&
                    instruction.getReference<FieldReference>()?.type == "Ljava/util/LinkedList;"
            } ?: throw PatchException("No LinkedList field read found in the sheet presenter")
            val listRegister = getInstruction<TwoRegisterInstruction>(linkedListRead.index).registerA
            addInstruction(
                linkedListRead.index + 1,
                "invoke-static { v$listRegister }, $REELS_BUTTONS_CLASS->onMenuOptions(Ljava/util/List;)V",
            )
        }

        ActionSheetViewCreatedFingerprint.method.apply {
            val returnIndex = instructions.indexOfLast { it.opcode == Opcode.RETURN_VOID }
            if (returnIndex < 0) throw PatchException("No return-void in onViewCreated")
            addInstruction(returnIndex, "invoke-static { p0 }, $REELS_BUTTONS_CLASS->onActionSheetCreated(Ljava/lang/Object;)V")
        }

        AppShellOnCreateFingerprint.method.addInstruction(
            0,
            "invoke-static/range { p0 .. p0 }, $REELS_BUTTONS_CLASS->install(Landroid/app/Application;)V",
        )
    }
}
