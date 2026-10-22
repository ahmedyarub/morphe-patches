/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.userdata

import app.crimera.patches.instagram.entity.decoder.USER_MODEL_CLASS_NAME
import app.crimera.patches.instagram.entity.decoder.decoderEntity
import app.crimera.utils.changeFirstString
import app.crimera.utils.liveTreeGetter
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import app.morphe.library.instagram.patches.instagramExtensionPatch

private const val STRING = "Ljava/lang/String;"
private const val BOXED_BOOLEAN = "Ljava/lang/Boolean;"

/**
 * Names for the user getters the extension reflects into.
 *
 * piko reads these off a LiveTreeUserDict class that 446 removed, so each one is found by the json
 * key its getter carries instead — the same anchor the media model uses, and the one piko itself
 * already reaches for on the username getter.
 */
val userDataEntity =
    bytecodePatch(
        description = "This patch is used for decoding obfuscated code of the user data",
    ) {
        dependsOn(instagramExtensionPatch)
        dependsOn(decoderEntity)
        execute {
            fun getterName(
                jsonKey: String,
                returnType: (String) -> Boolean,
            ) = liveTreeGetter(USER_MODEL_CLASS_NAME, jsonKey, returnType)?.name
                ?: throw PatchException("Could not identify the $jsonKey getter on the user model")

            GetUsernameExtensionFingerprint.changeFirstString(getterName("username") { it == STRING })
            // The id getter. The extension's hard-coded getId is gone from the user model (451 and
            // 452 alike), and the id carries no json key of its own: the model's bulk tree methods
            // read it right after the "strong_id__" key, which is piko's anchor too.
            val userIdGetters = classDefBy(USER_MODEL_CLASS_NAME).methods.mapNotNull { method ->
                val instructions = method.implementation?.instructions?.toList() ?: return@mapNotNull null
                val keyIndex = instructions.indexOfFirst { it.getReference<StringReference>()?.string == "strong_id__" }
                if (keyIndex < 0) return@mapNotNull null
                instructions.getOrNull(keyIndex + 1)
                    ?.takeIf { it.opcode == Opcode.INVOKE_VIRTUAL }
                    ?.getReference<MethodReference>()
                    ?.takeIf { it.definingClass == USER_MODEL_CLASS_NAME && it.parameterTypes.isEmpty() && it.returnType == STRING }
                    ?.name
            }.distinct()
            GetUserIdExtensionFingerprint.changeFirstString(
                userIdGetters.singleOrNull() ?: throw PatchException("Could not identify the user id getter: $userIdGetters"),
            )
            GetFullNameExtensionFingerprint.changeFirstString(getterName("full_name") { it == STRING })
            GetBioExtensionFingerprint.changeFirstString(getterName("biography") { it == STRING })
            IsVerifiedExtensionFingerprint.changeFirstString(getterName("is_verified") { it == BOXED_BOOLEAN })
            GetProfilePictureUrlExtensionFingerprint.changeFirstString(
                getterName("hd_profile_pic_url_info") { it.endsWith("/ProfilePicUrlInfo;") },
            )
            GetLowResProfilePictureExtensionFingerprint.changeFirstString(
                getterName("profile_pic_url") { it.endsWith("/ImageUrl;") },
            )
        }
    }
