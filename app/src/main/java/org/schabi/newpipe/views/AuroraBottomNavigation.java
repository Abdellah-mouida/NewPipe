package org.schabi.newpipe.views;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.schabi.newpipe.R;
import org.schabi.newpipe.fragments.MainFragment;
import org.schabi.newpipe.local.feed.FeedFragment;
import org.schabi.newpipe.local.subscription.SubscriptionFragment;
import org.schabi.newpipe.util.NavigationHelper;

/** Premium, accessible bottom navigation for the app's primary destinations. */
public final class AuroraBottomNavigation extends LinearLayout {
    private static final int ITEM_COUNT = 5;
    private static final int[] LABELS = {
            R.string.aurora_nav_home,
            R.string.aurora_nav_subscriptions,
            R.string.aurora_nav_library,
            R.string.aurora_nav_downloads,
            R.string.aurora_nav_more
    };
    private static final int[] ICONS = {
            R.drawable.ic_tv,
            R.drawable.ic_subscriptions,
            R.drawable.ic_bookmark,
            R.drawable.ic_file_download,
            R.drawable.ic_settings
    };

    private final TextView[] items = new TextView[ITEM_COUNT];
    private int selectedPosition;
    private FragmentManager registeredManager;
    private final FragmentManager.FragmentLifecycleCallbacks fragmentCallbacks =
            new FragmentManager.FragmentLifecycleCallbacks() {
                @Override
                public void onFragmentResumed(@NonNull final FragmentManager fm,
                                              @NonNull final Fragment fragment) {
                    if (fragment.getId() != R.id.fragment_holder) {
                        return;
                    }
                    if (fragment instanceof MainFragment) {
                        setVisibility(VISIBLE);
                        select(0);
                    } else {
                        setVisibility(GONE);
                        if (fragment instanceof SubscriptionFragment
                                || fragment instanceof FeedFragment) {
                            select(1);
                        } else {
                            select(-1);
                        }
                    }
                }
            };

    public AuroraBottomNavigation(@NonNull final Context context) {
        this(context, null);
    }

    public AuroraBottomNavigation(@NonNull final Context context,
                                  @Nullable final AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    private void initialize() {
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER);
        setPadding(dp(6), dp(4), dp(6), dp(4));
        setElevation(dp(8));
        final GradientDrawable background = new GradientDrawable();
        background.setColor(resolveColor(R.attr.card_item_background_color, Color.BLACK));
        background.setCornerRadius(dp(32));
        background.setStroke(dp(1), resolveColor(R.attr.border_color, 0x33FFFFFF));
        setBackground(background);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);

        for (int i = 0; i < ITEM_COUNT; i++) {
            final int position = i;
            final TextView item = new TextView(getContext());
            item.setGravity(Gravity.CENTER);
            item.setOrientation(VERTICAL);
            item.setText(LABELS[i]);
            item.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            item.setTypeface(item.getTypeface(), android.graphics.Typeface.MEDIUM);
            item.setCompoundDrawablePadding(dp(1));
            item.setMinWidth(dp(48));
            item.setMinHeight(dp(48));
            item.setPadding(dp(2), dp(1), dp(2), dp(1));
            item.setFocusable(true);
            item.setClickable(true);
            item.setBackgroundResource(R.drawable.aurora_nav_item_ripple);
            item.setContentDescription(getContext().getString(LABELS[i]));
            final Drawable icon = DrawableCompat.wrap(getContext().getDrawable(ICONS[i]).mutate());
            DrawableCompat.setTint(icon, resolveColor(R.attr.card_item_contrast_color, Color.GRAY));
            item.setCompoundDrawablesWithIntrinsicBounds(null, icon, null, null);
            item.setOnClickListener(v -> navigate(position));
            items[i] = item;
            addView(item, new LayoutParams(0, LayoutParams.MATCH_PARENT, 1));
        }
        select(0);
    }

    private void navigate(final int position) {
        if (!(getContext() instanceof AppCompatActivity)) {
            return;
        }
        final AppCompatActivity activity = (AppCompatActivity) getContext();
        select(position);
        switch (position) {
            case 0:
                NavigationHelper.gotoMainFragment(activity.getSupportFragmentManager());
                break;
            case 1:
                NavigationHelper.openSubscriptionFragment(activity.getSupportFragmentManager());
                break;
            case 2:
                NavigationHelper.openBookmarksFragment(activity.getSupportFragmentManager());
                break;
            case 3:
                if (activity instanceof org.schabi.newpipe.MainActivity) {
                    NavigationHelper.openDownloads((org.schabi.newpipe.MainActivity) activity);
                }
                break;
            case 4:
                final View drawer = activity.findViewById(R.id.drawer_layout);
                if (drawer instanceof DrawerLayout) {
                    ((DrawerLayout) drawer).openDrawer(GravityCompat.START);
                }
                break;
            default:
                break;
        }
    }

    private void select(final int position) {
        if (position >= 0) {
            selectedPosition = position;
        }
        final int accent = resolveColor(androidx.appcompat.R.attr.colorAccent, 0xFF8B7BFF);
        final int inactive = resolveColor(R.attr.card_item_contrast_color, 0xFF9CA3B4);
        for (int i = 0; i < items.length; i++) {
            final boolean selected = i == selectedPosition;
            final TextView item = items[i];
            if (item == null) {
                continue;
            }
            final int color = selected ? accent : inactive;
            item.setTextColor(color);
            item.setSelected(selected);
            item.setAccessibilityDelegate(new View.AccessibilityDelegate() {
                @Override
                public void onInitializeAccessibilityNodeInfo(final View host,
                        final AccessibilityNodeInfo info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setSelected(selected);
                    info.setClassName("android.widget.Button");
                }
            });
            final Drawable[] drawables = item.getCompoundDrawables();
            if (drawables[1] != null) {
                DrawableCompat.setTint(drawables[1], color);
            }
            item.setScaleX(selected ? 1.04f : 1f);
            item.setScaleY(selected ? 1.04f : 1f);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getContext() instanceof AppCompatActivity) {
            registeredManager = ((AppCompatActivity) getContext()).getSupportFragmentManager();
            registeredManager.registerFragmentLifecycleCallbacks(fragmentCallbacks, false);
            final Fragment current = registeredManager.findFragmentById(R.id.fragment_holder);
            if (current instanceof MainFragment) {
                setVisibility(VISIBLE);
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        if (registeredManager != null) {
            registeredManager.unregisterFragmentLifecycleCallbacks(fragmentCallbacks);
            registeredManager = null;
        }
        super.onDetachedFromWindow();
    }

    private int resolveColor(final int attr, final int fallback) {
        final TypedValue value = new TypedValue();
        return getContext().getTheme().resolveAttribute(attr, value, true)
                ? value.data : fallback;
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
