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

/** Encodes extractor metadata in standard media-container tags. */
public final class MediaMetadata {
    private static final int MAX_ARTWORK_BYTES = 5 * 1024 * 1024;

    private MediaMetadata() {
    }

    public static List<Pair<String, String>> createVorbisComments(@Nullable final StreamInfo info) {
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
                    Base64.encodeToString(createFlacPicture(artwork), Base64.NO_WRAP));
        }
        return tags;
    }

    public static byte[] createMp4Udta(@Nullable final StreamInfo info) {
        if (info == null) {
            return null;
        }
        final List<byte[]> items = new ArrayList<>();
        addMp4Text(items, "\u00A9nam", info.getName());
        addMp4Text(items, "\u00A9ART", info.getUploaderName());
        addMp4Text(items, "\u00A9gen", info.getCategory());
        addMp4Text(items, "\u00A9day", info.getTextualUploadDate());
        addMp4Text(items, "\u00A9cmt", info.getUrl());
        if (info.getDescription() != null) {
            addMp4Text(items, "\u00A9des", info.getDescription().getContent());
        }
        for (final String tag : info.getTags()) {
            addMp4Text(items, "\u00A9key", tag);
        }
        final Artwork artwork = downloadArtwork(info);
        if (artwork != null) {
            items.add(makeBox("covr", makeDataAtom(
                    "image/png".equals(artwork.mime) ? 14 : 13, artwork.bytes)));
        }
        if (items.isEmpty()) {
            return null;
        }
        final byte[] ilst = makeBox("ilst", concatenate(items));
        final ByteBuffer handler = ByteBuffer.allocate(25).order(ByteOrder.BIG_ENDIAN);
        handler.putInt(0);
        handler.putInt(0);
        handler.put(new byte[]{'m', 'd', 'i', 'r'});
        handler.put(new byte[12]);
        handler.put((byte) 0);
        final byte[] hdlr = makeBox("hdlr", handler.array());
        final byte[] meta = makeBox("meta", concatenate(new byte[4], hdlr, ilst));
        return makeBox("udta", meta);
    }

    public static byte[] createId3v24Tag(@Nullable final StreamInfo info) {
        if (info == null) {
            return new byte[0];
        }
        final List<byte[]> frames = new ArrayList<>();
        addId3Text(frames, "TIT2", info.getName());
        addId3Text(frames, "TPE1", info.getUploaderName());
        addId3Text(frames, "TCON", info.getCategory());
        addId3Text(frames, "TDRC", info.getTextualUploadDate());
        addId3Text(frames, "WOAS", info.getUrl());
        addId3Text(frames, "WCOP", info.getLicence());
        if (info.getDescription() != null) {
            addId3Text(frames, "TIT3", info.getDescription().getContent());
            addId3Comment(frames, info.getDescription().getContent());
        }
        addId3UserTextIfPresent(frames, "ARTIST_URL", info.getUploaderUrl());
        addId3UserTextIfPresent(frames, "PUBLISHER", info.getHost());
        if (info.getDuration() > 0) {
            addId3UserText(frames, "DURATION_SECONDS", Long.toString(info.getDuration()));
        }
        if (info.getViewCount() >= 0) {
            addId3UserText(frames, "VIEWS", Long.toString(info.getViewCount()));
        }
        if (info.getLikeCount() >= 0) {
            addId3UserText(frames, "LIKES", Long.toString(info.getLikeCount()));
        }
        for (final String tag : info.getTags()) {
            addId3UserText(frames, "TAG", tag);
        }
        final Artwork artwork = downloadArtwork(info);
        if (artwork != null) {
            final ByteArrayOutputStream payload = new ByteArrayOutputStream();
            payload.write(3); // UTF-8
            writeAscii(payload, artwork.mime);
            payload.write(0);
            payload.write(3); // front cover
            payload.write(0); // empty description
            payload.write(artwork.bytes, 0, artwork.bytes.length);
            frames.add(makeId3Frame("APIC", payload.toByteArray()));
        }
        final byte[] body = concatenate(frames);
        if (body.length == 0) {
            return body;
        }
        final ByteBuffer header = ByteBuffer.allocate(10);
        header.put(new byte[]{'I', 'D', '3', 4, 0, 0});
        header.put(syncSafe(body.length));
        return concatenate(header.array(), body);
    }

    private static void add(final List<Pair<String, String>> tags, final String key,
                            @Nullable final String value) {
        if (value != null && !value.trim().isEmpty()) {
            tags.add(Pair.create(key, value.trim()));
        }
    }

    private static void addMp4Text(final List<byte[]> items, final String key,
                                   @Nullable final String value) {
        if (value != null && !value.trim().isEmpty()) {
            items.add(makeBox(key, makeDataAtom(1, value.trim().getBytes(StandardCharsets.UTF_8))));
        }
    }

    private static byte[] makeDataAtom(final int type, final byte[] value) {
        final ByteBuffer atom = ByteBuffer.allocate(16 + value.length).order(ByteOrder.BIG_ENDIAN);
        atom.putInt(16 + value.length);
        atom.put(new byte[]{'d', 'a', 't', 'a'});
        atom.putInt(type);
        atom.putInt(0);
        atom.put(value);
        return atom.array();
    }

    private static void addId3Text(final List<byte[]> frames, final String id,
                                   @Nullable final String value) {
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        final byte[] text = value.trim().getBytes(StandardCharsets.UTF_8);
        final byte[] payload = new byte[text.length + 1];
        payload[0] = 3;
        System.arraycopy(text, 0, payload, 1, text.length);
        frames.add(makeId3Frame(id, payload));
    }

    private static void addId3Comment(final List<byte[]> frames, final String value) {
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(3);
        writeAscii(payload, "eng");
        payload.write(0);
        final byte[] text = value.getBytes(StandardCharsets.UTF_8);
        payload.write(text, 0, text.length);
        frames.add(makeId3Frame("COMM", payload.toByteArray()));
    }

    private static void addId3UserTextIfPresent(final List<byte[]> frames, final String key,
                                                @Nullable final String value) {
        if (value != null && !value.trim().isEmpty()) {
            addId3UserText(frames, key, value);
        }
    }

    private static void addId3UserText(final List<byte[]> frames, final String key,
                                       final String value) {
        final ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(3);
        final byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        payload.write(keyBytes, 0, keyBytes.length);
        payload.write(0);
        final byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
        payload.write(valueBytes, 0, valueBytes.length);
        frames.add(makeId3Frame("TXXX", payload.toByteArray()));
    }

    private static byte[] makeId3Frame(final String id, final byte[] payload) {
        final ByteBuffer frame = ByteBuffer.allocate(10 + payload.length);
        frame.put(id.getBytes(StandardCharsets.US_ASCII));
        frame.put(syncSafe(payload.length));
        frame.putShort((short) 0);
        frame.put(payload);
        return frame.array();
    }

    private static byte[] syncSafe(final int size) {
        return new byte[]{
                (byte) ((size >> 21) & 0x7f),
                (byte) ((size >> 14) & 0x7f),
                (byte) ((size >> 7) & 0x7f),
                (byte) (size & 0x7f)
        };
    }

    private static void writeAscii(final ByteArrayOutputStream output, final String value) {
        final byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        output.write(bytes, 0, bytes.length);
    }

    private static byte[] createFlacPicture(final Artwork artwork) {
        final byte[] mime = artwork.mime.getBytes(StandardCharsets.US_ASCII);
        final byte[] description = "Cover art".getBytes(StandardCharsets.UTF_8);
        final ByteBuffer picture = ByteBuffer.allocate(4 + 4 + mime.length + 4
                + description.length + 16 + 4 + artwork.bytes.length).order(ByteOrder.BIG_ENDIAN);
        picture.putInt(3);
        picture.putInt(mime.length);
        picture.put(mime);
        picture.putInt(description.length);
        picture.put(description);
        picture.putInt(artwork.width);
        picture.putInt(artwork.height);
        picture.putInt(24);
        picture.putInt(0);
        picture.putInt(artwork.bytes.length);
        picture.put(artwork.bytes);
        return picture.array();
    }

    private static byte[] makeBox(final String type, final byte[] payload) {
        final byte[] typeBytes = type.getBytes(StandardCharsets.ISO_8859_1);
        if (typeBytes.length != 4) {
            throw new IllegalArgumentException("MP4 atom type must be four bytes");
        }
        final ByteBuffer box = ByteBuffer.allocate(8 + payload.length).order(ByteOrder.BIG_ENDIAN);
        box.putInt(8 + payload.length);
        box.put(typeBytes);
        box.put(payload);
        return box.array();
    }

    private static byte[] concatenate(final List<byte[]> chunks) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (final byte[] chunk : chunks) {
            output.write(chunk, 0, chunk.length);
        }
        return output.toByteArray();
    }

    private static byte[] concatenate(final byte[]... chunks) {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (final byte[] chunk : chunks) {
            output.write(chunk, 0, chunk.length);
        }
        return output.toByteArray();
    }

    @Nullable
    private static Artwork downloadArtwork(@Nullable final StreamInfo info) {
        if (info == null || info.getThumbnails() == null || info.getThumbnails().isEmpty()) {
            return null;
        }
        Image selected = null;
        long bestArea = -1;
        for (final Image image : info.getThumbnails()) {
            if (image == null || image.getUrl() == null || image.getUrl().isEmpty()) {
                continue;
            }
            final long area = (long) Math.max(0, image.getWidth()) * Math.max(0, image.getHeight());
            if (selected == null || area > bestArea) {
                selected = image;
                bestArea = area;
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
            final ByteArrayOutputStream output = new ByteArrayOutputStream(length > 0 ? length : 8192);
            try (InputStream input = connection.getInputStream()) {
                final byte[] buffer = new byte[16 * 1024];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (output.size() + count > MAX_ARTWORK_BYTES) {
                        return null;
                    }
                    output.write(buffer, 0, count);
                }
            }
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) {
                return null;
            }
            String mime = connection.getContentType();
            if (mime != null && mime.contains(";")) {
                mime = mime.substring(0, mime.indexOf(';')).trim();
            }
            if (mime == null || !mime.startsWith("image/")) {
                mime = sniffImageMime(bytes);
            }
            if (!"image/jpeg".equalsIgnoreCase(mime) && !"image/png".equalsIgnoreCase(mime)) {
                final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bitmap == null) {
                    return null;
                }
                try {
                    final ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, jpeg);
                    bytes = jpeg.toByteArray();
                    mime = "image/jpeg";
                } finally {
                    bitmap.recycle();
                }
            }
            final BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
            final int width = selected.getWidth() > 0 ? selected.getWidth() : bounds.outWidth;
            final int height = selected.getHeight() > 0 ? selected.getHeight() : bounds.outHeight;
            return new Artwork(bytes, mime, Math.max(0, width), Math.max(0, height));
        } catch (final Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String sniffImageMime(final byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47) {
            return "image/png";
        }
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static final class Artwork {
        final byte[] bytes;
        final String mime;
        final int width;
        final int height;

        Artwork(final byte[] bytes, final String mime, final int width, final int height) {
            this.bytes = bytes;
            this.mime = mime;
            this.width = width;
            this.height = height;
        }
    }
}
