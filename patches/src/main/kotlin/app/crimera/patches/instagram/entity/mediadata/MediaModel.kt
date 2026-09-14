/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.mediadata

import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.util.getReference
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.WideLiteralInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference

/**
 * The generated getter on [mediaModelClass] that reads the json key [jsonKey] and returns
 * [returnType], or null when it cannot be told apart from anything else on the class.
 *
 * Every getter carries its own key, which makes the key a far steadier anchor than the
 * unrelated call sites piko reads these names out of. How it carries it changed with the app:
 * up to v439 the key sat in the getter as a plain string, while v446 pools the keys and passes
 * `key.hashCode()` to the LiveTree reader instead. Both forms are accepted here.
 *
 * Taking no arguments is what separates a getter from its setter and from the bulk update
 * method, both of which mention the same key.
 */
internal fun BytecodePatchContext.mediaModelGetter(
    jsonKey: String,
    returnType: String,
): Method? = mediaModelGetter(jsonKey) { it == returnType }

/**
 * As above, for the getters whose return type is itself an obfuscated or version-dependent
 * class and can only be recognised by shape.
 */
internal fun BytecodePatchContext.mediaModelGetter(
    jsonKey: String,
    returnType: (String) -> Boolean,
): Method? {
    val keyHash = jsonKey.hashCode().toLong()
    return classDefByOrNull(mediaModelClass)
        ?.methods
        ?.singleOrNull { method ->
            method.parameters.isEmpty() &&
                returnType(method.returnType) &&
                method.mentions(jsonKey, keyHash)
        }
}

private fun Method.mentions(
    jsonKey: String,
    keyHash: Long,
): Boolean =
    implementation?.instructions?.any { instruction ->
        when (instruction.opcode) {
            Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO ->
                instruction.getReference<StringReference>()?.string == jsonKey
            else -> (instruction as? WideLiteralInstruction)?.wideLiteral == keyHash
        }
    } == true

/**
 * The field a [mediaModelGetter] caches its value in. Since v446 the media model keeps its
 * decoded values on a separate holder object, so this is how that holder is found.
 */
internal fun Method.cacheFieldOrNull(): FieldReference? =
    implementation
        ?.instructions
        ?.firstOrNull { it.opcode == Opcode.IPUT_OBJECT }
        ?.getReference<FieldReference>()
