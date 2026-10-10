package app.ahmedyarub.extension.instagram;

import java.lang.reflect.Field;
import java.util.List;

import app.morphe.extension.shared.Logger;
import app.morphe.library.extension.instagram.patches.FilterStoriesListPatch;

@SuppressWarnings("unused")
public final class StoryTray {
    private static volatile boolean failureLogged;

    private StoryTray() {
    }

    public static void addStoryIfNotBlocked(List<Object> stories, Object story, String reelTypeFieldName) {
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

        if (story == null) return;

        try {
            if (hasYourTurnPrompt(story)) {
                stories.remove(story);
                return;
            }
            if (isNetegoItem(story)) {
                stories.remove(story);
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasYourTurnPrompt(Object story) {
        try {
            Field f = story.getClass().getDeclaredField("A0I");
            f.setAccessible(true);
            return f.get(story) != null;
        } catch (Throwable e) {
            return false;
        }
    }

    private static boolean isNetegoItem(Object story) {
        for (String fieldName : new String[]{
                "A09", "A0A", "A0G", "A0J", "A0R",
                "A0U", "A0V", "A0W", "A0X", "A0Y", "A0Z",
                "A0b", "A0d", "A0e", "A0f", "A0g"}) {
            try {
                Field f = story.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                if (f.get(story) != null) return true;
            } catch (Throwable ignored) {
            }
        }
        return false;
    }
}
