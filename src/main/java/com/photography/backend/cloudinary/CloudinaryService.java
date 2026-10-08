package com.photography.backend.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.photography.backend.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class CloudinaryService {

    /** Cloudinary image upload hard limit is 10MB; keep a safety margin. */
    private static final long SAFE_TARGET_BYTES = 9_500_000L;
    private static final int[] LONG_EDGE_CANDIDATES = {3200, 2560, 2048, 1600, 1024};

    private final Cloudinary cloudinary;

    public Map<String, Object> uploadFile(MultipartFile file, String folder) throws IOException {
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
    }

    /**
     * Cloudinary rejects image uploads above 10MB. Real camera JPEGs are often
     * 10-25MB, so oversized images are downscaled and re-encoded as JPEG until
     * they fit comfortably under the limit.
     */
    private byte[] prepareUploadBytes(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        if (bytes.length <= SAFE_TARGET_BYTES) {
            return bytes;
        }

        BufferedImage source = ImageIO.read(new ByteArrayInputStream(bytes));
        if (source == null) {
            throw new BusinessException(
                    "Image exceeds the 10MB upload limit and cannot be downscaled (unsupported format)",
                    "IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST);
        }

        for (int edge : LONG_EDGE_CANDIDATES) {
            byte[] out = encodeJpeg(scale(source, edge));
            if (out.length <= SAFE_TARGET_BYTES) {
                log.info("Downscaled {} bytes -> {} bytes (long edge {})", bytes.length, out.length, edge);
                return out;
            }
        }

        byte[] last = encodeJpeg(scale(source, LONG_EDGE_CANDIDATES[LONG_EDGE_CANDIDATES.length - 1]));
        if (last.length <= SAFE_TARGET_BYTES) {
            return last;
        }
        throw new BusinessException("Image is too large even after downscaling",
                "IMAGE_TOO_LARGE", HttpStatus.BAD_REQUEST);
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
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(0.85f);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        writer.setOutput(new MemoryCacheImageOutputStream(bos));
        writer.write(null, new IIOImage(image, null, null), param);
        writer.dispose();
        return bos.toByteArray();
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
