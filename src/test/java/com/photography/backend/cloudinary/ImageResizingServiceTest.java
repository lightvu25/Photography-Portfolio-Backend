package com.photography.backend.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.controller.AdminPhotoController;
import com.photography.backend.gallery.entity.Category;
import com.photography.backend.gallery.entity.Photo;
import com.photography.backend.gallery.entity.PhotoStatus;
import com.photography.backend.gallery.repository.CategoryRepository;
import com.photography.backend.gallery.repository.PhotoRepository;
import com.photography.backend.gallery.service.GalleryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ImageResizingServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    @Mock
    private PhotoRepository photoRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private ImageProcessingProperties defaultProps;
    private ImageResizingService resizingService;
    private CloudinaryService cloudinaryService;
    private GalleryService galleryService;
    private AdminPhotoController adminPhotoController;

    @BeforeEach
    void setUp() {
        defaultProps = new ImageProcessingProperties(2560, 20_000, 100_000_000L);
        resizingService = new ImageResizingService(defaultProps, tempDir);
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        cloudinaryService = new CloudinaryService(cloudinary, resizingService, 2, 5_000L);
        galleryService = new GalleryService(photoRepository, categoryRepository, cloudinaryService);
        adminPhotoController = new AdminPhotoController(galleryService);
    }

    // -------------------------------------------------------------------------
    // 1. Image dimensions below the maximum: no enlargement, untouched bytes
    // -------------------------------------------------------------------------
    @Test
    void case1_belowMaximum_neverEnlargedAndPreservesOriginalBytes() throws IOException {
        byte[] originalJpeg = createJpegBytes(1200, 800);
        MockMultipartFile file = new MockMultipartFile("file", "small.jpg", "image/jpeg", originalJpeg);

        try (ImageResizingService.ProcessedImage processed = resizingService.process(file)) {
            assertFalse(processed.isResized(), "Image below 2560px must not be marked resized");
            assertEquals("jpeg", processed.getFormat());
            assertEquals(1200, processed.getWidth());
            assertEquals(800, processed.getHeight());
            assertArrayEquals(originalJpeg, processed.readBytes(),
                    "Image below maxLongEdge must not be re-encoded or modified");
        }
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 2. Image dimensions above the maximum: longest edge equals 2560
    // -------------------------------------------------------------------------
    @Test
    void case2_aboveMaximum_longestEdgeEqualsConfiguredMax2560() throws IOException {
        byte[] largeJpeg = createJpegBytes(3840, 2160);
        MockMultipartFile file = new MockMultipartFile("file", "uhd.jpg", "image/jpeg", largeJpeg);

        try (ImageResizingService.ProcessedImage processed = resizingService.process(file)) {
            assertTrue(processed.isResized());
            assertEquals("jpeg", processed.getFormat());
            assertEquals(2560, Math.max(processed.getWidth(), processed.getHeight()));
            assertEquals(2560, processed.getWidth());
            assertEquals(1440, processed.getHeight());

            BufferedImage decodedOut = ImageIO.read(new ByteArrayInputStream(processed.readBytes()));
            assertNotNull(decodedOut);
            assertEquals(2560, decodedOut.getWidth());
            assertEquals(1440, decodedOut.getHeight());
        }
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 3. Landscape image: aspect ratio preserved
    // -------------------------------------------------------------------------
    @Test
    void case3_landscapeImage_preservesAspectRatio() throws IOException {
        // 4000 x 2500 (aspect ratio 1.60) -> 2560 x 1600 (aspect ratio 1.60)
        byte[] landscapeJpeg = createJpegBytes(4000, 2500);
        MockMultipartFile file = new MockMultipartFile("file", "landscape.jpg", "image/jpeg", landscapeJpeg);

        try (ImageResizingService.ProcessedImage processed = resizingService.process(file)) {
            assertTrue(processed.isResized());
            assertEquals(2560, processed.getWidth());
            assertEquals(1600, processed.getHeight());

            BufferedImage out = ImageIO.read(new ByteArrayInputStream(processed.readBytes()));
            assertEquals(2560, out.getWidth());
            assertEquals(1600, out.getHeight());
            assertEquals(4000.0 / 2500.0, (double) out.getWidth() / out.getHeight(), 0.001);
        }
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 4. Portrait image: aspect ratio preserved
    // -------------------------------------------------------------------------
    @Test
    void case4_portraitImage_preservesAspectRatio() throws IOException {
        // 2400 x 3600 (2:3 portrait) -> 1707 x 2560
        byte[] portraitJpeg = createJpegBytes(2400, 3600);
        MockMultipartFile file = new MockMultipartFile("file", "portrait.jpg", "image/jpeg", portraitJpeg);

        try (ImageResizingService.ProcessedImage processed = resizingService.process(file)) {
            assertTrue(processed.isResized());
            assertEquals(1707, processed.getWidth());
            assertEquals(2560, processed.getHeight());

            BufferedImage out = ImageIO.read(new ByteArrayInputStream(processed.readBytes()));
            assertEquals(1707, out.getWidth());
            assertEquals(2560, out.getHeight());
            assertEquals(2400.0 / 3600.0, (double) out.getWidth() / out.getHeight(), 0.001);
        }
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 5. Square image: both dimensions handled correctly
    // -------------------------------------------------------------------------
    @Test
    void case5_squareImage_bothDimensionsEqualMaxLongEdge() throws IOException {
        byte[] squareJpeg = createJpegBytes(3200, 3200);
        MockMultipartFile file = new MockMultipartFile("file", "square.jpg", "image/jpeg", squareJpeg);

        try (ImageResizingService.ProcessedImage processed = resizingService.process(file)) {
            assertTrue(processed.isResized());
            assertEquals(2560, processed.getWidth());
            assertEquals(2560, processed.getHeight());

            BufferedImage out = ImageIO.read(new ByteArrayInputStream(processed.readBytes()));
            assertEquals(2560, out.getWidth());
            assertEquals(2560, out.getHeight());
        }
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 6. Transparent PNG: transparency and format preserved (even with misleading filename)
    // -------------------------------------------------------------------------
    @Test
    void case6_transparentPng_preservesTransparencyAndFormatEvenWithMisleadingExtension() throws IOException {
        byte[] rgbaPng = createTransparentPngBytes(3200, 2400);
        // Intentionally pass a misleading ".jpg" filename to verify binary format inspection
        MockMultipartFile file = new MockMultipartFile("file", "misleading-name.jpg", "image/jpeg", rgbaPng);

        try (ImageResizingService.ProcessedImage processed = resizingService.process(file)) {
            assertTrue(processed.isResized());
            assertEquals("png", processed.getFormat(), "Must detect PNG from binary header, not filename");
            assertEquals(2560, processed.getWidth());
            assertEquals(1920, processed.getHeight());

            byte[] outBytes = processed.readBytes();
            assertEquals("png", detectBinaryFormat(outBytes), "Output binary stream must remain PNG");

            BufferedImage out = ImageIO.read(new ByteArrayInputStream(outBytes));
            assertNotNull(out);
            assertTrue(out.getColorModel().hasAlpha(), "Resized PNG must retain alpha channel");

            // Top-left corner was drawn fully transparent (alpha == 0)
            int cornerArgb = out.getRGB(10, 10);
            int cornerAlpha = (cornerArgb >>> 24) & 0xFF;
            assertEquals(0, cornerAlpha, "Transparent region must remain fully transparent (alpha=0)");

            // Center was drawn opaque green (alpha == 255)
            int centerArgb = out.getRGB(out.getWidth() / 2, out.getHeight() / 2);
            int centerAlpha = (centerArgb >>> 24) & 0xFF;
            assertEquals(255, centerAlpha, "Opaque center must remain opaque (alpha=255)");
        }
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 7. Unsupported or malformed file: meaningful error
    // -------------------------------------------------------------------------
    @Test
    void case7_unsupportedOrMalformedFile_throwsMeaningfulBusinessException() throws IOException {
        // 7a. Non-image text payload masquerading as .jpg
        MockMultipartFile fakeImage = new MockMultipartFile(
                "file", "fake.jpg", "image/jpeg", "This is plain text, not an image".getBytes(StandardCharsets.UTF_8));
        BusinessException unsupportedEx = assertThrows(BusinessException.class,
                () -> resizingService.process(fakeImage));
        assertEquals("UNSUPPORTED_IMAGE_FORMAT", unsupportedEx.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, unsupportedEx.getStatus());

        // 7b. Truncated/corrupt JPEG header
        byte[] truncatedJpeg = new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'};
        MockMultipartFile corruptFile = new MockMultipartFile(
                "file", "corrupt.jpg", "image/jpeg", truncatedJpeg);
        BusinessException invalidEx = assertThrows(BusinessException.class,
                () -> resizingService.process(corruptFile));
        assertTrue(Set.of("INVALID_IMAGE", "UNSUPPORTED_IMAGE_FORMAT").contains(invalidEx.getErrorCode()));
        assertEquals(HttpStatus.BAD_REQUEST, invalidEx.getStatus());

        // 7c. Empty file
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.jpg", "image/jpeg", new byte[0]);
        BusinessException emptyEx = assertThrows(BusinessException.class,
                () -> resizingService.process(emptyFile));
        assertEquals("FILE_REQUIRED", emptyEx.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, emptyEx.getStatus());

        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 8. Cloudinary upload failure: no invalid database record created
    // -------------------------------------------------------------------------
    @Test
    void case8_cloudinaryUploadFailure_doesNotCreateDatabaseRecordAndCleansUpTempFiles() throws IOException {
        byte[] oversizedJpeg = createJpegBytes(3000, 2000);
        MockMultipartFile file = new MockMultipartFile("file", "fail-upload.jpg", "image/jpeg", oversizedJpeg);

        when(uploader.upload(any(byte[].class), anyMap()))
                .thenThrow(new RuntimeException("Invalid API key or 403 Forbidden"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> galleryService.uploadPhoto(file, "Portrait", "Desc", "Alt", 1, null));

        assertEquals("UPLOAD_FAILED", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        verify(photoRepository, never()).save(any());
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 9. Image-processing failure: temporary resources cleaned up
    // -------------------------------------------------------------------------
    @Test
    void case9_imageProcessingFailure_cleansUpAllTemporaryFiles() throws IOException {
        // Valid PNG header with corrupt IDAT body that fails during pixel decode
        byte[] corruptPng = createDimensionOnlyPngHeader(1000, 800);
        MockMultipartFile file = new MockMultipartFile("file", "corrupt-body.png", "image/png", corruptPng);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cloudinaryService.uploadFile(file, "haibau/photos"));

        assertEquals("INVALID_IMAGE", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(uploader, never()).upload(any(byte[].class), anyMap());
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 10. Existing upload response contract: unchanged
    // -------------------------------------------------------------------------
    @Test
    void case10_existingUploadResponseContract_preservedEndToEnd() throws IOException {
        byte[] oversizedJpeg = createJpegBytes(3600, 2400);
        MockMultipartFile file = new MockMultipartFile("file", "session.jpg", "image/jpeg", oversizedJpeg);

        Category cat = new Category();
        cat.setId(5L);
        cat.setName("Ngoại cảnh");
        cat.setSlug("ngoai-canh");
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(cat));

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/v1/haibau/photos/resized123.jpg",
                "public_id", "haibau/photos/resized123"
        ));

        when(photoRepository.save(any(Photo.class))).thenAnswer(inv -> {
            Photo p = inv.getArgument(0);
            p.setId(42L);
            return p;
        });

        Photo saved = galleryService.uploadPhoto(
                file, "Chiều hoàng hôn", "Mô tả", "Alt text", 7, Set.of(5L),
                0.1, 0.1, 0.8, 0.8);

        assertNotNull(saved);
        assertEquals(42L, saved.getId());
        assertEquals("Chiều hoàng hôn", saved.getTitle());
        assertEquals(PhotoStatus.DRAFT, saved.getStatus());
        assertEquals("haibau/photos/resized123", saved.getCloudinaryPublicId());
        assertTrue(saved.getImageUrl().contains("c_crop,x_0.1,y_0.1,w_0.8,h_0.8"));
        assertTrue(saved.getThumbnailUrl().contains("w_600,c_limit,q_auto"));
        assertEquals(1, saved.getCategories().size());

        // Verify the bytes actually sent to Cloudinary were resized to 2560x1707
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(uploader).upload(bytesCaptor.capture(), anyMap());
        BufferedImage uploadedImg = ImageIO.read(new ByteArrayInputStream(bytesCaptor.getValue()));
        assertEquals(2560, uploadedImg.getWidth());
        assertEquals(1707, uploadedImg.getHeight());
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 11. Configuration override: custom maximum edge works & validates
    // -------------------------------------------------------------------------
    @Test
    void case11_configurationOverride_customMaxLongEdgeWorksAndInvalidConfigRejected() throws IOException {
        ImageProcessingProperties customProps = new ImageProcessingProperties(1600, 20_000, 100_000_000L);
        ImageResizingService customService = new ImageResizingService(customProps, tempDir);

        byte[] jpeg = createJpegBytes(3200, 2000);
        MockMultipartFile file = new MockMultipartFile("file", "custom.jpg", "image/jpeg", jpeg);

        try (ImageResizingService.ProcessedImage processed = customService.process(file)) {
            assertTrue(processed.isResized());
            assertEquals(1600, processed.getWidth());
            assertEquals(1000, processed.getHeight());

            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(processed.readBytes()));
            assertEquals(1600, decoded.getWidth());
            assertEquals(1000, decoded.getHeight());
        }

        // Invalid configuration values must fail validation
        assertThrows(IllegalArgumentException.class,
                () -> new ImageProcessingProperties(0, 20_000, 100_000_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new ImageProcessingProperties(-500, 20_000, 100_000_000L));
        assertThrows(IllegalArgumentException.class,
                () -> new ImageProcessingProperties(30_000, 20_000, 100_000_000L));

        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // 12. Very large / malicious decompression-bomb dimensions safely rejected
    // -------------------------------------------------------------------------
    @Test
    void case12_maliciousDecompressionBombDimensions_rejectedBeforePixelAllocation() throws IOException {
        // Craft a tiny PNG header declaring 25,000 x 25,000 pixels (625 MP = 2.5 GB uncompressed)
        byte[] bombHeaderPng = createDimensionOnlyPngHeader(25_000, 25_000);
        MockMultipartFile bombFile = new MockMultipartFile("file", "bomb.png", "image/png", bombHeaderPng);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> resizingService.process(bombFile));

        assertEquals("IMAGE_DIMENSIONS_TOO_LARGE", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTempDirEmpty();
    }

    // -------------------------------------------------------------------------
    // Bonus: EXIF Orientation 6 (90° CW) is preserved / baked into resized output
    // -------------------------------------------------------------------------
    @Test
    void exifOrientation6_rotatesSensorLandscapeToPortraitBeforeResizing() throws IOException {
        // Sensor raw dimensions 4000x2000 with EXIF Orientation 6 (90° CW) -> visual 2000x4000 -> resized 1280x2560
        byte[] rawJpeg = createJpegBytes(4000, 2000);
        byte[] jpegWithExif6 = injectExifOrientation(rawJpeg, 6);
        MockMultipartFile file = new MockMultipartFile("file", "exif6.jpg", "image/jpeg", jpegWithExif6);

        try (ImageResizingService.ProcessedImage processed = resizingService.process(file)) {
            assertTrue(processed.isResized());
            assertEquals(1280, processed.getWidth());
            assertEquals(2560, processed.getHeight());

            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(processed.readBytes()));
            assertEquals(1280, decoded.getWidth());
            assertEquals(2560, decoded.getHeight());
        }
        assertTempDirEmpty();
    }

    // =========================================================================
    // Test Helpers
    // =========================================================================

    private void assertTempDirEmpty() throws IOException {
        try (Stream<Path> entries = Files.list(tempDir)) {
            List<Path> remaining = entries.toList();
            assertTrue(remaining.isEmpty(),
                    "Expected temporary directory to be empty, but found: " + remaining);
        }
    }

    private static byte[] createJpegBytes(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setColor(new Color(42, 85, 60));
            g.fillRect(0, 0, width, height);
            g.setColor(new Color(230, 200, 160));
            g.fillOval(width / 4, height / 4, width / 2, height / 2);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", bos);
        img.flush();
        return bos.toByteArray();
    }

    private static byte[] createTransparentPngBytes(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            // Clear entire canvas to transparent (alpha = 0)
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, width, height);

            // Draw an opaque circle in the center (alpha = 255)
            g.setComposite(AlphaComposite.SrcOver);
            g.setColor(new Color(80, 140, 95, 255));
            g.fillOval(width / 4, height / 4, width / 2, height / 2);
        } finally {
            g.dispose();
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bos);
        img.flush();
        return bos.toByteArray();
    }

    private static String detectBinaryFormat(byte[] bytes) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            assertTrue(readers.hasNext(), "Expected readable image format");
            ImageReader r = readers.next();
            try {
                return r.getFormatName().toLowerCase();
            } finally {
                r.dispose();
            }
        }
    }

    /**
     * Builds a minimal PNG byte array with a valid signature and IHDR chunk declaring
     * {@code width} x {@code height} so {@code ImageReader.getWidth(0)} / {@code getHeight(0)}
     * read the header without needing to allocate {@code width * height} pixels in the test.
     */
    private static byte[] createDimensionOnlyPngHeader(int width, int height) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(bos);
        // PNG 8-byte signature
        dos.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});

        // IHDR chunk (13 bytes payload)
        ByteArrayOutputStream ihdrPayload = new ByteArrayOutputStream();
        DataOutputStream ihdr = new DataOutputStream(ihdrPayload);
        ihdr.writeInt(width);
        ihdr.writeInt(height);
        ihdr.writeByte(8); // bit depth
        ihdr.writeByte(6); // color type RGBA
        ihdr.writeByte(0); // compression
        ihdr.writeByte(0); // filter
        ihdr.writeByte(0); // interlace
        byte[] ihdrBytes = ihdrPayload.toByteArray();

        dos.writeInt(ihdrBytes.length);
        dos.writeBytes("IHDR");
        dos.write(ihdrBytes);

        CRC32 crc = new CRC32();
        crc.update("IHDR".getBytes(StandardCharsets.US_ASCII));
        crc.update(ihdrBytes);
        dos.writeInt((int) crc.getValue());
        dos.flush();
        return bos.toByteArray();
    }

    /** Injects a minimal big-endian TIFF EXIF APP1 orientation segment right after JPEG SOI (FF D8). */
    private static byte[] injectExifOrientation(byte[] jpegBytes, int orientation) throws IOException {
        ByteArrayOutputStream app1 = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(app1);
        // "Exif\0\0"
        dos.write(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        // Big-endian TIFF header: "MM", 0x002A, offset to IFD0 = 8
        dos.write(new byte[]{'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08});
        // Number of directory entries = 1
        dos.writeShort(1);
        // Tag = 0x0112 (Orientation), Type = 3 (SHORT), Count = 1, Value = orientation (left-aligned in 4 bytes)
        dos.writeShort(0x0112);
        dos.writeShort(3);
        dos.writeInt(1);
        dos.writeShort(orientation);
        dos.writeShort(0);
        // Next IFD offset = 0
        dos.writeInt(0);
        dos.flush();

        byte[] app1Payload = app1.toByteArray();
        int segmentLen = app1Payload.length + 2;

        ByteArrayOutputStream out = new ByteArrayOutputStream(jpegBytes.length + segmentLen + 2);
        out.write(0xFF);
        out.write(0xD8);
        out.write(0xFF);
        out.write(0xE1);
        out.write((segmentLen >>> 8) & 0xFF);
        out.write(segmentLen & 0xFF);
        out.write(app1Payload);
        out.write(jpegBytes, 2, jpegBytes.length - 2);
        return out.toByteArray();
    }
}
