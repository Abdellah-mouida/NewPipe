package us.shandian.giga.postprocessing;

import androidx.annotation.NonNull;

import org.schabi.newpipe.streams.MediaMetadata;
import org.schabi.newpipe.streams.io.SharpStream;

import java.io.IOException;

/** Adds ID3v2.4 metadata while preserving the original MPEG audio stream. */
class Mp3MetadataTagger extends Postprocessing {
    Mp3MetadataTagger() {
        super(false, true, ALGORITHM_MP3_METADATA);
    }

    @Override
    boolean test(final SharpStream... sources) throws IOException {
        final byte[] signature = new byte[3];
        final int count = sources[0].read(signature);
        sources[0].rewind();
        // Avoid placing a second ID3 header in front of existing media tags.
        return count != 3 || signature[0] != 'I' || signature[1] != 'D'
                || signature[2] != '3';
    }

    @Override
    int process(final SharpStream output, @NonNull final SharpStream... sources) throws IOException {
        final byte[] tag = MediaMetadata.createId3v24Tag(streamInfo);
        if (tag.length > 0) {
            output.write(tag);
        }

        final SharpStream input = sources[0];
        final byte[] buffer = new byte[64 * 1024];
        while (input.available() > 0) {
            final int count = input.read(buffer, 0,
                    (int) Math.min(buffer.length, input.available()));
            if (count <= 0) {
                break;
            }
            output.write(buffer, 0, count);
        }
        return OK_RESULT;
    }
}
