package org.schabi.newpipe.fragments;

import static android.widget.RelativeLayout.ABOVE;
import static android.widget.RelativeLayout.ALIGN_PARENT_BOTTOM;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapterMenuWorkaround;
import androidx.preference.PreferenceManager;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.databinding.FragmentMainBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.ErrorUtil;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.playlist.LocalPlaylistFragment;
import org.schabi.newpipe.settings.tabs.Tab;
import org.schabi.newpipe.settings.tabs.TabsManager;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.ServiceHelper;
import org.schabi.newpipe.util.ThemeHelper;
import org.schabi.newpipe.views.ScrollableTabLayout;

import java.util.ArrayList;
import java.util.List;

public class MainFragment extends BaseFragment implements TabLayout.OnTabSelectedListener {
    private FragmentMainBinding binding;
    private SelectedTabsPagerAdapter pagerAdapter;

    private final List<Tab> tabsList = new ArrayList<>();
    private TabsManager tabsManager;

    private boolean hasTabsChanged = false;

    private SharedPreferences prefs;
    private boolean youtubeRestrictedModeEnabled;
    private String youtubeRestrictedModeEnabledKey;

    /*//////////////////////////////////////////////////////////////////////////
    // Fragment's LifeCycle
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        tabsManager = TabsManager.getManager(activity);
        tabsManager.setSavedTabsListener(() -> {
            if (DEBUG) {
                Log.d(TAG, "TabsManager.SavedTabsChangeListener: "
                        + "onTabsChanged called, isResumed = " + isResumed());
            }
            if (isResumed()) {
                setupTabs();
            } else {
                hasTabsChanged = true;
            }
        });

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());
        youtubeRestrictedModeEnabledKey = getString(R.string.youtube_restricted_mode_enabled);
        youtubeRestrictedModeEnabled = prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             @Nullable final Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        binding = FragmentMainBinding.bind(rootView);

        binding.mainTabLayout.setupWithViewPager(binding.pager);
        binding.mainTabLayout.addOnTabSelectedListener(this);
        binding.pager.setPageTransformer(true, (page, position) -> {
            final float distance = Math.min(1f, Math.abs(position));
            page.setAlpha(1f - (distance * 0.18f));
            page.setTranslationY(dp(10) * distance);
            page.setScaleX(1f - (distance * 0.02f));
            page.setScaleY(1f - (distance * 0.02f));
        });

        setupTabs();
        updateTabLayoutPosition();
    }

    @Override
    public void onResume() {
        super.onResume();

        final boolean newYoutubeRestrictedModeEnabled =
                prefs.getBoolean(youtubeRestrictedModeEnabledKey, false);
        if (youtubeRestrictedModeEnabled != newYoutubeRestrictedModeEnabled || hasTabsChanged) {
            youtubeRestrictedModeEnabled = newYoutubeRestrictedModeEnabled;
            setupTabs();
        }

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        tabsManager.unsetSavedTabsListener();
        if (binding != null) {
            binding.pager.setAdapter(null);
            binding = null;
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Menu
    //////////////////////////////////////////////////////////////////////////*/

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu,
                                    @NonNull final MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        if (DEBUG) {
            Log.d(TAG, "onCreateOptionsMenu() called with: "
                    + "menu = [" + menu + "], inflater = [" + inflater + "]");
        }
        inflater.inflate(R.menu.menu_main_fragment, menu);

        final ActionBar supportActionBar = activity.getSupportActionBar();
        if (supportActionBar != null) {
            supportActionBar.setDisplayHomeAsUpEnabled(false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            try {
                NavigationHelper.openSearchFragment(getFM(),
                        ServiceHelper.getSelectedServiceId(activity), "");
            } catch (final Exception e) {
                ErrorUtil.showUiErrorSnackbar(this, "Opening search fragment", e);
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Tabs
    //////////////////////////////////////////////////////////////////////////*/

    private void setupTabs() {
        tabsList.clear();
        tabsList.addAll(tabsManager.getTabs());

        if (pagerAdapter == null || !pagerAdapter.sameTabs(tabsList)) {
            pagerAdapter = new SelectedTabsPagerAdapter(requireContext(),
                    getChildFragmentManager(), tabsList);
        }

        binding.pager.setAdapter(null);
        binding.pager.setAdapter(pagerAdapter);

        updateTabsIconAndDescription();
        updateTitleForTab(binding.pager.getCurrentItem());

        hasTabsChanged = false;
    }

    private void updateTabsIconAndDescription() {
        for (int i = 0; i < tabsList.size(); i++) {
            final TabLayout.Tab tabToSet = binding.mainTabLayout.getTabAt(i);
            if (tabToSet != null) {
                final Tab tab = tabsList.get(i);
                final View tabView = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_bottom_nav_tab, null, false);
                final ImageView icon = tabView.findViewById(R.id.bottom_nav_icon);
                final TextView label = tabView.findViewById(R.id.bottom_nav_label);
                icon.setImageResource(tab.getTabIconRes(requireContext()));
                label.setText(tab.getTabName(requireContext()));
                tabToSet.setCustomView(tabView);
                tabToSet.setContentDescription(tab.getTabName(requireContext()));
                applyTabAppearance(tabToSet, i == binding.pager.getCurrentItem(), false);
            }
        }
    }

    private void applyTabAppearance(final TabLayout.Tab tab, final boolean selected,
                                    final boolean animate) {
        final View tabView = tab.getCustomView();
        if (tabView == null) {
            return;
        }
        final ImageView icon = tabView.findViewById(R.id.bottom_nav_icon);
        final TextView label = tabView.findViewById(R.id.bottom_nav_label);
        if (icon == null || label == null) {
            return;
        }

        final int tint = ThemeHelper.resolveColorFromAttr(requireContext(), selected
                ? R.attr.npColorOnAccent : R.attr.npColorOnSurfaceVariant);
        icon.setImageTintList(ColorStateList.valueOf(tint));
        label.setTextColor(tint);
        tabView.setSelected(selected);
        icon.setSelected(selected);
        label.setSelected(selected);

        tabView.animate().cancel();
        if (animate && selected) {
            tabView.setScaleX(0.88f);
            tabView.setScaleY(0.88f);
            tabView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(220L)
                    .setInterpolator(new OvershootInterpolator(1.15f))
                    .start();
        } else {
            tabView.setScaleX(selected ? 1f : 0.96f);
            tabView.setScaleY(selected ? 1f : 0.96f);
        }
    }

    private void updateTitleForTab(final int tabPosition) {
        setTitle(tabsList.get(tabPosition).getTabName(requireContext()));
    }

    public void commitPlaylistTabs() {
        pagerAdapter.getLocalPlaylistFragments()
                .stream()
                .forEach(LocalPlaylistFragment::saveImmediate);
    }

    private void updateTabLayoutPosition() {
        final ScrollableTabLayout tabLayout = binding.mainTabLayout;
        final ViewPager viewPager = binding.pager;
        final var tabParams = (RelativeLayout.LayoutParams) tabLayout.getLayoutParams();
        final var pagerParams = (RelativeLayout.LayoutParams) viewPager.getLayoutParams();

        tabParams.addRule(ALIGN_PARENT_BOTTOM);
        tabParams.removeRule(RelativeLayout.ALIGN_PARENT_TOP);
        pagerParams.addRule(ABOVE, R.id.main_tab_layout);
        pagerParams.removeRule(RelativeLayout.BELOW);
        pagerParams.bottomMargin = dp(14);

        tabLayout.setLayoutParams(tabParams);
        viewPager.setLayoutParams(pagerParams);
        tabLayout.setBackgroundResource(R.drawable.np_bottom_nav_background);
        tabLayout.setSelectedTabIndicatorGravity(TabLayout.INDICATOR_GRAVITY_CENTER);
        tabLayout.setTabIndicatorFullWidth(false);

        @ColorInt final int accent = ThemeHelper.resolveColorFromAttr(
                requireContext(), R.attr.npColorAccent);
        tabLayout.setTabRippleColor(ColorStateList.valueOf(accent).withAlpha(28));
        tabLayout.setSelectedTabIndicatorColor(accent);
    }

    private int dp(final int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onTabSelected(final TabLayout.Tab selectedTab) {
        if (DEBUG) {
            Log.d(TAG, "onTabSelected() called with: selectedTab = [" + selectedTab + "]");
        }
        updateTitleForTab(selectedTab.getPosition());
        applyTabAppearance(selectedTab, true, true);
    }

    @Override
    public void onTabUnselected(final TabLayout.Tab tab) {
        applyTabAppearance(tab, false, false);
    }

    @Override
    public void onTabReselected(final TabLayout.Tab tab) {
        if (DEBUG) {
            Log.d(TAG, "onTabReselected() called with: tab = [" + tab + "]");
        }
        updateTitleForTab(tab.getPosition());
        applyTabAppearance(tab, true, true);
    }

    public static final class SelectedTabsPagerAdapter
            extends FragmentStatePagerAdapterMenuWorkaround {
        private final Context context;
        private final List<Tab> internalTabsList;
        /**
         * Keep reference to LocalPlaylistFragments, because their data can be modified by the user
         * during runtime and changes are not committed immediately. However, in some cases,
         * the changes need to be committed immediately by calling
         * {@link LocalPlaylistFragment#saveImmediate()}.
         * The fragments are removed when {@link LocalPlaylistFragment#onDestroy()} is called.
         */
        private final List<LocalPlaylistFragment> localPlaylistFragments = new ArrayList<>();

        private SelectedTabsPagerAdapter(final Context context,
                                         final FragmentManager fragmentManager,
                                         final List<Tab> tabsList) {
            super(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.context = context;
            this.internalTabsList = new ArrayList<>(tabsList);
        }

        @NonNull
        @Override
        public Fragment getItem(final int position) {
            final Tab tab = internalTabsList.get(position);

            final Fragment fragment;
            try {
                fragment = tab.getFragment(context);
            } catch (final Throwable t) {
                return new BlankFragment(new ErrorInfo(t, UserAction.GETTING_MAIN_SCREEN_TAB,
                        "Tab " + tab.getClass().getSimpleName() + ":" + tab.getTabName(context)));
            }

            if (fragment instanceof BaseFragment) {
                ((BaseFragment) fragment).useAsFrontPage(true);
            }

            if (fragment instanceof LocalPlaylistFragment) {
                localPlaylistFragments.add((LocalPlaylistFragment) fragment);
            }

            return fragment;
        }

        public List<LocalPlaylistFragment> getLocalPlaylistFragments() {
            return localPlaylistFragments;
        }

        @Override
        public int getItemPosition(@NonNull final Object object) {
            // Causes adapter to reload all Fragments when
            // notifyDataSetChanged is called
            return POSITION_NONE;
        }

        @Override
        public int getCount() {
            return internalTabsList.size();
        }

        public boolean sameTabs(final List<Tab> tabsToCompare) {
            return internalTabsList.equals(tabsToCompare);
        }
    }
}
