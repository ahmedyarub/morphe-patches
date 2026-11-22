package app.ahmedyarub.patches.claude.internal

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_CLAUDE
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.bytecodePatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val MAX_CONST_4_REGISTER = 15

@Suppress("unused")
val showInternalSettingsPatch = bytecodePatch(
    name = "Show internal settings",
    description = "Forces the Settings screen's \"Internal settings\" gate (an Anthropic-employee " +
            "check) open: GrowthBook feature flag overrides, endpoint selection, network and usage " +
            "simulation. The entry may still depend on account state the server only sends to " +
            "Anthropic accounts.",
    default = false
) {
    compatibleWith(COMPATIBILITY_CLAUDE)

    execute {
        // The row's render gate is `account != null && wz.a.a()`: a non-null internal-org object AND the
        // is_ant (Anthropic employee) preference. Both are employee-only, so both are forced: the
        // employee check's result is set to 1, and the null-check branch before it is removed, since the
        // row's body does not read that org object (its onClick is passed in and the internal screen is
        // self-contained). The stored is_ant value, which the app reports to analytics, Sentry and
        // GrowthBook attributes (read straight from the preference, not through wz.a), is untouched.
        InternalSettingsRowFingerprint.method.apply {
            // `move-result` of wz.a.a() -> 1.
            val moveResultIndex = InternalSettingsRowFingerprint.instructionMatches[1].index
            val register = getInstruction<OneRegisterInstruction>(moveResultIndex).registerA
            val setTrue = if (register <= MAX_CONST_4_REGISTER) {
                "const/4 v$register, 0x1"
            } else {
                "const/16 v$register, 0x1"
            }
            replaceInstruction(moveResultIndex, setTrue)

            // The null-check `if-eqz`, just before the wz.a.a() call, -> nop.
            val callIndex = InternalSettingsRowFingerprint.instructionMatches[0].index
            val nullCheckIndex = (callIndex - 1 downTo 0).first {
                getInstruction(it).opcode == Opcode.IF_EQZ
            }
            replaceInstruction(nullCheckIndex, "nop")
        }
    }
}
