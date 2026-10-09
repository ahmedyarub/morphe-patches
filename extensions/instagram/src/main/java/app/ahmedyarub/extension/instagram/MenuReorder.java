package app.ahmedyarub.extension.instagram;

import java.util.List;

import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public final class MenuReorder {

    private MenuReorder() {
    }

    /**
     * Injection point. Moves INTERESTED and NOT_INTERESTED to the top of the menu options list.
     */
    public static void reorderMenuOptions(List<Object> options) {
        try {
            if (options == null || options.size() < 2) return;

            Object interested = null;
            Object notInterested = null;

            for (Object option : options) {
                if (option instanceof Enum) {
                    String name = ((Enum<?>) option).name();
                    if ("INTERESTED".equals(name)) interested = option;
                    else if ("NOT_INTERESTED".equals(name)) notInterested = option;
                }
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
}
