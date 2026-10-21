package app.ahmedyarub.extension.instagram;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import app.morphe.extension.shared.Logger;
import app.morphe.library.extension.instagram.patches.FilterStoriesListPatch;

@SuppressWarnings("unused")
public final class StoryTray {
    private static final String TAG = "StoryFilter";

    /**
     * Reel types people post themselves: their own stories, close friends, shared and collab
     * stories, lives and subscriber stories. Every other tray type is one Instagram injects.
     */
    private static final Set<String> ORGANIC_REEL_TYPES = new HashSet<>(Arrays.asList(
            "user_reel",
            "private_story",
            "group_reel",
            "mas_reel",
            "broadcast_reel",
            "fan_club_story"
    ));

    private static volatile boolean failureLogged;

    private StoryTray() {
    }

    /** Replaced at patch time with true when "Only organic stories" is on. */
    private static boolean onlyOrganicStories() {
        return false;
    }

    public static void addStoryIfNotBlocked(List<Object> stories, Object story, String reelTypeFieldName) {
        if (story != null) {
            try {
                String reason = droppedReason(story, reelTypeFieldName);
                if (reason != null) {
                    Log.d(TAG, "Dropped tray item: " + reason);
                    return;
                }
            } catch (Throwable ex) {
                // Fails open. The check reads the same reel-type field as the library filter
                // below, so a failure here is not specific to one item: failing closed would
                // empty the whole tray rather than drop a card.
                if (!failureLogged) {
                    failureLogged = true;
                    Logger.printException(() -> "Could not check a story tray item of "
                            + story.getClass().getName(), ex);
                }
            }
        }

        try {
            FilterStoriesListPatch.addStoryIfNotBlocked(stories, story, reelTypeFieldName);
        } catch (Throwable ex) {
            if (!failureLogged) {
                failureLogged = true;
                Logger.printException(() -> "Could not filter a story tray item of "
                        + (story == null ? "null" : story.getClass().getName()), ex);
            }
            stories.add(story);
        }
    }

    /**
     * Why the item is dropped, or null to hand it to the library filter. Injected units are
     * always dropped; with "Only organic stories" so is every reel type people do not post.
     */
    private static String droppedReason(Object story, String reelTypeFieldName) throws Exception {
        Field typeField = story.getClass().getDeclaredField(reelTypeFieldName);
        typeField.setAccessible(true);
        Object type = typeField.get(story);
        String typeName = type == null ? null : type.toString();
        if (onlyOrganicStories() && !ORGANIC_REEL_TYPES.contains(typeName)) return "type " + typeName;

        // Injected units (prompts, music and Threads cards, suggested users or products, ads)
        // carry their payload in a field of a netego, suggestion or ad schema type, whose names
        // survive obfuscation unlike the field names.
        for (Field field : story.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) || !isInjectedUnitType(field.getType())) continue;
            field.setAccessible(true);
            if (field.get(story) != null) {
                return "type " + typeName + " carrying " + field.getType().getSimpleName();
            }
        }
        return null;
    }

    private static boolean isInjectedUnitType(Class<?> type) {
        String name = type.getName();
        return name.contains("Netego")
                || name.contains(".netego.")
                || name.contains("SuggestedUsers")
                || name.contains("SuggestedProducts")
                || name.contains("Ad4ad");
    }
}
