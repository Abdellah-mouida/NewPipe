package org.schabi.newpipe.streams;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.util.Pair;

import androidx.annotation.Nullable;

import org.schabi.newpipe.extractor.Image;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Encodes extractor metadata in standard audio and video container tags. */
public final class MediaMetadata {
    private static final int MAX_ARTWORK_BYTES = 5 * 1024 * 1024;

    private MediaMetadata() {
    }

    public static List<Pair<String, String>> createVorbisComments(
            @Nullable final StreamInfo info) {
        final List<Pair<String, String>> tags = new ArrayList<>();
        if (info == null) {
            return tags;
        }
        add(tags, "TITLE", info.getName());
        add(tags, "ARTIST", info.getUploaderName());
        add(tags, "ARTIST_URL", info.getUploaderUrl());
        add(tags, "GENRE", info.getCategory());
        add(tags, "COMMENT", info.getUrl());
        add(tags, "PUBLISHER", info.getHost());
        add(tags, "LICENSE", info.getLicence());
        add(tags, "DATE", info.getTextualUploadDate());
        if (info.getDescription() != null) {
            add(tags, "DESCRIPTION", info.getDescription().getContent());
        }
        if (info.getDuration() > 0) {
            add(tags, "LENGTH", Long.toString(info.getDuration()));
        }
        if (info.getViewCount() >= 0) {
            add(tags, "VIEWS", Long.toString(info.getViewCount()));
        }
        if (info.getLikeCount() >= 0) {
            add(tags, "LIKES", Long.toString(info.getLikeCount()));
        }
        for (final String tag : info.getTags()) {
            add(tags, "TAG", tag);
        }
        final Artwork artwork = downloadArtwork(info);
        if (artwork != null) {
            add(tags, "METADATA_BLOCK_PICTURE",
                    Base64.encodeToString(picture(artwork), Base64.NO_WRAP));
        }
        return tags;
    }

    public static byte[] createMp4Udta(@Nullable final StreamInfo info) {
        if (info == null) {
            return null;
        }
        final List<byte[]> items = new ArrayList<>();
        textItem(items, "\u00A9nam", info.getName());
        textItem(items, "\u00A9ART", info.getUploaderName());
        textItem(items, "\u00A9gen", info.getCategory());
        textItem(items, "\u00A9day", info.getTextualUploadDate());
        textItem(items, "\u00A9cmt", info.getUrl());
        if (info.getDescription() != null) {
            textItem(items, "\u00A9des", info.getDescription().getContent());
        }
        for (final String tag : info.getTags()) {
            textItem(items, "\u00A9key", tag);
        }
        final Artwork artwork = downloadArtwork(info);
        if (artwork != null) {
            items.add(box("covr",
                    dataAtom("image/png".equals(artwork.mime) ? 14 : 13, artwork.bytes)));
        }
        if (items.isEmpty()) {
            return null;
        }
        final byte[] ilst = box("ilst", concat(items));
        final ByteBuffer header = ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN);
        header.putInt(0).putInt(0).put(new byte[]{'m', 'd', 'i', 'r'}).put(new byte[12])
                .put((byte) 0);
        return box("udta", box("meta", concat(new byte[4], box("hdlr", header.array()), ilst)));
    }

    public static byte[] createId3v24Tag(@Nullable final StreamInfo info) {
        if (info == null) {
            return new byte[0];
        }
        final List<byte[]> frames = new ArrayList<>();
        id3Text(frames, "TIT2", info.getName());
        id3Text(frames, "TPE1", info.getUploaderName());
        id3Text(frames, "TCON", info.getCategory());
        id3Text(frames, "TDRC", info.getTextualUploadDate());
        id3Text(frames, "WOAS", info.getUrl());
        id3Text(frames, "WCOP", info.getLicence());
        if (info.getDescription() != null) {
            id3Text(frames, "TIT3", info.getDescription().getContent());
            id3Comment(frames, info.getDescription().getContent());
        }
        id3UserIf(frames, "ARTIST_URL", info.getUploaderUrl());
        id3UserIf(frames, "PUBLISHER", info.getHost());
        if (info.getDuration() > 0) {
            id3User(frames, "DURATION_SECONDS", Long.toString(info.getDuration()));
        }
        if (info.getViewCount() >= 0) {
            id3User(frames, "VIEWS", Long.toString(info.getViewCount()));
        }
        if (info.getLikeCount() >= 0) {
            id3User(frames, "LIKES", Long.toString(info.getLikeCount()));
        }
        for (final String tag : info.getTags()) {
            id3User(frames, "TAG", tag);
        }
        final Artwork artwork = downloadArtwork(info);
        if (artwork != null) {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            output.write(3);
            ascii(output, artwork.mime);
            output.write(0);
            output.write(3);
            output.write(0);
            output.write(artwork.bytes, 0, artwork.bytes.length);
            frames.add(id3Frame("APIC", output.toByteArray()));
        }
        final byte[] body = concat(frames);
        if (body.length == 0) {
            return body;
        }
        final ByteBuffer header = ByteBuffer.allocate(10);
        header.put(new byte[]{'I', 'D', '3', 4, 0, 0}).put(syncSafe(body.length));
        return concat(header.array(), body);
    }

    private static void add(final List<Pair<String, String>> tags, final String key,
                            @Nullable final String value) {
        if (value != null && !value.trim().isEmpty()) {
            tags.add(Pair.create(key, value.trim()));
        }
    }

    private static void textItem(final List<byte[]> items, final String key,
                                 @Nullable final String value) {
        if (value != null && !value.trim().isEmpty()) {
            items.add(box(key, dataAtom(1, value.trim().getBytes(StandardCharsets.UTF_8))));
        }
    }

    private static byte[] dataAtom(final int type, final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.allocate(16 + data.length).order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(16 + data.length).put(new byte[]{'d', 'a', 't', 'a'})
                .putInt(type).putInt(0).put(data);
        return buffer.array();
    }

    private static void id3Text(final List<byte[]> output, final String id,
                                @Nullable final String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        final byte[] text = value.trim().getBytes(StandardCharsets.UTF_8);
        final byte[] payload = new byte[text.length + 1];
        payload[0] = 3;
        System.arraycopy(text, 0, payload, 1, text.length);
        output.add(id3Frame(id, payload));
    }

    private static void id3Comment(final List<byte[]> output, final String value) {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(3);
        ascii(buffer, "eng");
        buffer.write(0);
        final byte[] text = value.getBytes(StandardCharsets.UTF_8);
        buffer.write(text, 0, text.length);
        output.add(id3Frame("COMM", buffer.toByteArray()));
    }

    private static void id3UserIf(final List<byte[]> output, final String key,
                                  @Nullable final String value) {
        if (value != null && !value.trim().isEmpty()) {
            id3User(output, key, value);
        }
    }

    private static void id3User(final List<byte[]> output, final String key, final String value) {
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.write(3);
        final byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        buffer.write(keyBytes, 0, keyBytes.length);
        buffer.write(0);
        final byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        buffer.write(valueBytes, 0, valueBytes.length);
        output.add(id3Frame("TXXX", buffer.toByteArray()));
    }

    private static byte[] id3Frame(final String id, final byte[] data) {
        final ByteBuffer buffer = ByteBuffer.allocate(10 + data.length);
        buffer.put(id.getBytes(StandardCharsets.US_ASCII)).put(syncSafe(data.length))
                .putShort((short) 0).put(data);
        return buffer.array();
    }

    private static byte[] syncSafe(final int value) {
        return new byte[]{
                (byte) ((value >> 21) & 127),
                (byte) ((value >> 14) & 127),
                (byte) ((value >> 7) & 127),
                (byte) (value & 127)
        };
    }

    private static void ascii(final ByteArrayOutputStream output, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.write(bytes, 0, bytes.length);
    }

    private static byte[] picture(final Artwork artwork) {
        final byte[] mime = artwork.mime.getBytes(StandardCharsets.US_ASCII);
        final byte[] description = "Cover art".getBytes(StandardCharsets.UTF_8);
        final ByteBuffer buffer = ByteBuffer.allocate(
                4 + 4 + mime.length + 4 + description.length + 16 + 4 + artwork.bytes.length)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(3).putInt(mime.length).put(mime).putInt(description.length)
                .put(description).putInt(artwork.width).putInt(artwork.height)
                .putInt(24).putInt(0).putInt(artwork.bytes.length).put(artwork.bytes);
        return buffer.array();
    }

    private static byte[] box(final String type, final byte[] payload) {
        final byte[] name = type.getBytes(StandardCharsets.ISO_8859_1);
        if (name.length != 4) {
            throw new IllegalArgumentException("MP4 atom must be four bytes");
        }
        final ByteBuffer buffer = ByteBuffer.allocate(8 + payload.length)
                .order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(8 + payload.length).put(name).put(payload);
        return buffer.array();
    }

    private static byte[] concat(final List<byte[]> list) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (final byte[] bytes : list) {
            output.write(bytes, 0, bytes.length);
        }
        return output.toByteArray();
    }

    private static byte[] concat(final byte[]... list) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (final byte[] bytes : list) {
            output.write(bytes, 0, bytes.length);
        }
        return output.toByteArray();
    }

    @Nullable
    private static Artwork downloadArtwork(@Nullable final StreamInfo info) {
        if (info == null || info.getThumbnails() == null || info.getThumbnails().isEmpty()) {
            return null;
        }
        Image selected = null;
        long best = -1;
        for (final Image image : info.getThumbnails()) {
            if (image == null || image.getUrl() == null || image.getUrl().isEmpty()) {
                continue;
            }
            final long area = (long) Math.max(0, image.getWidth()) * Math.max(0, image.getHeight());
            if (selected == null || area > best) {
                selected = image;
                best = area;
            }
        }
        if (selected == null) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            final URL url = new URL(selected.getUrl());
            if (!"http".equalsIgnoreCase(url.getProtocol())
                    && !"https".equalsIgnoreCase(url.getProtocol())) {
                return null;
            }
            connection = (HttpURLConnection) url.openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(7000);
            connection.setRequestProperty("User-Agent", "NewPipe");
            final int response = connection.getResponseCode();
            if (response < 200 || response >= 300) {
                return null;
            }
            final int length = connection.getContentLength();
            if (length > MAX_ARTWORK_BYTES) {
                return null;
            }
            final ByteArrayOutputStream output =
                    new ByteArrayOutputStream(length > 0 ? length : 8192);
            try (final InputStream input = connection.getInputStream()) {
                final byte[] buffer = new byte[16384];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_ARTWORK_BYTES) {
                        return null;
                    }
                    output.write(buffer, 0, count);
                }
            }
            final byte[] bytes = output.toByteArray();
            if (bytes.length == 0) {
                return null;
            }
            String mime = connection.getContentType();
            if (mime != null && mime.contains(";")) {
                mime = mime.substring(0, mime.indexOf(';')).trim();
            }
            if (mime == null || !mime.startsWith("image/")) {
                mime = sniff(bytes);
            }
            if (!"image/jpeg".equalsIgnoreCase(mime) && !"image/png".equalsIgnoreCase(mime)) {
                final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) {
                    return null;
                }
                try {
                    final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);
                    final byte[] jpegBytes = jpeg.toByteArray();
                    mime = "image/jpeg";
                    return artworkWithBounds(jpegBytes, mime, selected);
                } finally {
                    bitmap.recycle();
                }
            }
            return artworkWithBounds(bytes, mime, selected);
        } catch (final Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Artwork artworkWithBounds(final byte[] bytes, final String mime,
                                              final Image selected) {
        final BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
        final int width = selected.getWidth() > 0 ? selected.getWidth() : bounds.outWidth;
        final int height = selected.getHeight() > 0 ? selected.getHeight() : bounds.outHeight;
        return new Artwork(bytes, mime, Math.max(0, width), Math.max(0, height));
    }

    private static String sniff(final byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 255) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 255) == 0xff
                && (bytes[1] & 255) == 0xd8 && (bytes[2] & 255) == 0xff) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static final class Artwork {
        private final byte[] bytes;
        private final String mime;
        private final int width;
        private final int height;

        Artwork(final byte[] bytes, final String mime, final int width, final int height) {
            this.bytes = bytes;
            this.mime = mime;
            this.width = width;
            this.height = height;
        }
    }
}
