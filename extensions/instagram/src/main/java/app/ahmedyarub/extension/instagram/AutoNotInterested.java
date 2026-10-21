package app.ahmedyarub.extension.instagram;

import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;

@SuppressWarnings("unused")
public final class AutoNotInterested {

    private static final String NOT_INTERESTED_ACTION = "not_interested";
    private static final String SHEET_HEADER_ID = "action_sheet_header_text_view";

    /** The localized label of the overflow menu's Not Interested option, which titles the reasons popup. */
    private static volatile String notInterestedLabel;

    private AutoNotInterested() {
    }

    /**
     * Called with the overflow menu's options as it is presented. Each option holds its action
     * id ("not_interested") and its label in String fields.
     */
    public static void onMenuOptions(List<Object> options) {
        try {
            if (options == null) return;
            for (Object option : options) {
                List<String> strings = new ArrayList<>();
                for (Field field : option.getClass().getDeclaredFields()) {
                    if (field.getType() != String.class) continue;
                    field.setAccessible(true);
                    Object value = field.get(option);
                    if (value != null) strings.add((String) value);
                }
                if (!strings.remove(NOT_INTERESTED_ACTION) || strings.size() != 1) continue;
                notInterestedLabel = strings.get(0);
                return;
            }
        } catch (Throwable ex) {
            Logger.printException(() -> "AutoNI: onMenuOptions failed", ex);
        }
    }

    public static void onActionSheetCreated(Object fragment) {
        try {
            View rootView = getFragmentView(fragment);
            if (rootView == null) return;

            rootView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                private boolean fired;

                @Override
                public void onGlobalLayout() {
                    if (fired) return;
                    List<TextView> textViews = new ArrayList<>();
                    collectTextViews(rootView, textViews);

                    // Only the reasons popup is titled with the Not Interested label. Other
                    // sheets, such as the story options menu, must be left alone.
                    if (!isReasonsPopup(textViews)) return;

                    fired = true;
                    rootView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                    // The first text is the header. Reasons start at index 1.
                    // Click the first reason (index 1).
                    TextView target = textViews.get(1);
                    simulateTap(target);
                    rootView.postDelayed(() -> dismissFragment(fragment), 300);
                }
            });
        } catch (Throwable ex) {
            Logger.printException(() -> "AutoNI: onActionSheetCreated failed", ex);
        }
    }

    private static boolean isReasonsPopup(List<TextView> textViews) {
        String label = notInterestedLabel;
        if (label == null || textViews.size() < 2) return false;
        TextView header = textViews.get(0);
        if (header.getId() == View.NO_ID) return false;
        try {
            if (!SHEET_HEADER_ID.equals(header.getResources().getResourceEntryName(header.getId()))) return false;
        } catch (Throwable ex) {
            return false;
        }
        return label.contentEquals(header.getText());
    }

    private static void simulateTap(View view) {
        int[] loc = new int[2];
        view.getLocationOnScreen(loc);
        float x = loc[0] + view.getWidth() / 2f;
        float y = loc[1] + view.getHeight() / 2f;

        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0);

        View root = view.getRootView();
        root.dispatchTouchEvent(down);
        root.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }

    private static void collectTextViews(View root, List<TextView> list) {
        if (root instanceof TextView) {
            CharSequence text = ((TextView) root).getText();
            if (text != null && text.length() > 0) {
                list.add((TextView) root);
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTextViews(group.getChildAt(i), list);
            }
        }
    }

    private static View getFragmentView(Object fragment) {
        try {
            Method getView = fragment.getClass().getMethod("getView");
            return (View) getView.invoke(fragment);
        } catch (Throwable e) {
            return null;
        }
    }

    private static void dismissFragment(Object fragment) {
        try {
            Method dismiss = fragment.getClass().getMethod("dismissAllowingStateLoss");
            dismiss.invoke(fragment);
        } catch (Throwable ignored) {
        }
    }
}
