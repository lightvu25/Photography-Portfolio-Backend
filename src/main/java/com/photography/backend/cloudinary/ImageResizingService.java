package com.photography.backend.cloudinary;

import com.photography.backend.common.exception.BusinessException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Dedicated image-processing service implementing "Resize to Fit -> Long Edge"
 * before uploading to Cloudinary.
 *
 * <p>Key guarantees:
 * <ul>
 *   <li>Inspects actual binary format via {@link ImageIO#getImageReaders(Object)} instead of
 *       trusting filename extensions or MIME headers.</li>
 *   <li>Reads header dimensions ({@code width}, {@code height}) before pixel decoding and rejects
 *       decompression-bomb dimensions exceeding configured safeguards.</li>
 *   <li>Never enlarges or re-encodes images whose longest edge is already {@code <= maxLongEdge}:
 *       original bytes, format, and metadata are passed through untouched.</li>
 *   <li>Resizes proportionally when {@code max(width, height) > maxLongEdge} so the longest edge
 *       equals {@code maxLongEdge}, preserving aspect ratio, EXIF orientation, and alpha transparency.</li>
 *   <li>Does not apply an arbitrary fixed 85% JPEG quality reduction.</li>
 *   <li>Serializes full-resolution {@link BufferedImage} decoding and cleans up temporary files
 *       reliably after both successful and failed operations.</li>
 * </ul>
 */
@Service
@Slf4j
public class ImageResizingService {

    private static final Set<String> ALPHA_CAPABLE_FORMATS = Set.of("png", "gif", "tiff", "webp");

    private final ImageProcessingProperties properties;
    private final Path tempDirectory;
    private final ReentrantLock decodeLock = new ReentrantLock(true);

    @Autowired
    public ImageResizingService(ImageProcessingProperties properties) {
        this(properties, null);
    }

    public ImageResizingService() {
        this(new ImageProcessingProperties(), null);
    }

    public ImageResizingService(ImageProcessingProperties properties, Path tempDirectory) {
        if (properties == null) {
            throw new IllegalArgumentException("ImageProcessingProperties must not be null");
        }
        properties.validate();
        this.properties = properties;
        this.tempDirectory = tempDirectory;
    }

    /**
     * Validates, inspects, and (only when {@code longestEdge > maxLongEdge}) resizes the uploaded image.
     * Callers MUST close the returned {@link ProcessedImage} via try-with-resources to delete temporary files.
     */
    public ProcessedImage process(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Image file is required", "FILE_REQUIRED", HttpStatus.BAD_REQUEST);
        }

        Path sourceTemp = createTempFile("upload-src-", ".tmp");
        Path outputTemp = null;
        boolean handOffSourceTemp = false;
        boolean handOffOutputTemp = false;

        try {
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, sourceTemp, StandardCopyOption.REPLACE_EXISTING);
            }

            if (Files.size(sourceTemp) == 0L) {
                throw new BusinessException("Image file is required", "FILE_REQUIRED", HttpStatus.BAD_REQUEST);
            }

            decodeLock.lock();
            try (ImageInputStream iis = ImageIO.createImageInputStream(sourceTemp.toFile())) {
                if (iis == null) {
                    throw new BusinessException(
                            "Không thể đọc luồng dữ liệu ảnh tải lên",
                            "INVALID_IMAGE",
                            HttpStatus.BAD_REQUEST);
                }

                Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
                if (!readers.hasNext()) {
                    throw new BusinessException(
                            "Định dạng tệp không được hỗ trợ hoặc không phải là ảnh hợp lệ",
                            "UNSUPPORTED_IMAGE_FORMAT",
                            HttpStatus.BAD_REQUEST);
                }

                ImageReader reader = readers.next();
                try {
                    reader.setInput(iis, true, false);
                    String canonicalFormat = normalizeFormatName(reader.getFormatName());
                    ensureWriterAvailable(canonicalFormat);

                    int rawWidth;
                    int rawHeight;
                    try {
                        rawWidth = reader.getWidth(0);
                        rawHeight = reader.getHeight(0);
                    } catch (Exception e) {
                        throw new BusinessException(
                                "Tệp ảnh bị hỏng hoặc không thể đọc kích thước",
                                "INVALID_IMAGE",
                                HttpStatus.BAD_REQUEST);
                    }

                    validateDimensions(rawWidth, rawHeight);

                    int exifOrientation = "jpeg".equals(canonicalFormat)
                            ? ExifOrientationHelper.readJpegOrientation(sourceTemp)
                            : 1;
                    boolean swaps = ExifOrientationHelper.swapsDimensions(exifOrientation);
                    int effectiveWidth = swaps ? rawHeight : rawWidth;
                    int effectiveHeight = swaps ? rawWidth : rawHeight;
                    int longestEdge = Math.max(effectiveWidth, effectiveHeight);
                    int maxLongEdge = properties.getMaxLongEdge();

                    // Decode and verify pixel stream integrity
                    BufferedImage decoded;
                    try {
                        decoded = reader.read(0);
                    } catch (Exception e) {
                        throw new BusinessException(
                                "Tệp ảnh bị lỗi hoặc dữ liệu ảnh không hợp lệ",
                                "INVALID_IMAGE",
                                HttpStatus.BAD_REQUEST);
                    }
                    if (decoded == null) {
                        throw new BusinessException(
                                "Tệp ảnh bị lỗi hoặc không thể giải mã",
                                "INVALID_IMAGE",
                                HttpStatus.BAD_REQUEST);
                    }

                    // Case 1: Image longest edge is already <= maxLongEdge -> never enlarge, never re-encode
                    if (longestEdge <= maxLongEdge) {
                        decoded.flush();
                        handOffSourceTemp = true;
                        log.debug("Image {}x{} ({}) is within max-long-edge {} — passing original bytes untouched",
                                effectiveWidth, effectiveHeight, canonicalFormat, maxLongEdge);
                        return new ProcessedImage(
                                sourceTemp,
                                canonicalFormat,
                                effectiveWidth,
                                effectiveHeight,
                                effectiveWidth,
                                effectiveHeight,
                                false);
                    }

                    // Case 2: Image longest edge exceeds maxLongEdge -> apply EXIF orientation and resize proportionally
                    boolean preserveAlpha = decoded.getColorModel().hasAlpha()
                            && ALPHA_CAPABLE_FORMATS.contains(canonicalFormat);

                    BufferedImage oriented = decoded;
                    BufferedImage resized = null;
                    try {
                        if (exifOrientation != 1) {
                            oriented = ExifOrientationHelper.applyOrientation(decoded, exifOrientation, preserveAlpha);
                            if (oriented != decoded) {
                                decoded.flush();
                            }
                        }

                        int srcW = oriented.getWidth();
                        int srcH = oriented.getHeight();
                        int[] targetDims = calculateLongEdgeDimensions(srcW, srcH, maxLongEdge);
                        int targetW = targetDims[0];
                        int targetH = targetDims[1];

                        resized = progressiveResize(oriented, targetW, targetH, preserveAlpha);
                        if (resized != oriented) {
                            oriented.flush();
                        }

                        outputTemp = createTempFile("upload-resized-", "." + canonicalFormat);
                        writeEncodedImage(resized, canonicalFormat, outputTemp);

                        log.info("Resized {} image from {}x{} -> {}x{} (max-long-edge={})",
                                canonicalFormat, srcW, srcH, targetW, targetH, maxLongEdge);

                        handOffOutputTemp = true;
                        return new ProcessedImage(
                                outputTemp,
                                canonicalFormat,
                                srcW,
                                srcH,
                                targetW,
                                targetH,
                                true);
                    } finally {
                        decoded.flush();
                        if (oriented != null && oriented != decoded) {
                            oriented.flush();
                        }
                        if (resized != null && resized != oriented) {
                            resized.flush();
                        }
                    }
                } finally {
                    reader.dispose();
                }
            } finally {
                decodeLock.unlock();
            }
        } finally {
            if (!handOffSourceTemp) {
                deleteQuietly(sourceTemp);
            }
            if (!handOffOutputTemp && outputTemp != null) {
                deleteQuietly(outputTemp);
            }
        }
    }

    static int[] calculateLongEdgeDimensions(int width, int height, int maxLongEdge) {
        if (Math.max(width, height) <= maxLongEdge) {
            return new int[]{width, height};
        }
        if (width >= height) {
            int targetWidth = maxLongEdge;
            int targetHeight = Math.max(1, (int) Math.round((double) height * maxLongEdge / width));
            return new int[]{targetWidth, targetHeight};
        } else {
            int targetHeight = maxLongEdge;
            int targetWidth = Math.max(1, (int) Math.round((double) width * maxLongEdge / height));
            return new int[]{targetWidth, targetHeight};
        }
    }

    private void validateDimensions(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new BusinessException(
                    "Kích thước ảnh không hợp lệ (" + width + "x" + height + ")",
                    "INVALID_IMAGE",
                    HttpStatus.BAD_REQUEST);
        }
        long totalPixels = (long) width * (long) height;
        if (width > properties.getMaxDimension()
                || height > properties.getMaxDimension()
                || totalPixels > properties.getMaxPixels()) {
            throw new BusinessException(
                    String.format(
                            "Kích thước ảnh (%dx%d) vượt quá giới hạn an toàn cho phép (tối đa %d px mỗi chiều hoặc %d MP)",
                            width, height, properties.getMaxDimension(), properties.getMaxPixels() / 1_000_000L),
                    "IMAGE_DIMENSIONS_TOO_LARGE",
                    HttpStatus.BAD_REQUEST);
        }
    }

    private static String normalizeFormatName(String rawFormat) {
        if (rawFormat == null || rawFormat.isBlank()) {
            throw new BusinessException(
                    "Không xác định được định dạng ảnh",
                    "UNSUPPORTED_IMAGE_FORMAT",
                    HttpStatus.BAD_REQUEST);
        }
        String lower = rawFormat.trim().toLowerCase(Locale.ROOT);
        return switch (lower) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            case "gif" -> "gif";
            case "bmp" -> "bmp";
            case "wbmp" -> "wbmp";
            case "tif", "tiff" -> "tiff";
            case "webp" -> "webp";
            default -> lower;
        };
    }

    private static void ensureWriterAvailable(String canonicalFormat) {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(canonicalFormat);
        if (!writers.hasNext()) {
            throw new BusinessException(
                    "Định dạng ảnh không được hỗ trợ xử lý: " + canonicalFormat,
                    "UNSUPPORTED_IMAGE_FORMAT",
                    HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Multi-step progressive resampling (halving until within 2x of target, then final bicubic step)
     * to prevent aliasing on steep downscales while flushing intermediate buffers immediately.
     */
    private static BufferedImage progressiveResize(
            BufferedImage source,
            int targetWidth,
            int targetHeight,
            boolean preserveAlpha
    ) {
        int currentW = source.getWidth();
        int currentH = source.getHeight();
        if (currentW == targetWidth && currentH == targetHeight) {
            return source;
        }

        int imageType = preserveAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage current = source;

        while (currentW > targetWidth * 2 || currentH > targetHeight * 2) {
            int nextW = Math.max(targetWidth, (currentW + 1) / 2);
            int nextH = Math.max(targetHeight, (currentH + 1) / 2);
            BufferedImage step = renderScaledStep(current, nextW, nextH, imageType, preserveAlpha,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (current != source) {
                current.flush();
            }
            current = step;
            currentW = nextW;
            currentH = nextH;
        }

        BufferedImage finalImage = renderScaledStep(current, targetWidth, targetHeight, imageType, preserveAlpha,
                RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        if (current != source) {
            current.flush();
        }
        return finalImage;
    }

    private static BufferedImage renderScaledStep(
            BufferedImage src,
            int targetW,
            int targetH,
            int imageType,
            boolean preserveAlpha,
            Object interpolationHint
    ) {
        BufferedImage dest = new BufferedImage(targetW, targetH, imageType);
        Graphics2D g = dest.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolationHint);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            if (preserveAlpha) {
                g.setComposite(AlphaComposite.Src);
            } else {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, targetW, targetH);
            }
            g.drawImage(src, 0, 0, targetW, targetH, null);
        } finally {
            g.dispose();
        }
        return dest;
    }

    private static void writeEncodedImage(BufferedImage image, String canonicalFormat, Path outputPath)
            throws IOException {
        // GIF ImageWriter in standard JDK has 256-color palette limitations on true-color RGB buffers;
        // for PNG/JPEG/BMP/TIFF/WebP, use the native format ImageWriter without arbitrary 85% quality reduction.
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName(canonicalFormat);
        if (!writers.hasNext()) {
            throw new BusinessException(
                    "Không tìm thấy bộ mã hóa cho định dạng: " + canonicalFormat,
                    "UNSUPPORTED_IMAGE_FORMAT",
                    HttpStatus.BAD_REQUEST);
        }
        ImageWriter writer = writers.next();
        try (FileImageOutputStream fos = new FileImageOutputStream(outputPath.toFile())) {
            writer.setOutput(fos);
            ImageWriteParam writeParam = writer.getDefaultWriteParam();
            writer.write(null, new IIOImage(image, null, null), writeParam);
            fos.flush();
        } finally {
            writer.dispose();
        }
    }

    private Path createTempFile(String prefix, String suffix) throws IOException {
        if (tempDirectory != null) {
            Files.createDirectories(tempDirectory);
            return Files.createTempFile(tempDirectory, prefix, suffix);
        }
        return Files.createTempFile(prefix, suffix);
    }

    private static void deleteQuietly(Path path) {
        if (path != null) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // Best-effort cleanup of temp file
            }
        }
    }

    /**
     * Holds the processed temporary file on disk and metadata about the resize operation.
     * Closing this resource deletes the underlying temporary file.
     */
    @Getter
    public static final class ProcessedImage implements AutoCloseable {
        private final Path tempFile;
        private final String format;
        private final int originalWidth;
        private final int originalHeight;
        private final int width;
        private final int height;
        private final boolean resized;

        public ProcessedImage(
                Path tempFile,
                String format,
                int originalWidth,
                int originalHeight,
                int width,
                int height,
                boolean resized
        ) {
            this.tempFile = tempFile;
            this.format = format;
            this.originalWidth = originalWidth;
            this.originalHeight = originalHeight;
            this.width = width;
            this.height = height;
            this.resized = resized;
        }

        public File toFile() {
            return tempFile.toFile();
        }

        public byte[] readBytes() throws IOException {
            return Files.readAllBytes(tempFile);
        }

        public long sizeBytes() throws IOException {
            return Files.size(tempFile);
        }

        @Override
        public void close() {
            deleteQuietly(tempFile);
        }
    }
}
