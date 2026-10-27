package com.photography.backend.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.photography.backend.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class CloudinaryService {

    /** Cloudinary image upload hard limit is 10MB; keep a safety margin. */
    static final long SAFE_TARGET_BYTES = 9_500_000L;
    /** Maximum pixel count (100 MP) allowed for server-side BufferedImage decoding to prevent Heap OOM. */
    static final long MAX_DECODE_PIXELS = 100_000_000L;
    private static final int[] LONG_EDGE_CANDIDATES = {3200, 2560, 2048, 1600, 1024};

    private final Cloudinary cloudinary;
    private final Semaphore uploadSemaphore;
    private final long acquireTimeoutMs;

    @Autowired
    public CloudinaryService(
            Cloudinary cloudinary,
            @Value("${app.upload.max-concurrent:2}") int maxConcurrentUploads,
            @Value("${app.upload.acquire-timeout-ms:15000}") long acquireTimeoutMs
    ) {
        this.cloudinary = cloudinary;
        this.uploadSemaphore = new Semaphore(Math.max(1, maxConcurrentUploads), true);
        this.acquireTimeoutMs = Math.max(0L, acquireTimeoutMs);
    }

    public CloudinaryService(Cloudinary cloudinary) {
        this(cloudinary, 2, 15_000L);
    }

    public Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException {
        boolean acquired;
        try {
            acquired = uploadSemaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(
                    "Quá trình tải ảnh bị gián đoạn — vui lòng thử lại",
                    "UPLOAD_INTERRUPTED",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }

        if (!acquired) {
            log.warn("Upload concurrency limit reached; rejecting concurrent upload request for folder {}", folder);
            throw new BusinessException(
                    "Hệ thống đang xử lý ảnh khác, vui lòng thử lại sau giây lát",
                    "UPLOAD_BUSY",
                    HttpStatus.TOO_MANY_REQUESTS);
        }

        try {
            byte[] bytes = prepareUploadBytes(file);
            log.info("Uploading file to Cloudinary, folder: {}, size: {} bytes", folder, bytes.length);
            Map<String, Object> options = ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", "auto"
            );
            try {
                return cloudinary.uploader().upload(bytes, options);
            } catch (RuntimeException e) {
                // Preserve the real Cloudinary rejection (permissions, size, etc.)
                // instead of letting it surface as a generic 500.
                log.error("Cloudinary rejected upload: {}", e.getMessage());
                throw new BusinessException("Cloudinary upload failed: " + e.getMessage(),
                        "UPLOAD_FAILED", HttpStatus.BAD_GATEWAY);
            }
        } finally {
            uploadSemaphore.release();
        }
    }

    /**
     * Cloudinary rejects image uploads above 10MB.
     * - Files <= 9.5MB are returned as untouched original bytes (zero re-encoding,
     *   zero resolution/format modification, no BufferedImage allocation).
     * - Oversized files (> 9.5MB) have their image header inspected before pixel decoding
     *   and are downscaled from a stream without holding a duplicate raw byte[] in heap.
     */
    byte[] prepareUploadBytes(MultipartFile file) throws IOException {
        long fileSize = file.getSize();
        if (fileSize >= 0 && fileSize <= SAFE_TARGET_BYTES) {
            byte[] bytes = file.getBytes();
            if (bytes.length <= SAFE_TARGET_BYTES) {
                return bytes;
            }
        }

        BufferedImage source = decodeOversizedImageSafely(file);
        try {
            for (int edge : LONG_EDGE_CANDIDATES) {
                BufferedImage scaled = scale(source, edge);
                byte[] out;
                try {
                    out = encodeJpeg(scaled);
                } finally {
                    if (scaled != source) {
                        scaled.flush();
                    }
                }
                if (out.length <= SAFE_TARGET_BYTES) {
                    log.info("Downscaled {} bytes -> {} bytes (long edge {})", fileSize, out.length, edge);
                    return out;
                }
            }
        } finally {
            source.flush();
        }

        throw new BusinessException("Image is too large even after downscaling",
                "IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST);
    }

    private BufferedImage decodeOversizedImageSafely(MultipartFile file) throws IOException {
        try (InputStream in = file.getInputStream();
             ImageInputStream iis = ImageIO.createImageInputStream(in)) {
            if (iis == null) {
                throw new BusinessException(
                        "Image exceeds the 10MB upload limit and cannot be downscaled (unsupported format)",
                        "IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                throw new BusinessException(
                        "Image exceeds the 10MB upload limit and cannot be downscaled (unsupported format)",
                        "IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long totalPixels = (long) width * (long) height;
                if (width <= 0 || height <= 0 || totalPixels > MAX_DECODE_PIXELS) {
                    throw new BusinessException(
                            "Ảnh có độ phân giải quá lớn (" + width + "x" + height + ") không thể giải mã an toàn trên máy chủ",
                            "IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST);
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw new BusinessException(
                            "Image exceeds the 10MB upload limit and cannot be downscaled (unsupported format)",
                            "IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST);
                }
                return decoded;
            } finally {
                reader.dispose();
            }
        }
    }

    private BufferedImage scale(BufferedImage src, int longEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int max = Math.max(w, h);
        if (max <= longEdge) {
            return src;
        }
        double ratio = (double) longEdge / max;
        int nw = Math.max(1, (int) Math.round(w * ratio));
        int nh = Math.max(1, (int) Math.round(h * ratio));
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return out;
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream(256 * 1024);
            try (MemoryCacheImageOutputStream ios = new MemoryCacheImageOutputStream(bos)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(image, null, null), param);
                ios.flush();
            }
            return bos.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    /**
     * Deletes a Cloudinary asset. Returns normally only when the asset is gone:
     * a "not found" result is treated as success since the desired end state
     * (asset no longer exists) is already reached. Any other failure throws so
     * callers never silently lose track of a live asset.
     */
    public void deleteFile(String publicId) {
        try {
            log.info("Deleting file from Cloudinary: {}", publicId);
            Map<?, ?> result = cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            Object status = result.get("result");
            if (!"ok".equals(status) && !"not found".equals(status)) {
                throw new BusinessException("Cloudinary deletion failed: " + status,
                        "CLOUDINARY_DELETE_FAILED", HttpStatus.BAD_GATEWAY);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete file from Cloudinary: {}", publicId, e);
            throw new BusinessException("Cloudinary deletion failed",
                    "CLOUDINARY_DELETE_FAILED", HttpStatus.BAD_GATEWAY);
        }
    }
}
