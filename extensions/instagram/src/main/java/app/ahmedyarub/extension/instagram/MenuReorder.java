package app.ahmedyarub.extension.instagram;

import java.lang.reflect.Field;
import java.util.List;

import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public final class MenuReorder {

    private MenuReorder() {
    }

    public static void reorderMenuOptions(List<Object> options) {
        try {
            if (options == null || options.size() < 2) return;

            Object interested = null;
            Object notInterested = null;
            for (Object option : options) {
                if (interested == null && hasAction(option, "interested")) interested = option;
                if (notInterested == null && hasAction(option, "not_interested")) notInterested = option;
            }

            if (notInterested != null) {
                options.remove(notInterested);
                options.add(0, notInterested);
            }
            if (interested != null) {
                options.remove(interested);
                options.add(0, interested);
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "Could not reorder menu options", ex);
        }
    }

    private static boolean hasAction(Object item, String action) {
        try {
            for (Field field : item.getClass().getDeclaredFields()) {
                if (field.getType() == String.class) {
                    field.setAccessible(true);
                    if (action.equals(field.get(item))) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}
