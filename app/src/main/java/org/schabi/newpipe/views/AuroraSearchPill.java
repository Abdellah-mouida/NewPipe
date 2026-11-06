package org.schabi.newpipe.views;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.graphics.drawable.DrawableCompat;

import org.schabi.newpipe.R;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;

/** Search entry point styled as the Home feed's prominent search pill. */
public final class AuroraSearchPill extends AppCompatTextView {
    public AuroraSearchPill(@NonNull final Context context) {
        this(context, null);
    }

    public AuroraSearchPill(@NonNull final Context context, @Nullable final AttributeSet attrs) {
        super(context, attrs);
        setText(R.string.aurora_search_hint);
        setTextSize(15);
        setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        setPadding(dp(16), 0, dp(16), 0);
        setMinHeight(dp(52));
        setFocusable(true);
        setClickable(true);
        setContentDescription(getContext().getString(R.string.aurora_search_hint));
        setTextColor(resolveColor(R.attr.card_item_contrast_color, Color.GRAY));

        final GradientDrawable background = new GradientDrawable();
        background.setColor(resolveColor(R.attr.card_item_background_color, Color.WHITE));
        background.setCornerRadius(dp(26));
        background.setStroke(dp(1), resolveColor(R.attr.border_color, 0x33000000));
        setBackground(background);

        final Drawable search = DrawableCompat.wrap(getContext().getDrawable(R.drawable.ic_search).mutate());
        DrawableCompat.setTint(search, resolveColor(androidx.appcompat.R.attr.colorAccent, 0xFF5B3DF5));
        search.setBounds(0, 0, dp(22), dp(22));
        setCompoundDrawables(search, null, null, null);
        setCompoundDrawablePadding(dp(12));
        setOnClickListener(this::openSearch);
    }

    private void openSearch(final View ignored) {
        NavigationHelper.openSearchFragment(((androidx.fragment.app.FragmentActivity) getContext())
                        .getSupportFragmentManager(),
                ServiceHelper.getSelectedServiceId(getContext()), "");
    }

    private int resolveColor(final int attr, final int fallback) {
        final android.util.TypedValue value = new android.util.TypedValue();
        return getContext().getTheme().resolveAttribute(attr, value, true) ? value.data : fallback;
    }

    private int dp(final int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
