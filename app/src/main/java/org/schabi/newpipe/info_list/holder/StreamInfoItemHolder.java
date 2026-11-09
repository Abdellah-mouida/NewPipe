package org.schabi.newpipe.info_list.holder;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.schabi.newpipe.R;
import org.schabi.newpipe.extractor.InfoItem;
import org.schabi.newpipe.extractor.stream.StreamInfoItem;
import org.schabi.newpipe.extractor.stream.StreamType;
import org.schabi.newpipe.info_list.InfoItemBuilder;
import org.schabi.newpipe.local.history.HistoryRecordManager;
import org.schabi.newpipe.util.Localization;

/*
 * Created by Christian Schabesberger on 01.08.16.
 * <p>
 * Copyright (C) Christian Schabesberger 2016 <chris.schabesberger@mailbox.org>
 * StreamInfoItemHolder.java is part of NewPipe.
 * </p>
 * <p>
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * </p>
 * <p>
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * </p?
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with NewPipe. If not, see <http://www.gnu.org/licenses/>.
 * </p>
 */

public class StreamInfoItemHolder extends StreamMiniInfoItemHolder {
    public final TextView itemAdditionalDetails;
    private final TextView itemViewCountText;
    private final View itemViewCountIcon;

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final ViewGroup parent) {
        this(infoItemBuilder, R.layout.list_stream_item, parent);
    }

    public StreamInfoItemHolder(final InfoItemBuilder infoItemBuilder, final int layoutId,
                                final ViewGroup parent) {
        super(infoItemBuilder, layoutId, parent);
        itemAdditionalDetails = itemView.findViewById(R.id.itemAdditionalDetails);
        itemViewCountText = itemView.findViewById(R.id.itemViewCountText);
        itemViewCountIcon = itemView.findViewById(R.id.itemViewCountIcon);
    }

    @Override
    public void updateFromItem(final InfoItem infoItem,
                               final HistoryRecordManager historyRecordManager) {
        super.updateFromItem(infoItem, historyRecordManager);

        if (!(infoItem instanceof StreamInfoItem)) {
            return;
        }
        final StreamInfoItem item = (StreamInfoItem) infoItem;

        final String viewCount = getViewCountLine(item);
        if (itemViewCountText != null) {
            final boolean hasViewCount = !TextUtils.isEmpty(viewCount);
            itemViewCountText.setVisibility(hasViewCount ? View.VISIBLE : View.GONE);
            itemViewCountText.setText(viewCount);
        }
        if (itemViewCountIcon != null) {
            itemViewCountIcon.setVisibility(
                    TextUtils.isEmpty(viewCount) ? View.GONE : View.VISIBLE);
        }

        final String uploadDate = Localization.relativeTimeOrTextual(itemBuilder.getContext(),
                item.getUploadDate(), item.getTextualUploadDate());
        itemAdditionalDetails.setText(uploadDate);
        itemAdditionalDetails.setVisibility(
                TextUtils.isEmpty(uploadDate) ? View.GONE : View.VISIBLE);
    }

    private String getViewCountLine(final StreamInfoItem infoItem) {
        if (infoItem.getViewCount() < 0) {
            return "";
        }
        if (infoItem.getStreamType().equals(StreamType.AUDIO_LIVE_STREAM)) {
            return Localization.listeningCount(itemBuilder.getContext(),
                    infoItem.getViewCount());
        }
        if (infoItem.getStreamType().equals(StreamType.LIVE_STREAM)) {
            return Localization.shortWatchingCount(itemBuilder.getContext(),
                    infoItem.getViewCount());
        }
        return Localization.shortViewCount(itemBuilder.getContext(), infoItem.getViewCount());
    }
}
