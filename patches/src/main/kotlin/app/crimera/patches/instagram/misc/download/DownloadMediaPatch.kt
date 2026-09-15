/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.misc.download

import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.patches.instagram.entity.dialogbox.instagramDialogBoxEntity
import app.crimera.patches.instagram.entity.mediadata.mediaDataEntity
import app.crimera.patches.instagram.entity.originalSoundDataIntf.originalSoundDataIntfEntity
import app.crimera.patches.instagram.entity.trackDataIntf.trackDataIntfEntity
import app.crimera.patches.instagram.entity.videoData.videoDataEntity
import app.crimera.patches.instagram.misc.directMessage.saveAllMessages.saveAllMessagesPatch
import app.crimera.patches.instagram.misc.hookFlags.hookFlagsPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.addOverflowMenuButtonAttributes
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.debugOverflowButton.debugOverflowMenuButtonPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.hookFeedSheetPatch
import app.crimera.patches.instagram.misc.overflowMenuButton.posts.hookOverflowMenuButton
import app.crimera.patches.instagram.misc.overflowMenuButton.reels.hookReelOverflowMenuButton
import app.crimera.patches.instagram.misc.extension.sharedExtensionPatch
import app.crimera.patches.instagram.misc.stories.handleStoryButtonPatch
import app.crimera.patches.instagram.utils.Constants.COMPATIBILITY_INSTAGRAM
import app.crimera.patches.instagram.utils.Constants.DOWNLOAD_DESCRIPTOR
import app.crimera.patches.instagram.entity.userdata.userDataEntity
import app.crimera.patches.instagram.utils.addFlags
import app.crimera.patches.instagram.utils.enableSettings
import app.morphe.patcher.extensions.InstructionExtensions.addInstructionsWithLabels
import app.morphe.patcher.extensions.InstructionExtensions.getInstruction
import app.crimera.patches.instagram.entity.messageInfoEntity.directMessageClass
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.util.smali.ExternalLabel
import com.android.tools.smali.dexlib2.AccessFlags

@Suppress("unused")
val downloadMediaPatch =
    bytecodePatch(
        name = "Download media",
        description = "Adds ability to download posts, reels, stories and highlights",
    ) {
        dependsOn(
            sharedExtensionPatch,
            registerFolderPickerPatch,
            instagramDialogBoxEntity,
            mediaDataEntity,
            userDataEntity,
            videoDataEntity,
            originalSoundDataIntfEntity,
            trackDataIntfEntity,
            decoderEntity,
            handleStoryButtonPatch,
            hookFlagsPatch,
            saveAllMessagesPatch,
            hookOverflowMenuButton,
            hookFeedSheetPatch,
            debugOverflowMenuButtonPatch,
            hookReelOverflowMenuButton,
        )
        compatibleWith(COMPATIBILITY_INSTAGRAM)

        execute {

            addOverflowMenuButtonAttributes("PIKO_DOWNLOAD", "downloadOverflowButton")

            // DM media downloader. The saver hands the message to its save routine wrapped in a
            // holder, so the hook has to unwrap it before the extension can read the message type.
            GetDirectThreadMediaSaverModuleNameFingerprint.apply {
                val saverClass = classDef.type
                val appActivityField = classDef.fields.first { it.type == "Landroid/app/Activity;" }

                // Where the saver builds that holder. Its iput names both the holder class and the
                // field the message lands in, and the static method it is then handed to is the
                // funnel every save path goes through (the permission gate calls it too).
                val holderStore =
                    classDef.methods.firstNotNullOfOrNull { candidate ->
                        candidate.implementation?.instructions?.firstOrNull { instruction ->
                            instruction.opcode == Opcode.IPUT_OBJECT &&
                                instruction.getReference<FieldReference>()?.type == directMessageClass
                        }?.let { candidate to it.getReference<FieldReference>()!! }
                    } ?: throw PatchException("The media saver never stores a direct message")
                val (holderBuilder, messageField) = holderStore
                val holderClass = messageField.definingClass

                val saveRoutine =
                    holderBuilder.implementation!!
                        .instructions
                        .mapNotNull { it.getReference<MethodReference>() }
                        .firstOrNull { reference ->
                            reference.definingClass == saverClass && reference.returnType == "V" &&
                                reference.parameterTypes.map { type -> type.toString() }
                                    .containsAll(listOf(saverClass, holderClass))
                        } ?: throw PatchException("The media saver has no save routine taking the message holder")

                val parameterTypes = saveRoutine.parameterTypes.map { it.toString() }
                val saverParameter = parameterTypes.indexOf(saverClass)
                val holderParameter = parameterTypes.indexOf(holderClass)

                classDef.methods
                    .first { it.name == saveRoutine.name && it.parameterTypes.map { type -> type.toString() } == parameterTypes }
                    .apply {
                        // p registers sit at the top of the frame, so on a wide method they are
                        // above v15 — and iget-object / move-object encode their registers in
                        // 4 bits, which the assembler drops silently. Stage them through v0/v1,
                        // which are free at entry (the local count is checked below).
                        val parameterRegisters =
                            parameterTypes.sumOf { type -> if (type == "J" || type == "D") 2 else 1 } +
                                if (AccessFlags.STATIC.isSet(accessFlags)) 0 else 1
                        val localRegisters = implementation!!.registerCount - parameterRegisters
                        if (localRegisters < 2) {
                            throw PatchException(
                                "The DM save routine $saverClass->$name has no scratch registers",
                            )
                        }

                        addInstructionsWithLabels(
                            0,
                            """
                            move-object/from16 v0, p$saverParameter
                            iget-object v0, v0, $saverClass->${appActivityField.name}:Landroid/app/Activity;
                            move-object/from16 v1, p$holderParameter
                            iget-object v1, v1, $holderClass->${messageField.name}:$directMessageClass
                            invoke-static {v0, v1}, $DOWNLOAD_DESCRIPTOR/MessageUtils;->messageDownloadCheck(Landroid/content/Context;Ljava/lang/Object;)Z
                            move-result v1
                            if-nez v1, :piko
                            return-void
                            """.trimIndent(),
                            ExternalLabel("piko", getInstruction(0)),
                        )
                    }
            }

            // The extension gates every download entry point on this, so without it the patch
            // applies and then does nothing visible. piko sets it from its settings screen.
            enableSettings("downloadMedia")

            addFlags("simpleOverflowMenuFlags")
        }
    }
