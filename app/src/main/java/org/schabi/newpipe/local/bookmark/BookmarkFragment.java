package org.schabi.newpipe.local.bookmark;

import static org.schabi.newpipe.local.bookmark.MergedPlaylistManager.getMergedOrderedPlaylists;
import static org.schabi.newpipe.util.ThemeHelper.shouldUseGridLayout;

import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.widget.Toast;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.evernote.android.state.State;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import org.schabi.newpipe.NewPipeDatabase;
import org.schabi.newpipe.R;
import org.schabi.newpipe.database.AppDatabase;
import org.schabi.newpipe.database.LocalItem;
import org.schabi.newpipe.database.playlist.PlaylistLocalItem;
import org.schabi.newpipe.database.playlist.PlaylistMetadataEntry;
import org.schabi.newpipe.database.playlist.PlaylistStreamEntry;
import org.schabi.newpipe.database.playlist.model.PlaylistRemoteEntity;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.Page;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.databinding.DialogEditTextBinding;
import org.schabi.newpipe.error.ErrorInfo;
import org.schabi.newpipe.error.UserAction;
import org.schabi.newpipe.local.BaseLocalListFragment;
import org.schabi.newpipe.local.holder.LocalBookmarkPlaylistItemHolder;
import org.schabi.newpipe.local.holder.RemoteBookmarkPlaylistItemHolder;
import org.schabi.newpipe.local.playlist.LocalPlaylistManager;
import org.schabi.newpipe.local.playlist.RemotePlaylistManager;
import org.schabi.newpipe.player.playqueue.SinglePlayQueue;
import org.schabi.newpipe.util.ExtractorHelper;
import org.schabi.newpipe.util.NavigationHelper;
import org.schabi.newpipe.util.OnClickGesture;
import org.schabi.newpipe.util.debounce.DebounceSavable;
import org.schabi.newpipe.util.debounce.DebounceSaver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public final class BookmarkFragment extends BaseLocalListFragment<List<PlaylistLocalItem>, Void>
        implements DebounceSavable {

    private static final int MINIMUM_INITIAL_DRAG_VELOCITY = 12;
    @State
    Parcelable itemsListState;

    private Subscription databaseSubscription;
    private CompositeDisposable disposables = new CompositeDisposable();
    private LocalPlaylistManager localPlaylistManager;
    private RemotePlaylistManager remotePlaylistManager;
    private ItemTouchHelper itemTouchHelper;

    /* Have the bookmarked playlists been fully loaded from db */
    private AtomicBoolean isLoadingComplete;

    /* Gives enough time to avoid interrupting user sorting operations */
    @Nullable
    private DebounceSaver debounceSaver;

    private List<Pair<Long, LocalItem.LocalItemType>> deletedItems;
    @Nullable
    private View shuffleBookmarksButton;
    @Nullable
    private View shuffleBookmarksProgress;
    private boolean isShufflingBookmarks;

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Creation
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (activity == null) {
            return;
        }
        final AppDatabase database = NewPipeDatabase.getInstance(activity);
        localPlaylistManager = new LocalPlaylistManager(database);
        remotePlaylistManager = new RemotePlaylistManager(database);
        disposables = new CompositeDisposable();

        isLoadingComplete = new AtomicBoolean();
        debounceSaver = new DebounceSaver(3000, this);

        deletedItems = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             @Nullable final ViewGroup container,
                             final Bundle savedInstanceState) {

        if (!useAsFrontPage) {
            setTitle(activity.getString(R.string.tab_bookmarks));
        }
        return inflater.inflate(R.layout.fragment_bookmarks, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (activity != null) {
            setTitle(activity.getString(R.string.tab_bookmarks));
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Views
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void initViews(final View rootView, final Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        itemListAdapter.setUseItemHandle(true);
        shuffleBookmarksButton = rootView.findViewById(R.id.bookmarks_shuffle_all);
        shuffleBookmarksProgress = rootView.findViewById(R.id.bookmarks_shuffle_progress);
        shuffleBookmarksButton.setVisibility(View.GONE);
    }

    @Override
    protected void initListeners() {
        super.initListeners();

        if (shuffleBookmarksButton != null) {
            shuffleBookmarksButton.setOnClickListener(view -> shuffleAllBookmarkedPlaylists());
        }

        itemTouchHelper = new ItemTouchHelper(getItemTouchCallback());
        itemTouchHelper.attachToRecyclerView(itemsList);

        itemListAdapter.setSelectedListener(new OnClickGesture<>() {
            @Override
            public void selected(final LocalItem selectedItem) {
                final FragmentManager fragmentManager = getFM();

                if (selectedItem instanceof PlaylistMetadataEntry) {
                    final PlaylistMetadataEntry entry = ((PlaylistMetadataEntry) selectedItem);
                    NavigationHelper.openLocalPlaylistFragment(fragmentManager, entry.getUid(),
                            entry.getOrderingName());

                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    final PlaylistRemoteEntity entry = ((PlaylistRemoteEntity) selectedItem);
                    NavigationHelper.openPlaylistFragment(
                            fragmentManager,
                            entry.getServiceId(),
                            entry.getUrl(),
                            entry.getOrderingName());
                }
            }

            @Override
            public void held(final LocalItem selectedItem) {
                if (selectedItem instanceof PlaylistMetadataEntry) {
                    showLocalDialog((PlaylistMetadataEntry) selectedItem);
                } else if (selectedItem instanceof PlaylistRemoteEntity) {
                    showRemoteDeleteDialog((PlaylistRemoteEntity) selectedItem);
                }
            }

            @Override
            public void drag(final LocalItem selectedItem,
                             final RecyclerView.ViewHolder viewHolder) {
                if (itemTouchHelper != null) {
                    itemTouchHelper.startDrag(viewHolder);
                }
            }
        });
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Loading
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void startLoading(final boolean forceLoad) {
        super.startLoading(forceLoad);

        if (debounceSaver != null) {
            disposables.add(debounceSaver.getDebouncedSaver());
            debounceSaver.setNoChangesToSave();
        }
        isLoadingComplete.set(false);

        getMergedOrderedPlaylists(localPlaylistManager, remotePlaylistManager)
                .onBackpressureLatest()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(getPlaylistsSubscriber());
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment LifeCycle - Destruction
    ///////////////////////////////////////////////////////////////////////////

    @Override
    public void onPause() {
        super.onPause();
        itemsListState = itemsList.getLayoutManager().onSaveInstanceState();

        // Save on exit
        saveImmediate();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (disposables != null) {
            disposables.clear();
        }
        if (databaseSubscription != null) {
            databaseSubscription.cancel();
        }

        databaseSubscription = null;
        itemTouchHelper = null;
        shuffleBookmarksButton = null;
        shuffleBookmarksProgress = null;
        isShufflingBookmarks = false;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (debounceSaver != null) {
            debounceSaver.getDebouncedSaveSignal().onComplete();
        }
        if (disposables != null) {
            disposables.dispose();
        }

        debounceSaver = null;
        disposables = null;
        localPlaylistManager = null;
        remotePlaylistManager = null;
        itemsListState = null;

        isLoadingComplete = null;
        deletedItems = null;
    }

    ///////////////////////////////////////////////////////////////////////////
    // Subscriptions Loader
    ///////////////////////////////////////////////////////////////////////////

    private Subscriber<List<PlaylistLocalItem>> getPlaylistsSubscriber() {
        return new Subscriber<>() {
            @Override
            public void onSubscribe(final Subscription s) {
                showLoading();
                isLoadingComplete.set(false);

                if (databaseSubscription != null) {
                    databaseSubscription.cancel();
                }
                databaseSubscription = s;
                databaseSubscription.request(1);
            }

            @Override
            public void onNext(final List<PlaylistLocalItem> subscriptions) {
                if (debounceSaver == null || !debounceSaver.getIsModified()) {
                    handleResult(subscriptions);
                    isLoadingComplete.set(true);
                }
                if (databaseSubscription != null) {
                    databaseSubscription.request(1);
                }
            }

            @Override
            public void onError(final Throwable exception) {
                showError(new ErrorInfo(exception,
                        UserAction.REQUESTED_BOOKMARK, "Loading playlists"));
            }

            @Override
            public void onComplete() {
            }
        };
    }

    @Override
    public void handleResult(@NonNull final List<PlaylistLocalItem> result) {
        super.handleResult(result);

        itemListAdapter.clearStreamItemList();

        if (shuffleBookmarksButton != null) {
            shuffleBookmarksButton.setVisibility(result.isEmpty() ? View.GONE : View.VISIBLE);
        }
        if (result.isEmpty()) {
            showEmptyState();
            return;
        }

        itemListAdapter.addItems(result);
        if (itemsListState != null) {
            itemsList.getLayoutManager().onRestoreInstanceState(itemsListState);
            itemsListState = null;
        }
        hideLoading();
    }

    /**
     * Load all streams from the bookmarked local and remote playlists, then start one shuffled
     * queue. Remote playlists are paged through completely so later pages are included too.
     */
    private void shuffleAllBookmarkedPlaylists() {
        if (activity == null || itemListAdapter == null || localPlaylistManager == null
                || isLoadingComplete == null || !isLoadingComplete.get()
                || isShufflingBookmarks) {
            return;
        }

        setShuffleLoading(true);
        disposables.add(loadAllBookmarkedStreams()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(streams -> {
                    setShuffleLoading(false);
                    if (activity == null) {
                        return;
                    }
                    if (streams.isEmpty()) {
                        Toast.makeText(activity, R.string.no_bookmarked_streams_to_shuffle,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Randomize before creating the queue so the first stream is random too.
                    Collections.shuffle(streams);
                    final SinglePlayQueue queue = new SinglePlayQueue(streams, 0);
                    // Use native queue shuffle so the player can restore the original order.
                    queue.shuffle();
                    NavigationHelper.playOnMainPlayer(activity, queue);
                }, throwable -> {
                    setShuffleLoading(false);
                    if (activity != null) {
                        showError(new ErrorInfo(throwable, UserAction.REQUESTED_BOOKMARK,
                                "Shuffling bookmarked playlists"));
                    }
                }));
    }

    private void setShuffleLoading(final boolean loading) {
        isShufflingBookmarks = loading;
        if (shuffleBookmarksButton != null) {
            shuffleBookmarksButton.setEnabled(!loading);
            shuffleBookmarksButton.setAlpha(loading ? 0.65f : 1.0f);
        }
        if (shuffleBookmarksProgress != null) {
            shuffleBookmarksProgress.setVisibility(loading ? View.VISIBLE : View.GONE);
        }
    }

    private Single<List<StreamInfoItem>> loadAllBookmarkedStreams() {
        final List<Single<List<StreamInfoItem>>> playlistLoads = new ArrayList<>();
        for (final LocalItem item : itemListAdapter.getItemsList()) {
            if (item instanceof PlaylistMetadataEntry) {
                playlistLoads.add(localPlaylistManager.getPlaylistStreams(item.getUid())
                        .firstElement()
                        .map(entries -> entries.stream()
                                .map(PlaylistStreamEntry::toStreamInfoItem)
                                .collect(Collectors.toList()))
                        .defaultIfEmpty(new ArrayList<>())
                        .toSingle());
            } else if (item instanceof PlaylistRemoteEntity) {
                final PlaylistRemoteEntity remotePlaylist = (PlaylistRemoteEntity) item;
                final String playlistUrl = remotePlaylist.getUrl();
                if (playlistUrl != null) {
                    playlistLoads.add(loadRemotePlaylistStreams(
                            remotePlaylist.getServiceId(), playlistUrl));
                }
            }
        }

        if (playlistLoads.isEmpty()) {
            return Single.just(new ArrayList<>());
        }

        return Single.concat(playlistLoads)
                .toList()
                .map(playlistStreams -> {
                    final List<StreamInfoItem> allStreams = new ArrayList<>();
                    playlistStreams.forEach(allStreams::addAll);
                    return allStreams;
                });
    }

    private Single<List<StreamInfoItem>> loadRemotePlaylistStreams(
            final int serviceId, @NonNull final String url) {
        return ExtractorHelper.getPlaylistInfo(serviceId, url, false)
                .flatMap(info -> loadMoreRemotePlaylistStreams(
                        serviceId,
                        url,
                        info.getNextPage(),
                        extractStreamItems(info.getRelatedItems())));
    }

    private Single<List<StreamInfoItem>> loadMoreRemotePlaylistStreams(
            final int serviceId,
            @NonNull final String url,
            @Nullable final Page nextPage,
            @NonNull final List<StreamInfoItem> streams) {
        if (!Page.isValid(nextPage)) {
            return Single.just(streams);
        }

        return ExtractorHelper.getMorePlaylistItems(serviceId, url, nextPage)
                .flatMap(page -> {
                    streams.addAll(page.getItems());
                    return loadMoreRemotePlaylistStreams(
                            serviceId, url, page.getNextPage(), streams);
                });
    }

    @NonNull
    private List<StreamInfoItem> extractStreamItems(@NonNull final List<InfoItem> items) {
        return items.stream()
                .filter(StreamInfoItem.class::isInstance)
                .map(StreamInfoItem.class::cast)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    ///////////////////////////////////////////////////////////////////////////
    // Fragment Error Handling
    ///////////////////////////////////////////////////////////////////////////

    @Override
    protected void resetFragment() {
        super.resetFragment();
        if (disposables != null) {
            disposables.clear();
        }
    }

    /*//////////////////////////////////////////////////////////////////////////
    // Playlist Metadata Manipulation
    //////////////////////////////////////////////////////////////////////////*/

    private void changeLocalPlaylistName(final long id, final String name) {
        if (localPlaylistManager == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Updating playlist id=[" + id + "] "
                    + "with new name=[" + name + "] items");
        }

        final Disposable disposable = localPlaylistManager.renamePlaylist(id, name)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(longs -> { /*Do nothing on success*/ }, throwable -> showError(
                        new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK,
                                "Changing playlist name")));
        disposables.add(disposable);
    }

    private void deleteItem(final PlaylistLocalItem item) {
        if (itemListAdapter == null) {
            return;
        }
        itemListAdapter.removeItem(item);

        if (item instanceof PlaylistMetadataEntry) {
            deletedItems.add(new Pair<>(item.getUid(),
                    LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM));
        } else if (item instanceof PlaylistRemoteEntity) {
            deletedItems.add(new Pair<>(item.getUid(),
                    LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM));
        }

        if (debounceSaver != null) {
            debounceSaver.setHasChangesToSave();
            saveImmediate();
        }
    }

    @Override
    public void saveImmediate() {
        if (itemListAdapter == null) {
            return;
        }

        // List must be loaded and modified in order to save
        if (isLoadingComplete == null || debounceSaver == null
                || !isLoadingComplete.get() || !debounceSaver.getIsModified()) {
            return;
        }

        final List<LocalItem> items = itemListAdapter.getItemsList();
        final List<PlaylistMetadataEntry> localItemsUpdate = new ArrayList<>();
        final List<Long> localItemsDeleteUid = new ArrayList<>();
        final List<PlaylistRemoteEntity> remoteItemsUpdate = new ArrayList<>();
        final List<Long> remoteItemsDeleteUid = new ArrayList<>();

        // Calculate display index
        for (int i = 0; i < items.size(); i++) {
            final LocalItem item = items.get(i);

            if (item instanceof PlaylistMetadataEntry
                    && ((PlaylistMetadataEntry) item).getDisplayIndex() != i) {
                ((PlaylistMetadataEntry) item).setDisplayIndex((long) i);
                localItemsUpdate.add((PlaylistMetadataEntry) item);
            } else if (item instanceof PlaylistRemoteEntity
                    && ((PlaylistRemoteEntity) item).getDisplayIndex() != i) {
                ((PlaylistRemoteEntity) item).setDisplayIndex((long) i);
                remoteItemsUpdate.add((PlaylistRemoteEntity) item);
            }
        }

        // Find deleted items
        for (final Pair<Long, LocalItem.LocalItemType> item : deletedItems) {
            if (item.second.equals(LocalItem.LocalItemType.PLAYLIST_LOCAL_ITEM)) {
                localItemsDeleteUid.add(item.first);
            } else if (item.second.equals(LocalItem.LocalItemType.PLAYLIST_REMOTE_ITEM)) {
                remoteItemsDeleteUid.add(item.first);
            }
        }

        deletedItems.clear();

        // 1. Update local playlists
        // 2. Update remote playlists
        // 3. Set NoChangesToSave
        disposables.add(localPlaylistManager.updatePlaylists(localItemsUpdate, localItemsDeleteUid)
                .mergeWith(remotePlaylistManager.updatePlaylists(
                        remoteItemsUpdate, remoteItemsDeleteUid))
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(() -> {
                            if (debounceSaver != null) {
                                debounceSaver.setNoChangesToSave();
                            }
                        },
                        throwable -> showError(new ErrorInfo(throwable,
                                UserAction.REQUESTED_BOOKMARK, "Saving playlist"))
                ));

    }

    private ItemTouchHelper.SimpleCallback getItemTouchCallback() {
        int directions = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        if (shouldUseGridLayout(requireContext())) {
            directions |= ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT;
        }
        return new ItemTouchHelper.SimpleCallback(directions, ItemTouchHelper.ACTION_STATE_IDLE) {
            @Override
            public int interpolateOutOfBoundsScroll(@NonNull final RecyclerView recyclerView,
                                                    final int viewSize,
                                                    final int viewSizeOutOfBounds,
                                                    final int totalSize,
                                                    final long msSinceStartScroll) {
                final int standardSpeed = super.interpolateOutOfBoundsScroll(recyclerView,
                        viewSize, viewSizeOutOfBounds, totalSize, msSinceStartScroll);
                final int minimumAbsVelocity = Math.max(MINIMUM_INITIAL_DRAG_VELOCITY,
                        Math.abs(standardSpeed));
                return minimumAbsVelocity * (int) Math.signum(viewSizeOutOfBounds);
            }

            @Override
            public boolean onMove(@NonNull final RecyclerView recyclerView,
                                  @NonNull final RecyclerView.ViewHolder source,
                                  @NonNull final RecyclerView.ViewHolder target) {

                // Allow swap LocalBookmarkPlaylistItemHolder and RemoteBookmarkPlaylistItemHolder.
                if (itemListAdapter == null
                        || source.getItemViewType() != target.getItemViewType()
                        && !(
                        (
                                (source instanceof LocalBookmarkPlaylistItemHolder)
                                        || (source instanceof RemoteBookmarkPlaylistItemHolder)
                        )
                                && (
                                (target instanceof LocalBookmarkPlaylistItemHolder)
                                        || (target instanceof RemoteBookmarkPlaylistItemHolder)
                        ))
                ) {
                    return false;
                }

                final int sourceIndex = source.getBindingAdapterPosition();
                final int targetIndex = target.getBindingAdapterPosition();
                final boolean isSwapped = itemListAdapter.swapItems(sourceIndex, targetIndex);
                if (isSwapped && debounceSaver != null) {
                    debounceSaver.setHasChangesToSave();
                }
                return isSwapped;
            }

            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean isItemViewSwipeEnabled() {
                return false;
            }

            @Override
            public void onSwiped(@NonNull final RecyclerView.ViewHolder viewHolder,
                                 final int swipeDir) {
                // Do nothing.
            }
        };
    }

    ///////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////

    private void showRemoteDeleteDialog(final PlaylistRemoteEntity item) {
        showDeleteDialog(item.getOrderingName(), item);
    }

    private void showLocalDialog(final PlaylistMetadataEntry selectedItem) {
        final String rename = getString(R.string.rename);
        final String delete = getString(R.string.delete);
        final String unsetThumbnail = getString(R.string.unset_playlist_thumbnail);
        final boolean isThumbnailPermanent = localPlaylistManager
                .getIsPlaylistThumbnailPermanent(selectedItem.getUid());

        final ArrayList<String> items = new ArrayList<>();
        items.add(rename);
        items.add(delete);
        if (isThumbnailPermanent) {
            items.add(unsetThumbnail);
        }

        final DialogInterface.OnClickListener action = (d, index) -> {
            if (items.get(index).equals(rename)) {
                showRenameDialog(selectedItem);
            } else if (items.get(index).equals(delete)) {
                showDeleteDialog(selectedItem.getOrderingName(), selectedItem);
            } else if (isThumbnailPermanent && items.get(index).equals(unsetThumbnail)) {
                final long thumbnailStreamId = localPlaylistManager
                        .getAutomaticPlaylistThumbnailStreamId(selectedItem.getUid());
                localPlaylistManager
                        .changePlaylistThumbnail(selectedItem.getUid(), thumbnailStreamId, false)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe();
            }
        };

        new AlertDialog.Builder(activity)
                .setItems(items.toArray(new String[0]), action)
                .show();
    }

    private void showRenameDialog(final PlaylistMetadataEntry selectedItem) {
        final DialogEditTextBinding dialogBinding =
                DialogEditTextBinding.inflate(getLayoutInflater());
        dialogBinding.dialogEditText.setHint(R.string.name);
        dialogBinding.dialogEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        dialogBinding.dialogEditText.setText(selectedItem.getOrderingName());

        new AlertDialog.Builder(activity)
                .setView(dialogBinding.getRoot())
                .setPositiveButton(R.string.rename_playlist, (dialog, which) ->
                        changeLocalPlaylistName(
                                selectedItem.getUid(),
                                dialogBinding.dialogEditText.getText().toString()))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showDeleteDialog(final String name, final PlaylistLocalItem item) {
        if (activity == null || disposables == null) {
            return;
        }

        new AlertDialog.Builder(activity)
                .setTitle(name)
                .setMessage(R.string.delete_playlist_prompt)
                .setCancelable(true)
                .setPositiveButton(R.string.delete, (dialog, i) -> deleteItem(item))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
