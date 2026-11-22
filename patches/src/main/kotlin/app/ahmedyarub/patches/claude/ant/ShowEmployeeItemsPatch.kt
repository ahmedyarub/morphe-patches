package app.ahmedyarub.patches.claude.ant

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_CLAUDE
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

@Suppress("unused")
val showEmployeeItemsPatch = bytecodePatch(
    name = "Show employee-only items",
    description = "Shows menu items the app keeps for Anthropic accounts that only work on your own data: " +
            "Copy conversation ID in a chat's menu. Items that send anything to Anthropic (feedback, problem " +
            "reports) stay hidden, and the app still treats you as a regular account everywhere else.",
    default = false
) {
    compatibleWith(COMPATIBILITY_CLAUDE)

    execute {
        // Chat menu: the employee check's result is kept (it also labels the outline "(Ant only)"); only the
        // branch that leaves "Copy conversation ID" out is removed, i.e. the first `if-eqz` on that register.
        chatMenuEmployeeCheckFingerprint.method.apply {
            val moveResultIndex = chatMenuEmployeeCheckFingerprint.instructionMatches.last().index
            val isAnt = getInstruction<OneRegisterInstruction>(moveResultIndex).registerA
            val branchIndex = instructions.withIndex().drop(moveResultIndex + 1).firstOrNull { (_, instruction) ->
                instruction.opcode == Opcode.IF_EQZ && (instruction as OneRegisterInstruction).registerA == isAnt
            }?.index ?: throw PatchException("No branch on the chat menu's employee check")
            replaceInstruction(branchIndex, "nop")
        }
    }
}
