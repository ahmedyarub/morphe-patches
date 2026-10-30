package app.ahmedyarub.extension.instagram;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Logger;

/**
 * Interested and Not interested buttons above the Reels side bar. A tap opens the reel's ⋯ menu,
 * unseen, so the app binds its options helper to that reel, then runs the helper's own handler for
 * the option and closes the menu. Whether, and how, the menu lists the options themselves depends
 * on server experiments, so they are not looked for in it.
 *
 * The side bar is drawn by Litho, which does not take views of its own, so the buttons are a
 * column over the activity, kept above the like button of the reel on screen as it scrolls.
 */
@SuppressWarnings("unused")
public final class ReelsButtons {
    private static final String MORE_TAG = "clips_ufi_more_button_component";
    private static final String LIKE_TAG = "clips_ufi_like_button_component";
    private static final String INTERESTED = "INTERESTED";
    private static final String NOT_INTERESTED = "NOT_INTERESTED";
    private static final String OPTION_CLASS = "com.instagram.feed.media.mediaoption.MediaOption$Option";
    private static final String COLUMN_TAG = "morphe_reels_buttons";
    private static final long PENDING_TIMEOUT_MS = 4000;

    private static volatile WeakReference<Object> menuHelper = new WeakReference<>(null);
    private static volatile WeakReference<View> openSheet = new WeakReference<>(null);
    private static volatile String pendingAction;
    private static volatile long pendingSince;

    private ReelsButtons() {
    }

    /** The options helper's method running an option. Rewritten by the patch. */
    private static String optionHandler() {
        return "optionHandler";
    }

    /** The integer tag the ⋯ button may carry instead of its name. Rewritten by the patch. */
    private static int moreTagId() {
        return 0;
    }

    // region Buttons

    /** Injection point: the application's onCreate. */
    public static void install(Application application) {
        try {
            application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                @Override
                public void onActivityResumed(Activity activity) {
                    attach(activity);
                }

                @Override
                public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
                }

                @Override
                public void onActivityStarted(Activity activity) {
                }

                @Override
                public void onActivityPaused(Activity activity) {
                }

                @Override
                public void onActivityStopped(Activity activity) {
                }

                @Override
                public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
                }

                @Override
                public void onActivityDestroyed(Activity activity) {
                }
            });
        } catch (Throwable ex) {
            Logger.printException(() -> "ReelsButtons install failed", ex);
        }
    }

    /** One side bar: the reel's ⋯ and like buttons. */
    private static final class SideBar {
        final WeakReference<View> more;
        final WeakReference<View> like;

        SideBar(View more, View like) {
            this.more = new WeakReference<>(more);
            this.like = new WeakReference<>(like);
        }
    }

    private static void attach(Activity activity) {
        try {
            View decor = activity.getWindow().getDecorView();
            FrameLayout content = activity.findViewById(android.R.id.content);
            if (content == null || content.findViewWithTag(COLUMN_TAG) != null) return;

            List<SideBar> sideBars = new ArrayList<>();
            SideBar[] shown = new SideBar[1];
            LinearLayout column = new LinearLayout(activity);
            column.setTag(COLUMN_TAG);
            column.setOrientation(LinearLayout.VERTICAL);
            column.setVisibility(View.GONE);
            column.addView(button(activity, "instagram_thumb_up_outline_24", "Interested", () -> request(INTERESTED, shown[0])));
            column.addView(button(activity, "instagram_thumb_down_outline_24", "Not interested", () -> request(NOT_INTERESTED, shown[0])));
            content.addView(column, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            ViewTreeObserver observer = decor.getViewTreeObserver();
            observer.addOnGlobalLayoutListener(() -> {
                sideBars.clear();
                collect(decor, sideBars);
            });
            observer.addOnPreDrawListener(() -> {
                shown[0] = place(content, column, sideBars);
                return true;
            });
        } catch (Throwable ex) {
            Logger.printException(() -> "ReelsButtons attach failed", ex);
        }
    }

    private static ImageView button(Activity activity, String drawable, String description, Runnable action) {
        int size = dp(activity, 44);
        int padding = dp(activity, 9);
        ImageView view = new ImageView(activity);
        int id = activity.getResources().getIdentifier(drawable, "drawable", activity.getPackageName());
        if (id != 0) view.setImageResource(id);
        view.setColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);
        view.setPadding(padding, padding, padding, padding);
        view.setContentDescription(description);
        view.setLayoutParams(new LinearLayout.LayoutParams(size, size));
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private static int dp(Activity activity, int value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, activity.getResources().getDisplayMetrics());
    }

    /** Every Reels side bar in the window: a ⋯ button and the like button beside it. */
    private static void collect(View root, List<SideBar> sideBars) {
        List<View> mores = new ArrayList<>();
        findTagged(root, MORE_TAG, moreTagId(), mores);
        for (View more : mores) {
            // The like button is in the same side bar: the nearest ancestor holding one.
            View parent = (View) more.getParent();
            View like = null;
            while (parent instanceof ViewGroup && like == null) {
                like = parent.findViewWithTag(LIKE_TAG);
                parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
            }
            if (like != null) sideBars.add(new SideBar(more, like));
        }
    }

    private static void findTagged(View view, String tag, int tagId, List<View> found) {
        if (view.getVisibility() != View.VISIBLE) return;
        Object viewTag = view.getTag();
        if (tag.equals(viewTag) || (tagId != 0 && viewTag instanceof Integer && (Integer) viewTag == tagId)) found.add(view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) findTagged(group.getChildAt(i), tag, tagId, found);
        }
    }

    /** Puts the column above the like button of the side bar most on screen, or hides it. */
    private static SideBar place(FrameLayout content, LinearLayout column, List<SideBar> sideBars) {
        SideBar best = null;
        int bestVisible = 0;
        Rect rect = new Rect();
        for (SideBar sideBar : sideBars) {
            View like = sideBar.like.get();
            if (like == null || !like.isShown() || !like.getGlobalVisibleRect(rect)) continue;
            if (rect.height() > bestVisible) {
                bestVisible = rect.height();
                best = sideBar;
            }
        }
        View like = best == null ? null : best.like.get();
        View sheet = openSheet.get();
        if (sheet != null && sheet.isAttachedToWindow() && sheet.isShown()) like = null;
        if (like == null) {
            if (column.getVisibility() != View.GONE) column.setVisibility(View.GONE);
            return null;
        }
        int[] likeAt = new int[2];
        int[] contentAt = new int[2];
        like.getLocationOnScreen(likeAt);
        content.getLocationOnScreen(contentAt);
        int width = column.getWidth() > 0 ? column.getWidth() : column.getMeasuredWidth();
        int height = column.getHeight() > 0 ? column.getHeight() : column.getMeasuredHeight();
        float x = likeAt[0] - contentAt[0] + (like.getWidth() - width) / 2f;
        float y = likeAt[1] - contentAt[1] - height;
        if (column.getTranslationX() != x) column.setTranslationX(x);
        if (column.getTranslationY() != y) column.setTranslationY(y);
        if (column.getVisibility() != View.VISIBLE) column.setVisibility(View.VISIBLE);
        return best;
    }

    private static void request(String action, SideBar sideBar) {
        View more = sideBar == null ? null : sideBar.more.get();
        if (more == null) return;
        pendingAction = action;
        pendingSince = SystemClock.uptimeMillis();
        tap(more);
    }

    // endregion

    // region Menu

    /** Injection point: the ⋯ menu's options helper, bound to the reel whose menu is being built. */
    public static void onMenuHelper(Object helper) {
        menuHelper = new WeakReference<>(helper);
    }

    /** Injection point: a bottom sheet's view was created. Runs the requested option of a ⋯ menu. */
    public static void onActionSheetCreated(Object fragment) {
        try {
            Method getView = fragment.getClass().getMethod("getView");
            View root = (View) getView.invoke(fragment);
            if (root == null) return;
            openSheet = new WeakReference<>(root);

            String action = pendingAction;
            if (action == null) return;
            pendingAction = null;
            if (SystemClock.uptimeMillis() - pendingSince > PENDING_TIMEOUT_MS) return;

            root.setAlpha(0f);
            root.post(() -> {
                try {
                    Object helper = menuHelper.get();
                    if (helper != null) {
                        Class<?> optionClass = Class.forName(OPTION_CLASS, false, helper.getClass().getClassLoader());
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        Object option = Enum.valueOf((Class) optionClass, action);
                        Method handler = helper.getClass().getDeclaredMethod(optionHandler(), optionClass);
                        handler.setAccessible(true);
                        handler.invoke(helper, option);
                    }
                } catch (Throwable ex) {
                    Logger.printException(() -> "ReelsButtons could not run " + action, ex);
                }
                try {
                    fragment.getClass().getMethod("dismissAllowingStateLoss").invoke(fragment);
                } catch (Throwable ignored) {
                }
            });
        } catch (Throwable ex) {
            Logger.printException(() -> "ReelsButtons onActionSheetCreated failed", ex);
        }
    }

    // endregion

    /** A tap at the view's centre, delivered to its window, as a finger would. */
    private static void tap(View view) {
        int[] at = new int[2];
        view.getLocationOnScreen(at);
        float x = at[0] + view.getWidth() / 2f;
        float y = at[1] + view.getHeight() / 2f;
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 50, MotionEvent.ACTION_UP, x, y, 0);
        View root = view.getRootView();
        root.dispatchTouchEvent(down);
        root.dispatchTouchEvent(up);
        down.recycle();
        up.recycle();
    }
}
