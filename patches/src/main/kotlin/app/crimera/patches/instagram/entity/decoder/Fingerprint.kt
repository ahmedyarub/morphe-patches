/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.crimera.patches.instagram.entity.decoder

import app.morphe.patcher.Fingerprint

internal object UserTagInfoDictInitFingerprint : Fingerprint(
    definingClass = "Lcom/instagram/api/schemas/UserTagInfoDict;",
    name = "<init>",
)

/**
 * The media helper class, by a method of it taking only a media object. The media class is
 * compared when matching, not when the object is created: [MEDIA_CLASS_NAME] is only known once
 * [decoderEntity] has run, and an object initialised any earlier fails for good.
 */
object ReelsInlineQualitySurveyRelatedFingerprint : Fingerprint(
    strings = listOf("reels_inline_quality_survey"),
    custom = { method, _ -> method.parameterTypes.singleOrNull()?.toString() == MEDIA_CLASS_NAME },
)
