/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.directMessage.makeEphemeralPermanent

import app.ahmedyarub.patches.shared.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.entity.directItem.directItemEntity
import app.crimera.patches.instagram.entity.messageInfoEntity.messageInfoEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.utils.Constants.PATCHES_DESCRIPTOR
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.library.instagram.patches.instagramExtensionPatch
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.smali.ExternalLabel
import app.morphe.util.getFreeRegisterProvider
import app.morphe.util.getReference
import app.morphe.util.registersUsed
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.reference.TypeReference

internal object EphemeralMediaJsonParserFingerprint : Fingerprint(
    name = "unsafeParseFromJson",
    returnType = "Ljava/lang/Object;",
    strings = listOf("url_expire_at_secs", "view_mode", "seen_count", "tap_models"),
)

@Suppress("unused")
val makeEphemeralPermanentPatch =
    bytecodePatch(
        name = "Make ephemeral media permanent",
        description =
            "Changes unexpired view once, view twice media to permanent view. Opening such media " +
                "no longer sends Instagram a seen receipt, so it is still there after the chat refreshes.",
        default = true,
    ) {
        dependsOn(instagramExtensionPatch)
        compatibleWith(COMPATIBILITY_INSTAGRAM)
        dependsOn(messageInfoEntity, saveAllMessagesPatch, directItemEntity)
        execute {
            suppressSeenReceipts()

            EphemeralMediaJsonParserFingerprint.method.apply {
                val code = instructions.toList()

                // The field a key's value is stored in: the first store of the expected type after
                // the key. piko took the last store before "view_mode" as the expiry, which on 448
                // is the tap models list, so the hook read a Long field that does not exist and
                // threw NoSuchFieldError for every view-once message.
                fun fieldAfter(
                    key: String,
                    type: String,
                ): FieldReference {
                    val keyIndex = code.indexOfFirst { it.getReference<StringReference>()?.string == key }
                    if (keyIndex < 0) throw PatchException("The ephemeral media parser has no \"$key\" key")
                    return code.drop(keyIndex + 1).firstNotNullOfOrNull { instruction ->
                        instruction.takeIf { it.opcode == Opcode.IPUT_OBJECT }
                            ?.getReference<FieldReference>()
                            ?.takeIf { it.type == type }
                    } ?: throw PatchException("No $type is stored after \"$key\"")
                }

                val expireAtField = fieldAfter("url_expire_at_secs", "Ljava/lang/Long;")
                val viewModeField = fieldAfter("view_mode", "Ljava/lang/String;")

                // The parser returns the object it filled when it reaches the end of the JSON
                // object. That is the if-eq branching straight to the final return, and the hook
                // goes in front of it, in the path where the parse has finished.
                val finalReturn = code.last { it.opcode == Opcode.RETURN_OBJECT }
                val objectRegister = finalReturn.registersUsed[0]
                val endCheck =
                    code.firstOrNull { instruction ->
                        instruction.opcode == Opcode.IF_EQ && branchTarget(code, instruction) == finalReturn
                    } ?: throw PatchException("The ephemeral media parser has no end-of-object check")
                val (tokenRegister, endTokenRegister) = endCheck.registersUsed

                // Past the if-ne the hook only returns, so every register but the object's is
                // spare there, token registers included. iget-object and the non-range invoke
                // take four-bit registers.
                if (objectRegister > 15) throw PatchException("The parsed object is not in a four-bit register")
                val (expireAt, viewMode) =
                    (0 until minOf(16, implementation!!.registerCount)).filter { it != objectRegister }.take(2)
                        .takeIf { it.size == 2 } ?: throw PatchException("The ephemeral media parser has too few registers")

                addInstructionsWithLabels(
                    endCheck.location.index,
                    """
                    if-ne v$tokenRegister, v$endTokenRegister, :parsing
                    iget-object v$expireAt, v$objectRegister, $expireAtField
                    iget-object v$viewMode, v$objectRegister, $viewModeField
                    invoke-static { v$expireAt, v$viewMode }, $PATCHES_DESCRIPTOR/dm/EphemeralMediaPatch;->makeEphemeralMediaPermanent(Ljava/lang/Long;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$viewMode
                    iput-object v$viewMode, v$objectRegister, $viewModeField
                    return-object v$objectRegister
                    """,
                    ExternalLabel("parsing", getInstruction(endCheck.location.index)),
                )
            }
            enableSettings("unlimitedReplaysOnEphemeralMedia")
        }
    }

private const val EXTENSION = "$PATCHES_DESCRIPTOR/dm/EphemeralMediaPatch;"
private const val USER_SESSION = "Lcom/instagram/common/session/UserSession;"
private const val USER_MODEL = "Lcom/instagram/user/model/User;"

// The request that tells the server a visual message was seen. Its handler casts the event to
// the type the viewer builds.
private object EphemeralMediaSeenReceiptFingerprint : Fingerprint(
    filters = listOf(string("direct_v2/visual_threads/%s/item_seen/"), string("raven_media")),
)

/**
 * Opening view once media sends the server a seen receipt, and the next chat refresh then returns
 * the message without its media. The receipt is no longer sent, and the message is remembered as
 * viewed locally instead, as the server keeps reporting it unseen.
 */
context(patchContext: BytecodePatchContext)
private fun suppressSeenReceipts() {
    val eventType =
        EphemeralMediaSeenReceiptFingerprint.method.instructions.firstOrNull { it.opcode == Opcode.CHECK_CAST }
            ?.getReference<TypeReference>()?.type
            ?: throw PatchException("The seen receipt request casts no event")

    val producer =
        Fingerprint(
            definingClass = "Linstagram/features/direct/visual/internal/DirectVisualMessageViewerController;",
            returnType = "V",
            filters = listOf(string("directVisualViewerSummaryLogger")),
            custom = { method, _ ->
                method.implementation?.instructions?.any {
                    it.opcode == Opcode.NEW_INSTANCE && it.getReference<TypeReference>()?.type == eventType
                } == true
            },
        ).method
    val code = producer.instructions.toList()
    val creationIndex =
        code.indices.singleOrNull {
            code[it].opcode == Opcode.NEW_INSTANCE && code[it].getReference<TypeReference>()?.type == eventType
        } ?: throw PatchException("The viewer does not build one seen receipt")

    // The viewer asks the item whether it is unseen, and later builds the receipt from the item.
    val unseenIndex =
        (0 until creationIndex).singleOrNull { index ->
            val reference = code[index].getReference<MethodReference>()
            code[index].opcode == Opcode.INVOKE_VIRTUAL && reference?.returnType == "Z" &&
                reference.parameterTypes.map { it.toString() } == listOf(USER_MODEL)
        } ?: throw PatchException("The viewer does not ask the item whether it is unseen once")
    val unseenReference = code[unseenIndex].getReference<MethodReference>()!!
    val itemRegister = code[unseenIndex].registersUsed.first()

    // The receipt's request is built from the session. The hook goes in front of that call: by the
    // time the receipt itself is created, the item's register holds one of the item's strings.
    val sessionIndex =
        (unseenIndex + 1 until creationIndex).lastOrNull { index ->
            code[index].opcode == Opcode.INVOKE_STATIC &&
                code[index].getReference<MethodReference>()?.parameterTypes?.firstOrNull()?.toString() == USER_SESSION
        } ?: throw PatchException("The seen receipt is not built from the session")
    val sessionRegister = code[sessionIndex].registersUsed.first()
    if ((unseenIndex + 1 until sessionIndex).any { index ->
            code[index].opcode.setsRegister() && (code[index] as? OneRegisterInstruction)?.registerA == itemRegister
        }
    ) {
        throw PatchException("The item is overwritten before the seen receipt is built")
    }
    if (itemRegister > 15 || sessionRegister > 15) {
        throw PatchException("The seen receipt's arguments are not in four-bit registers")
    }

    // The predicate must be the one that reads the seen count the parser fills.
    val parser = EphemeralMediaJsonParserFingerprint.method.instructions.toList()
    val seenKeyIndex = parser.indexOfFirst { it.getReference<StringReference>()?.string == "seen_count" }
    val seenField =
        parser.drop(seenKeyIndex + 1).firstOrNull { it.opcode == Opcode.IPUT }?.getReference<FieldReference>()
            ?: throw PatchException("The ephemeral media parser stores no seen count")
    val unseen =
        Fingerprint(
            definingClass = unseenReference.definingClass,
            name = unseenReference.name,
            returnType = "Z",
            parameters = listOf(USER_MODEL),
        ).method
    if (unseen.instructions.none { it.getReference<FieldReference>() == seenField }) {
        throw PatchException("The unseen predicate does not read the seen count")
    }

    // Only where the app would answer unseen, so every other answer needs no lookup.
    val unseenCode = unseen.instructions.toList()
    val trueIndex =
        unseenCode.indices.singleOrNull { index ->
            val instruction = unseenCode[index]
            val next = unseenCode.getOrNull(index + 1)
            instruction.opcode == Opcode.CONST_4 && (instruction as NarrowLiteralInstruction).narrowLiteral == 1 &&
                next?.opcode == Opcode.RETURN &&
                (next as OneRegisterInstruction).registerA == (instruction as OneRegisterInstruction).registerA
        } ?: throw PatchException("The unseen predicate does not answer true once")
    val resultRegister = (unseenCode[trueIndex] as OneRegisterInstruction).registerA
    unseen.addInstructionsWithLabels(
        trueIndex,
        """
        invoke-static { p0, p1 }, $EXTENSION->wasViewed(Ljava/lang/Object;Ljava/lang/Object;)Z
        move-result v$resultRegister
        if-eqz v$resultRegister, :unseen
        const/4 v$resultRegister, 0x0
        return v$resultRegister
        """,
        ExternalLabel("unseen", unseen.getInstruction(trueIndex)),
    )

    val register =
        producer.getFreeRegisterProvider(index = sessionIndex, numberOfFreeRegistersNeeded = 1).getFreeRegister()
    producer.addInstructionsWithLabels(
        sessionIndex,
        """
        invoke-static { v$sessionRegister, v$itemRegister }, $EXTENSION->suppressSeenReceipt(${USER_SESSION}Ljava/lang/Object;)Z
        move-result v$register
        if-eqz v$register, :send_receipt
        return-void
        """,
        ExternalLabel("send_receipt", producer.getInstruction(sessionIndex)),
    )
}

/** The instruction a branch jumps to. */
private fun branchTarget(
    instructions: List<Instruction>,
    branch: Instruction,
): Instruction? {
    var address = 0
    val addresses = instructions.map { instruction -> address.also { address += instruction.codeUnits } }
    val target = addresses[instructions.indexOf(branch)] + (branch as OffsetInstruction).codeOffset
    return instructions.getOrNull(addresses.indexOf(target))
}
