package app.ahmedyarub.patches.claude.internal

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * The string resource "Internal settings" (res `0x7f141294` in 1.261120.19). Its name was stripped by
 * R8 resource shrinking (`APKTOOL_RENAMED_0x7f141294`), so it can only be matched by id, which changes
 * every release — hence a new value each time the version is bumped. This is the row the live Settings
 * screen shows; two unrelated hardcoded `"Internal Settings"` string literals belong to an alternate
 * settings surface the app does not render (`au.l4`) and to the internal screen's own header (`p3.x`).
 */
private const val INTERNAL_SETTINGS_STRING = 0x7f141294

/**
 * The "Internal settings" row of the Settings screen, a case of an R8-merged `invoke(Object, Object,
 * Object)` lambda (`a30.s` in 1.261120.19):
 *
 * ```
 * if (b1Var.f15744e != null && ((wz.a) b1Var.f15745f).a()) {   // non-null account AND Anthropic employee
 *     settingsRow(..., getString(R.string.internal_settings), onClick, ...)
 * }
 * ```
 *
 * `wz.a.a()` returns the `is_ant` preference that sign-in stores for `@anthropic.com` accounts. The
 * anchor is that boolean check — a no-argument `()Z` call, its `move-result` and the `if-eqz` that
 * skips the row — immediately followed (within the row's setup) by the Internal-settings string
 * resource, which pins the match to this row rather than to the dormant `au.l4` copy of the same shape.
 */
internal object InternalSettingsRowFingerprint : Fingerprint(
    name = "invoke",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;", "Ljava/lang/Object;"),
    filters = listOf(
        methodCall(parameters = listOf(), returnType = "Z", opcode = Opcode.INVOKE_VIRTUAL),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately()),
        opcode(Opcode.IF_EQZ, MatchAfterImmediately()),
        literal(INTERNAL_SETTINGS_STRING, location = MatchAfterWithin(20))
    )
)
