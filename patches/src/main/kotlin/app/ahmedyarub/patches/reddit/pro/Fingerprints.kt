package app.ahmedyarub.patches.reddit.pro

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.fieldAccess
import app.morphe.patcher.methodCall
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode

/**
 * `com.reddit.pro.promo.domain.model.ProPromoEligibility`.
 *
 * Enum of `PUBLISHER`, `BUSINESS`, `NONE`. Not obfuscated, so it is safe to
 * reference by name from both fingerprints and injected smali.
 */
internal const val PRO_PROMO_ELIGIBILITY_CLASS =
    "Lcom/reddit/pro/promo/domain/model/ProPromoEligibility;"

/**
 * `RedditProPromoEligibilityUseCase.checkEligibility(params, continuation)`.
 *
 * The single gate every "Try Reddit Pro" upsell is behind. Callers such as
 * `PostUploadHandler` (post creation promo), `SubredditPagerViewModel`
 * (subreddit join promo) and `CreatorStatsViewModel` only show their promo when
 * this returns `PUBLISHER` or `BUSINESS`.
 *
 * The class itself is obfuscated to `com.reddit.pro.promo.domain.usecase.a`, and the
 * method to `a`, but the Kotlin coroutine state machine class that the method
 * instantiates keeps its original name because of `@DebugMetadata`, so that is used
 * as the anchor instead of any obfuscated name.
 *
 * The return type is the erased `Ljava/lang/Enum;` rather than the enum itself,
 * because R8 erases the suspend function's return type.
 */
internal object ProPromoEligibilityFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.FINAL),
    returnType = "Ljava/lang/Enum;",
    parameters = listOf("L", "Lkotlin/coroutines/jvm/internal/ContinuationImpl;"),
    filters = listOf(
        methodCall(
            definingClass = "Lcom/reddit/pro/promo/domain/usecase/" +
                    "RedditProPromoEligibilityUseCase\$checkEligibility\$1;",
            name = "<init>",
            opcode = Opcode.INVOKE_DIRECT
        ),
        fieldAccess(
            definingClass = PRO_PROMO_ELIGIBILITY_CLASS,
            name = "NONE",
            opcode = Opcode.SGET_OBJECT
        )
    )
)

/**
 * Factory that turns a profile feed upsell GraphQL node into a
 * `ProfileFeedUpsellElement`, or `null` when the viewer is not eligible.
 *
 * This one is reached without going through
 * [ProPromoEligibilityFingerprint]: it derives eligibility from
 * `ProfileFeedUpsellPromoType` on the feed node plus two feature flags, so the
 * profile feed banner has to be suppressed separately.
 *
 * Every class in the signature is obfuscated, so the anchors are the call
 * returning the (unobfuscated) `ProfileFeedUpsellPromoType` and the two
 * `ProPromoEligibility` constants read in the `BUSINESS` and `PUBLISHER`
 * branches, in that bytecode order.
 */
internal object ProfileFeedUpsellElementFingerprint : Fingerprint(
    accessFlags = listOf(AccessFlags.PUBLIC),
    returnType = "L",
    parameters = listOf("L"),
    filters = listOf(
        methodCall(
            returnType = "Lcom/reddit/type/ProfileFeedUpsellPromoType;"
        ),
        fieldAccess(
            definingClass = PRO_PROMO_ELIGIBILITY_CLASS,
            name = "BUSINESS",
            opcode = Opcode.SGET_OBJECT
        ),
        fieldAccess(
            definingClass = PRO_PROMO_ELIGIBILITY_CLASS,
            name = "PUBLISHER",
            opcode = Opcode.SGET_OBJECT
        )
    )
)
