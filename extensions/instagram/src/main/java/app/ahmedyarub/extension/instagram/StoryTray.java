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
        if (story != null && onlyOrganicStories()) {
            try {
                String reason = nonOrganicReason(story, reelTypeFieldName);
                if (reason != null) {
                    Log.d(TAG, "Dropped tray item: " + reason);
                    return;
                }
            } catch (Throwable ex) {
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

    /** Why the item is not an organic story, or null if it is one. */
    private static String nonOrganicReason(Object story, String reelTypeFieldName) throws Exception {
        Field typeField = story.getClass().getDeclaredField(reelTypeFieldName);
        typeField.setAccessible(true);
        Object type = typeField.get(story);
        String typeName = type == null ? null : type.toString();
        if (!ORGANIC_REEL_TYPES.contains(typeName)) return "type " + typeName;

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
