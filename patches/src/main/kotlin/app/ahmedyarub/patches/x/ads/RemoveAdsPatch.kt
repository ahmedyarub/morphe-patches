package app.ahmedyarub.patches.x.ads

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_X
import app.ahmedyarub.patches.x.shared.EXTENSION_PACKAGE
import app.ahmedyarub.patches.x.shared.xExtensionPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.addInstructionsAtControlFlowLabel
import app.morphe.util.returnEarly
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val ADS_CLASS = "$EXTENSION_PACKAGE/Ads;"

/** The metadata a promoted timeline item carries. */
private object TimelinePromotedMetadataToStringFingerprint : Fingerprint(
    name = "toString",
    strings = listOf("TimelinePromotedMetadata(impressionId="),
)

/**
 * Turns a cached timeline row into a timeline item. Timelines are shown from the database, and
 * both places that read them skip a row this returns null for.
 */
private object CachedTimelineItemFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("L", "Ljava/util/LinkedHashMap;", "Ljava/util/LinkedHashMap;", "L"),
    custom = { method, _ ->
        method.returnType.startsWith("Lcom/x/models/timelines/items/") &&
            method.parameterTypes.first().startsWith("Lcom/x/database/")
    },
)

/**
 * Stores a timeline item the server sent into the database the timelines are shown from. Posts,
 * accounts, trends and ads all come through here, module children too, and the promoted metadata
 * of accounts and trends is not stored, so a promoted item has to be dropped before it is.
 */
private object StoreTimelineItemFingerprint : Fingerprint(
    returnType = "V",
    custom = { method, _ ->
        val parameters = method.parameterTypes.map { it.toString() }
        parameters.size == 9 && parameters.first().startsWith("Lcom/x/models/timelines/items/") &&
            parameters.takeLast(6).all { it == "Ljava/util/ArrayList;" }
    },
)

private object PromotedMetadataClassExtensionFingerprint : Fingerprint(
    definingClass = ADS_CLASS,
    name = "promotedMetadataClass",
)

@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove Ads",
    description = "Removes promoted posts, accounts and trends from timelines.",
) {
    compatibleWith(COMPATIBILITY_X)
    dependsOn(xExtensionPatch)

    execute {
        PromotedMetadataClassExtensionFingerprint.method.returnEarly(
            TimelinePromotedMetadataToStringFingerprint.classDef.type.removePrefix("L").removeSuffix(";").replace('/', '.'),
        )

        StoreTimelineItemFingerprint.method.apply {
            addInstructionsWithLabels(
                0,
                """
                invoke-static/range { p1 .. p1 }, $ADS_CLASS->isPromoted(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :store
                return-void
                """,
                ExternalLabel("store", getInstruction(0)),
            )
        }

        // Rows already cached before the app was patched are dropped as they are read back.
        CachedTimelineItemFingerprint.method.apply {
            // Every return, last first so the indices still to patch do not move. Some are branch
            // targets, which a plain insert would not reach.
            instructions.filter { it.opcode == Opcode.RETURN_OBJECT }.map { it.location.index }.sortedDescending().forEach { index ->
                val item = getInstruction<OneRegisterInstruction>(index).registerA

                addInstructionsAtControlFlowLabel(
                    index,
                    """
                    invoke-static/range { v$item .. v$item }, $ADS_CLASS->hidePromoted(Ljava/lang/Object;)Ljava/lang/Object;
                    move-result-object v$item
                    check-cast v$item, $returnType
                    """,
                )
            }
        }
    }
}
