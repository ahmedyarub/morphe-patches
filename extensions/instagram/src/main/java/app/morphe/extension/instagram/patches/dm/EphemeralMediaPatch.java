/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.dm;

import android.content.Context;
import android.content.SharedPreferences;

import com.instagram.common.session.UserSession;

import app.morphe.extension.instagram.entity.DirectItem;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;
import app.morphe.extension.shared.Utils;

@SuppressWarnings("unused")
public class EphemeralMediaPatch {
    private static final boolean MAKE_EPHEMERAL_MEDIA_PERMANENT;
    private static final String PERMA_KEY = "permanent";

    static {
        MAKE_EPHEMERAL_MEDIA_PERMANENT = Pref.makeEphemeralMediaPermanent();
    }

    /** Viewed visual messages, by viewer and item id. The server never learns of them. */
    private static final String VIEWED_PREFERENCES = "piko_ephemeral_media_viewed";

    /**
     * Injection point. Called where the viewer would send the server a seen receipt, and returns
     * true to send none. The server drops the media of a message it knows was seen, which a chat
     * refresh then shows. The message is remembered as viewed here instead.
     */
    public static boolean suppressSeenReceipt(UserSession session, Object item) {
        if (!MAKE_EPHEMERAL_MEDIA_PERMANENT) return false;
        try {
            String key = viewedKey(session.getUserId(), item);
            SharedPreferences preferences = viewedPreferences();
            if (key != null && preferences != null) preferences.edit().putBoolean(key, true).apply();
        } catch (Exception e) {
            Logger.printException(() -> "suppressSeenReceipt failed", e);
        }
        return true;
    }

    /**
     * Injection point. Called only when the app would show a visual message as unseen, which,
     * without the receipt, the server reports for every one. Returns true if it was viewed here.
     */
    public static boolean wasViewed(Object item, Object viewer) {
        if (!MAKE_EPHEMERAL_MEDIA_PERMANENT) return false;
        try {
            String key = viewedKey(new UserData(viewer).getUserId(), item);
            SharedPreferences preferences = viewedPreferences();
            return key != null && preferences != null && preferences.getBoolean(key, false);
        } catch (Exception e) {
            Logger.printException(() -> "wasViewed failed", e);
            return false;
        }
    }

    private static String viewedKey(String viewerId, Object item) {
        if (viewerId == null || viewerId.isEmpty() || item == null) return null;
        String itemId = new DirectItem(item).getItemId();
        return itemId == null || itemId.isEmpty() ? null : viewerId + ":" + itemId;
    }

    private static SharedPreferences viewedPreferences() {
        Context context = Utils.getContext();
        return context == null ? null : context.getSharedPreferences(VIEWED_PREFERENCES, Context.MODE_PRIVATE);
    }

    public static String makeEphemeralMediaPermanent(Long expireAt, String viewMode) {
        try {
            if (expireAt == null || !MAKE_EPHEMERAL_MEDIA_PERMANENT) return viewMode;

            long currentTime = System.currentTimeMillis();
            long expireAtConv = Long.valueOf(expireAt) * 1000;

            // If current time is less than expire at time and the view mode isn't permanent
            // change the view mode to permanent.
            viewMode = currentTime <= expireAtConv && !viewMode.equals(PERMA_KEY) ? PERMA_KEY : viewMode;

        } catch (Exception e) {
            Logger.printException(() -> "error makeEphemeralMediaPermanent: " + e);
        }
        return viewMode;
    }

}
