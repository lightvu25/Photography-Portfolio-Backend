package com.photography.backend.cloudinary;

import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Lightweight, zero-dependency reader and transformer for the JPEG EXIF Orientation tag (0x0112).
 *
 * <p>Reads only the JPEG APP1 segment header from disk without decoding pixel data, and applies
 * the standard TIFF/EXIF orientation transform (values 1..8) when an image is re-encoded.
 */
public final class ExifOrientationHelper {

    private static final int EXIF_ORIENTATION_TAG = 0x0112;

    private ExifOrientationHelper() {
    }

    /**
     * Reads the EXIF orientation (1..8) from a JPEG file, returning {@code 1} (normal)
     * if the file is not a JPEG, has no EXIF APP1 segment, or does not specify an orientation.
     */
    public static int readJpegOrientation(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            if (in.read() != 0xFF || in.read() != 0xD8) {
                return 1; // Not a JPEG
            }
            while (true) {
                int markerPrefix = in.read();
                if (markerPrefix != 0xFF) {
                    return 1;
                }
                int marker = in.read();
                while (marker == 0xFF) {
                    marker = in.read();
                }
                if (marker < 0 || marker == 0xDA || marker == 0xD9) {
                    // Start of Scan (SOS) or End of Image (EOI) reached
                    return 1;
                }
                int lenHigh = in.read();
                int lenLow = in.read();
                if (lenHigh < 0 || lenLow < 0) {
                    return 1;
                }
                int segmentLength = ((lenHigh << 8) | lenLow) - 2;
                if (segmentLength < 0) {
                    return 1;
                }

                if (marker == 0xE1 && segmentLength >= 14 && segmentLength <= 65535) {
                    byte[] app1 = in.readNBytes(segmentLength);
                    if (app1.length == segmentLength && isExifHeader(app1)) {
                        int orientation = parseTiffOrientation(app1, 6, app1.length - 6);
                        if (orientation >= 1 && orientation <= 8) {
                            return orientation;
                        }
                    }
                } else {
                    long remaining = segmentLength;
                    while (remaining > 0) {
                        long skipped = in.skip(remaining);
                        if (skipped <= 0) {
                            if (in.read() < 0) {
                                return 1;
                            }
                            skipped = 1;
                        }
                        remaining -= skipped;
                    }
                }
            }
        } catch (IOException e) {
            return 1;
        }
    }

    private static boolean isExifHeader(byte[] data) {
        return data.length >= 6
                && data[0] == 'E'
                && data[1] == 'x'
                && data[2] == 'i'
                && data[3] == 'f'
                && data[4] == 0
                && data[5] == 0;
    }

    private static int parseTiffOrientation(byte[] data, int tiffStart, int tiffLen) {
        if (tiffLen < 8) {
            return 1;
        }
        boolean littleEndian;
        if (data[tiffStart] == 'I' && data[tiffStart + 1] == 'I') {
            littleEndian = true;
        } else if (data[tiffStart] == 'M' && data[tiffStart + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }

        if (readUint16(data, tiffStart + 2, littleEndian) != 0x002A) {
            return 1;
        }
        int ifdOffset = readInt32(data, tiffStart + 4, littleEndian);
        if (ifdOffset < 8 || ifdOffset + 2 > tiffLen) {
            return 1;
        }

        int dirStart = tiffStart + ifdOffset;
        int numEntries = readUint16(data, dirStart, littleEndian);
        int entryOffset = dirStart + 2;

        for (int i = 0; i < numEntries; i++) {
            if (entryOffset + 12 > tiffStart + tiffLen) {
                break;
            }
            int tag = readUint16(data, entryOffset, littleEndian);
            if (tag == EXIF_ORIENTATION_TAG) {
                int orientation = readUint16(data, entryOffset + 8, littleEndian);
                return (orientation >= 1 && orientation <= 8) ? orientation : 1;
            }
            entryOffset += 12;
        }
        return 1;
    }

    private static int readUint16(byte[] data, int offset, boolean littleEndian) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        return littleEndian ? ((b1 << 8) | b0) : ((b0 << 8) | b1);
    }

    private static int readInt32(byte[] data, int offset, boolean littleEndian) {
        int b0 = data[offset] & 0xFF;
        int b1 = data[offset + 1] & 0xFF;
        int b2 = data[offset + 2] & 0xFF;
        int b3 = data[offset + 3] & 0xFF;
        return littleEndian
                ? ((b3 << 24) | (b2 << 16) | (b1 << 8) | b0)
                : ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3);
    }

    /** Returns {@code true} if the given EXIF orientation swaps width and height (orientations 5..8). */
    public static boolean swapsDimensions(int orientation) {
        return orientation >= 5 && orientation <= 8;
    }

    /**
     * Applies the EXIF orientation transform (1..8) to {@code source}.
     * If {@code orientation == 1}, returns {@code source} directly without allocating a copy.
     */
    public static BufferedImage applyOrientation(BufferedImage source, int orientation, boolean preserveAlpha) {
        if (orientation <= 1 || orientation > 8) {
            return source;
        }

        int w = source.getWidth();
        int h = source.getHeight();
        boolean swap = swapsDimensions(orientation);
        int targetW = swap ? h : w;
        int targetH = swap ? w : h;

        int imageType = preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage dest = new BufferedImage(targetW, targetH, imageType);

        AffineTransform tx = new AffineTransform();
        switch (orientation) {
            case 2 -> { // Flip horizontal
                tx.scale(-1.0, 1.0);
                tx.translate(-w, 0);
            }
            case 3 -> { // Rotate 180
                tx.translate(w, h);
                tx.rotate(Math.PI);
            }
            case 4 -> { // Flip vertical
                tx.scale(1.0, -1.0);
                tx.translate(0, -h);
            }
            case 5 -> { // Transpose (flip horizontal + rotate 270 CW)
                tx.rotate(Math.PI / 2);
                tx.scale(1.0, -1.0);
            }
            case 6 -> { // Rotate 90 CW
                tx.translate(h, 0);
                tx.rotate(Math.PI / 2);
            }
            case 7 -> { // Transverse (flip horizontal + rotate 90 CW)
                tx.scale(-1.0, 1.0);
                tx.translate(-h, 0);
                tx.translate(0, w);
                tx.rotate(3 * Math.PI / 2);
            }
            case 8 -> { // Rotate 270 CW
                tx.translate(0, w);
                tx.rotate(3 * Math.PI / 2);
            }
            default -> {
                return source;
            }
        }

        Graphics2D g = dest.createGraphics();
        try {
            g.drawImage(source, tx, null);
        } finally {
            g.dispose();
        }
        return dest;
    }
}
