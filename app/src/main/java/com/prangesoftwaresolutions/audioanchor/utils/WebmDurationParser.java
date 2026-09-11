package com.prangesoftwaresolutions.audioanchor.utils;

import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/*
 * Reads a WebM/Matroska file's own declared duration directly out of its EBML header (Segment >
 * Info > Duration, scaled by Segment > Info > TimestampScale), without decoding any audio.
 *
 * MediaMetadataRetriever normally gets this for free from the same header, but for webm files
 * that lack a Cues (seek index) element -- common for recordings that were streamed/muxed live,
 * e.g. straight out of a browser's MediaRecorder API -- Android's extractor falls back to
 * demuxing every block in the file to work out where it ends, which for a many-hour recording
 * can take seconds to minutes (see issue #233) and can still fail to produce a duration at all.
 * The Duration/TimestampScale fields, when present, sit in the file's first few hundred bytes
 * regardless of how long the recording is, so reading them directly is effectively instant.
 *
 * This is a best-effort fast path: any file this can't confidently parse (not a Matroska file,
 * corrupt header, or simply no Duration written) yields null, and callers should fall back to
 * MediaMetadataRetriever as before.
 */
public final class WebmDurationParser {

    private static final long ID_EBML = 0x1A45DFA3L;
    private static final long ID_SEGMENT = 0x18538067L;
    private static final long ID_INFO = 0x1549A966L;
    private static final long ID_TIMESTAMP_SCALE = 0x2AD7B1L;
    private static final long ID_DURATION = 0x4489L;

    // Matroska's default TimestampScale (ns per duration unit) when the element is absent.
    private static final long DEFAULT_TIMESTAMP_SCALE = 1_000_000L;

    // Far more header than any real muxer writes before the Info element. If Info hasn't shown
    // up within this many bytes, give up rather than keep scanning indefinitely.
    private static final long SEARCH_BUDGET_BYTES = 5 * 1024 * 1024;

    private WebmDurationParser() {
    }

    public static Long parseDurationMs(File file) {
        try (CountingInputStream in = new CountingInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            if (readId(in) != ID_EBML) return null;
            skip(in, readSize(in));

            if (readId(in) != ID_SEGMENT) return null;
            // Segment's own size is commonly "unknown" for streamed files -- we don't need it,
            // since we walk its children directly below instead of skipping over the element.
            readSize(in);

            while (in.count < SEARCH_BUDGET_BYTES) {
                long id = readId(in);
                long size = readSize(in);
                // An unknown-size sibling (e.g. a live-streamed Cluster) before Info means we
                // can't safely skip past it to keep looking -- bail out to the slow path.
                if (size < 0) return null;

                if (id == ID_INFO) {
                    return parseInfo(in, size);
                }
                skip(in, size);
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    private static Long parseInfo(CountingInputStream in, long size) throws IOException {
        long end = in.count + size;
        Long timestampScale = null;
        Double duration = null;
        while (in.count < end) {
            long id = readId(in);
            long childSize = readSize(in);
            if (childSize < 0) return null; // Info's children always have a definite size

            if (id == ID_TIMESTAMP_SCALE) {
                timestampScale = readUInt(in, childSize);
            } else if (id == ID_DURATION) {
                duration = readFloat(in, childSize);
            } else {
                skip(in, childSize);
            }
        }

        if (duration == null) return null;
        long scale = timestampScale != null ? timestampScale : DEFAULT_TIMESTAMP_SCALE;
        double durationMs = duration * scale / 1_000_000.0;
        if (!(durationMs > 0) || !Double.isFinite(durationMs)) return null;
        return Math.round(durationMs);
    }

    /*
     * Reads an EBML element ID, keeping its length-marker bits (matching conventional Matroska
     * ID notation, e.g. Segment = 0x18538067).
     */
    private static long readId(CountingInputStream in) throws IOException {
        int first = readByte(in);
        int length = vintLength(first);
        long id = first;
        for (int i = 1; i < length; i++) {
            id = (id << 8) | readByte(in);
        }
        return id;
    }

    /*
     * Reads an EBML element size, with its length-marker bits stripped. Returns -1 for the
     * special "unknown size" value (all data bits set), used by elements in streamed files whose
     * length wasn't known when they were written.
     */
    private static long readSize(CountingInputStream in) throws IOException {
        int first = readByte(in);
        int length = vintLength(first);
        int marker = 0x80 >> (length - 1);
        long size = first & (marker - 1);
        boolean allOnes = size == (marker - 1);
        for (int i = 1; i < length; i++) {
            int b = readByte(in);
            size = (size << 8) | b;
            if (b != 0xFF) allOnes = false;
        }
        return allOnes ? -1 : size;
    }

    private static long readUInt(CountingInputStream in, long byteLength) throws IOException {
        if (byteLength < 0 || byteLength > 8) throw new IOException("invalid EBML uint length");
        long value = 0;
        for (long i = 0; i < byteLength; i++) {
            value = (value << 8) | readByte(in);
        }
        return value;
    }

    private static double readFloat(CountingInputStream in, long byteLength) throws IOException {
        if (byteLength == 4) {
            return Float.intBitsToFloat((int) readUInt(in, 4));
        } else if (byteLength == 8) {
            return Double.longBitsToDouble(readUInt(in, 8));
        }
        throw new IOException("invalid EBML float length");
    }

    // The number of bytes in an EBML variable-size integer, from its first byte: the position of
    // the leading 1 bit (1-8, counting from the most significant bit).
    private static int vintLength(int firstByte) throws IOException {
        for (int length = 1, marker = 0x80; length <= 8; length++, marker >>= 1) {
            if ((firstByte & marker) != 0) return length;
        }
        throw new IOException("invalid EBML variable-size integer");
    }

    private static int readByte(CountingInputStream in) throws IOException {
        int b = in.read();
        if (b < 0) throw new EOFException();
        return b;
    }

    /*
     * Skips forward, looping since InputStream.skip() is free to skip fewer bytes than
     * requested even mid-stream.
     */
    private static void skip(CountingInputStream in, long n) throws IOException {
        while (n > 0) {
            long skipped = in.skip(n);
            if (skipped <= 0) {
                if (in.read() < 0) throw new EOFException();
                n--;
            } else {
                n -= skipped;
            }
        }
    }

    private static final class CountingInputStream extends FilterInputStream {
        long count = 0;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count++;
            return b;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            count += skipped;
            return skipped;
        }
    }
}
