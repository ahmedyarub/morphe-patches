package app.ahmedyarub.extension.instagram;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
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

    private static final String TRAY_ITEM_INTERFACE = "com.instagram.model.reels.ReelResponseItemIntf";

    /** The tray item interface's reel type getter and injected-unit payload getters, found once. */
    private static volatile Method trayItemTypeGetter;
    private static volatile List<Method> trayItemPayloadGetters;

    /**
     * Injection point: the tray items the reel store is about to add, whichever response or model
     * they came from. Returns the items without injected units and, with "Only organic stories",
     * without reel types people do not post. The list is returned as is when nothing is dropped.
     */
    public static List<Object> filterTrayItems(List<Object> items) {
        if (items == null || items.isEmpty()) return items;
        try {
            List<Object> kept = new ArrayList<>(items.size());
            for (Object item : items) {
                String reason = item == null ? null : trayItemDropReason(item);
                if (reason == null) {
                    kept.add(item);
                } else {
                    Log.d(TAG, "Dropped store tray item: " + reason);
                }
            }
            return kept.size() == items.size() ? items : kept;
        } catch (Throwable ex) {
            // Fails open, as addStoryIfNotBlocked does: a failure here is not specific to one item.
            if (!failureLogged) {
                failureLogged = true;
                Logger.printException(() -> "Could not filter the reel store's tray items", ex);
            }
            return items;
        }
    }

    /** Why a tray item is dropped, or null to keep it. Items of other types are kept. */
    private static String trayItemDropReason(Object item) throws Exception {
        Class<?> intf = trayItemInterface(item.getClass());
        if (intf == null) return null;
        resolveTrayItemGetters(intf);

        for (Method getter : trayItemPayloadGetters) {
            if (getter.invoke(item) != null) return "carrying " + getter.getReturnType().getSimpleName();
        }
        if (onlyOrganicStories() && trayItemTypeGetter != null) {
            Object type = trayItemTypeGetter.invoke(item);
            // An item without a type is kept: nothing marks it as injected.
            if (type != null && !ORGANIC_REEL_TYPES.contains(type.toString())) return "type " + type;
        }
        return null;
    }

    private static Class<?> trayItemInterface(Class<?> type) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Class<?> intf : c.getInterfaces()) {
                if (intf.getName().equals(TRAY_ITEM_INTERFACE)) return intf;
            }
        }
        return null;
    }

    /**
     * Finds the getters by return type, since their names are obfuscated: the type getter returns
     * the reel type enum (which has a suggested_user_reel constant), the payload getters return
     * netego, suggestion or ad schema types.
     */
    private static void resolveTrayItemGetters(Class<?> intf) {
        if (trayItemPayloadGetters != null) return;
        List<Method> payloads = new ArrayList<>();
        Method typeGetter = null;
        for (Method method : intf.getMethods()) {
            if (method.getParameterTypes().length != 0) continue;
            Class<?> returnType = method.getReturnType();
            if (isInjectedUnitType(returnType)) {
                payloads.add(method);
            } else if (returnType.isEnum() && hasConstant(returnType, "suggested_user_reel")) {
                typeGetter = method;
            }
        }
        trayItemTypeGetter = typeGetter;
        trayItemPayloadGetters = payloads;
    }

    private static boolean hasConstant(Class<?> enumType, String value) {
        for (Object constant : enumType.getEnumConstants()) {
            if (value.equals(constant.toString())) return true;
        }
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
