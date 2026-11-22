package app.ahmedyarub.patches.claude.ant

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.InstructionLocation.MatchAfterImmediately
import app.morphe.patcher.InstructionLocation.MatchAfterWithin
import app.morphe.patcher.literal
import app.morphe.patcher.methodCall
import app.morphe.patcher.opcode
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * String resources whose names R8 resource shrinking stripped (`APKTOOL_RENAMED_0x…`), so they can only be
 * matched by id. Ids change every release: update them each time the Claude version is bumped.
 */
private object Resources {
    /** "Untitled": the chat title fallback, read just before the chat menu's employee check (1.261120.19). */
    const val UNTITLED = 0x7f141158
}

/**
 * The chat screen's top bar (`bk.w8.invoke(Object, Object, Object, Object)` in 1.261120.19) builds the ⋯ menu's
 * actions. It reads the employee check once:
 *
 * ```
 * String title = ... ?: getString(R.string.untitled);
 * boolean isAnt = ((wz.a) (kr.a) inject(kr.a.class)).a();
 * ...
 * copyConversationId = isAnt ? new m20.d(..., new ol.g0(...)) : null;   // "Copy conversation ID"
 * topBar(..., outline, isAnt, ...);   // isAnt: the outline's "(Ant only)" label, copyConversationId, ...);
 * ```
 *
 * The anchor is the "Untitled" fallback followed by the check: the injected object cast to the interface and then
 * to the implementation (`check-cast`, `check-cast`), the no-argument `()Z` call and its `move-result`.
 */
internal val chatMenuEmployeeCheckFingerprint = Fingerprint(
    name = "invoke",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Object;",
    parameters = listOf("Ljava/lang/Object;", "Ljava/lang/Object;", "Ljava/lang/Object;", "Ljava/lang/Object;"),
    filters = listOf(
        literal(Resources.UNTITLED),
        opcode(Opcode.CHECK_CAST, MatchAfterWithin(12)),
        opcode(Opcode.CHECK_CAST, MatchAfterImmediately()),
        methodCall(parameters = listOf(), returnType = "Z", opcode = Opcode.INVOKE_VIRTUAL, location = MatchAfterImmediately()),
        opcode(Opcode.MOVE_RESULT, MatchAfterImmediately())
    )
)
