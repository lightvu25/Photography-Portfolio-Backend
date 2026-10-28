package com.photography.backend.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.photography.backend.common.exception.BusinessException;
import com.photography.backend.gallery.dto.PhotoCompleteUploadRequest;
import com.photography.backend.gallery.dto.PhotoUploadSignatureResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CloudinaryServiceUnitTest {

    @Mock
    private Cloudinary cloudinary;

    @Mock
    private Uploader uploader;

    private CloudinaryService cloudinaryService;

    @BeforeEach
    void setUp() {
        lenient().when(cloudinary.uploader()).thenReturn(uploader);
        cloudinaryService = new CloudinaryService(cloudinary, 2, 100L);
    }

    @Test
    void uploadSmallImage_preservesExactOriginalBytesAndFormat() throws IOException {
        byte[] originalBytes = createSampleJpeg(800, 600);
        MockMultipartFile file = new MockMultipartFile(
                "file", "portrait.jpg", "image/jpeg", originalBytes);

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/v1/portrait.jpg",
                "public_id", "portfolio/portrait"
        ));

        Map<String, Object> result = cloudinaryService.uploadFile(file, "portfolio/chan-dung");

        assertEquals("portfolio/portrait", result.get("public_id"));
        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(uploader).upload(bytesCaptor.capture(), anyMap());
        assertArrayEquals(originalBytes, bytesCaptor.getValue(),
                "Images <= 2560px must preserve 100% untouched original bytes");
    }

    @Test
    void uploadOversizedImage_resizesLongestEdgeTo2560PreservingAspectRatio() throws IOException {
        byte[] highResJpeg = createSampleJpeg(3840, 2560);
        MockMultipartFile file = new MockMultipartFile(
                "file", "camera-original.jpg", "image/jpeg", highResJpeg);

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/v1/camera-original.jpg",
                "public_id", "portfolio/camera-original"
        ));

        cloudinaryService.uploadFile(file, "portfolio/ngoai-canh");

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(uploader).upload(bytesCaptor.capture(), anyMap());
        BufferedImage uploadedImg = ImageIO.read(new ByteArrayInputStream(bytesCaptor.getValue()));
        assertNotNull(uploadedImg);
        assertEquals(2560, uploadedImg.getWidth());
        assertEquals(1707, uploadedImg.getHeight());
    }

    @Test
    void concurrentUploads_enforcesMaxConcurrencyTwoAndRejectsThirdWith429WithoutReadingBytes() throws Exception {
        CloudinaryService strictService = new CloudinaryService(cloudinary, 2, 80L);

        CountDownLatch twoEnteredUpload = new CountDownLatch(2);
        CountDownLatch allowPairToFinish = new CountDownLatch(1);
        AtomicInteger activeInCloudinary = new AtomicInteger(0);
        AtomicInteger maxObservedActive = new AtomicInteger(0);

        when(uploader.upload(any(byte[].class), anyMap())).thenAnswer(inv -> {
            int active = activeInCloudinary.incrementAndGet();
            maxObservedActive.accumulateAndGet(active, Math::max);
            twoEnteredUpload.countDown();
            allowPairToFinish.await(5, TimeUnit.SECONDS);
            activeInCloudinary.decrementAndGet();
            return Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/ok.jpg", "public_id", "ok");
        });

        byte[] imgBytes = createSampleJpeg(400, 300);
        MockMultipartFile firstFile = new MockMultipartFile("file", "first.jpg", "image/jpeg", imgBytes);
        MockMultipartFile secondFile = new MockMultipartFile("file", "second.jpg", "image/jpeg", imgBytes);
        MultipartFile thirdFile = mock(MultipartFile.class);

        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<Map<String, Object>> firstFuture = pool.submit(
                    () -> strictService.uploadFile(firstFile, "portfolio"));
            Future<Map<String, Object>> secondFuture = pool.submit(
                    () -> strictService.uploadFile(secondFile, "portfolio"));

            assertTrue(twoEnteredUpload.await(2, TimeUnit.SECONDS), "Both uploads should enter Cloudinary uploader concurrently");

            // While 2 uploads hold the 2 semaphore permits, a 3rd upload must time out with 429 UPLOAD_BUSY
            BusinessException busyEx = assertThrows(BusinessException.class,
                    () -> strictService.uploadFile(thirdFile, "portfolio"));
            assertEquals("UPLOAD_BUSY", busyEx.getErrorCode());
            assertEquals(HttpStatus.TOO_MANY_REQUESTS, busyEx.getStatus());

            // Crucial memory safety check: rejected 3rd request must NOT have read file bytes into RAM
            verify(thirdFile, never()).getBytes();
            verify(thirdFile, never()).getInputStream();

            allowPairToFinish.countDown();
            Map<String, Object> firstResult = firstFuture.get(2, TimeUnit.SECONDS);
            Map<String, Object> secondResult = secondFuture.get(2, TimeUnit.SECONDS);
            assertEquals("ok", firstResult.get("public_id"));
            assertEquals("ok", secondResult.get("public_id"));
            assertEquals(2, maxObservedActive.get());

            // Now that the pair finished and released permits, a subsequent upload succeeds
            MockMultipartFile fourthFile = new MockMultipartFile("file", "fourth.jpg", "image/jpeg", imgBytes);
            Map<String, Object> fourthResult = strictService.uploadFile(fourthFile, "portfolio");
            assertEquals("ok", fourthResult.get("public_id"));
        } finally {
            allowPairToFinish.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void uploadFailure_alwaysReleasesSemaphorePermitInFinally() throws IOException {
        byte[] imgBytes = createSampleJpeg(300, 200);
        MockMultipartFile file1 = new MockMultipartFile("file", "fail.jpg", "image/jpeg", imgBytes);
        MockMultipartFile file2 = new MockMultipartFile("file", "recover.jpg", "image/jpeg", imgBytes);

        when(uploader.upload(any(byte[].class), anyMap()))
                .thenThrow(new RuntimeException("Simulated Cloudinary timeout"))
                .thenReturn(Map.of("secure_url", "https://res.cloudinary.com/demo/image/upload/v1/recover.jpg", "public_id", "recover"));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> cloudinaryService.uploadFile(file1, "portfolio"));
        assertEquals("UPLOAD_FAILED", ex.getErrorCode());
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());

        // Subsequent upload must still acquire the permit immediately and succeed
        Map<String, Object> secondResult = cloudinaryService.uploadFile(file2, "portfolio");
        assertEquals("recover", secondResult.get("public_id"));
    }

    // ---------- Signed Direct Upload tests ----------

    @Test
    void createSignedUploadTicket_signsControlledParamsAndNeverExposesSecret() {
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "bau-cloud",
                "api_key", "123456789",
                "api_secret", "super-secret-do-not-expose"
        ));
        CloudinaryService realService = new CloudinaryService(realCloudinary);

        PhotoUploadSignatureResponse ticket = realService.createSignedUploadTicket(
                "haibau/photos", "image/jpeg", 4_500_000L);

        assertEquals("bau-cloud", ticket.getCloudName());
        assertEquals("123456789", ticket.getApiKey());
        assertEquals("haibau/photos", ticket.getFolder());
        assertNotNull(ticket.getPublicId());
        assertFalse(ticket.getPublicId().isBlank());
        assertTrue(ticket.getTimestamp() > 0L);
        assertEquals("https://api.cloudinary.com/v1_1/bau-cloud/image/upload", ticket.getUploadUrl());
        assertNotNull(ticket.getSignature());
        assertFalse(ticket.getSignature().contains("super-secret-do-not-expose"));

        String expectedSig = realCloudinary.apiSignRequest(
                Map.of(
                        "folder", "haibau/photos",
                        "public_id", ticket.getPublicId(),
                        "timestamp", ticket.getTimestamp()
                ),
                "super-secret-do-not-expose"
        );
        assertEquals(expectedSig, ticket.getSignature());
    }

    @Test
    void createSignedUploadTicket_rejectsUnsupportedMimeOrInvalidSize() {
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "bau-cloud",
                "api_key", "123456789",
                "api_secret", "super-secret"
        ));
        CloudinaryService realService = new CloudinaryService(realCloudinary);

        BusinessException badMime = assertThrows(BusinessException.class,
                () -> realService.createSignedUploadTicket("haibau/photos", "application/pdf", 1024L));
        assertEquals("UNSUPPORTED_IMAGE_FORMAT", badMime.getErrorCode());

        BusinessException badSize = assertThrows(BusinessException.class,
                () -> realService.createSignedUploadTicket("haibau/photos", "image/png", 0L));
        assertEquals("INVALID_FILE_SIZE", badSize.getErrorCode());
    }

    @Test
    void verifyAndBuildSecureUrl_acceptsValidSignatureAndBuildsTrustedCanonicalUrl() {
        String secret = "my-cloudinary-secret";
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "bau-cloud",
                "api_key", "123456789",
                "api_secret", secret
        ));
        CloudinaryService realService = new CloudinaryService(realCloudinary);

        String publicId = "haibau/photos/a1b2c3d4-e5f6";
        long version = 1730140000L;
        String validResponseSig = realCloudinary.apiSignRequest(
                Map.of("public_id", publicId, "version", String.valueOf(version)),
                secret
        );

        PhotoCompleteUploadRequest req = new PhotoCompleteUploadRequest();
        req.setPublicId(publicId);
        req.setVersion(version);
        req.setSignature(validResponseSig);
        req.setResourceType("image");
        req.setFormat("jpeg");
        req.setBytes(5_200_000L);
        req.setWidth(6000);
        req.setHeight(4000);
        req.setSecureUrl("https://res.cloudinary.com/bau-cloud/image/upload/v1730140000/haibau/photos/a1b2c3d4-e5f6.jpg");

        String canonicalUrl = realService.verifyAndBuildSecureUrl("haibau/photos", req);
        assertEquals(
                "https://res.cloudinary.com/bau-cloud/image/upload/v1730140000/haibau/photos/a1b2c3d4-e5f6.jpg",
                canonicalUrl
        );
    }

    @Test
    void verifyAndBuildSecureUrl_rejectsForgedSignatureWrongFolderOrUntrustedUrl() {
        String secret = "my-cloudinary-secret";
        Cloudinary realCloudinary = new Cloudinary(Map.of(
                "cloud_name", "bau-cloud",
                "api_key", "123456789",
                "api_secret", secret
        ));
        CloudinaryService realService = new CloudinaryService(realCloudinary);

        String publicId = "haibau/photos/valid-id";
        long version = 1730140000L;
        String validSig = realCloudinary.apiSignRequest(
                Map.of("public_id", publicId, "version", String.valueOf(version)),
                secret
        );

        // 1. Forged signature
        PhotoCompleteUploadRequest forged = new PhotoCompleteUploadRequest();
        forged.setPublicId(publicId);
        forged.setVersion(version);
        forged.setSignature("0000000000000000000000000000000000000000");
        forged.setFormat("jpg");
        forged.setBytes(1000L);
        forged.setWidth(800);
        forged.setHeight(600);
        BusinessException sigEx = assertThrows(BusinessException.class,
                () -> realService.verifyAndBuildSecureUrl("haibau/photos", forged));
        assertEquals("INVALID_UPLOAD_SIGNATURE", sigEx.getErrorCode());

        // 2. Outside allowed folder
        PhotoCompleteUploadRequest wrongFolder = new PhotoCompleteUploadRequest();
        wrongFolder.setPublicId("other/folder/asset");
        wrongFolder.setVersion(version);
        wrongFolder.setSignature(validSig);
        wrongFolder.setFormat("jpg");
        wrongFolder.setBytes(1000L);
        wrongFolder.setWidth(800);
        wrongFolder.setHeight(600);
        BusinessException folderEx = assertThrows(BusinessException.class,
                () -> realService.verifyAndBuildSecureUrl("haibau/photos", wrongFolder));
        assertEquals("INVALID_PUBLIC_ID", folderEx.getErrorCode());

        // 3. Mismatched external URL
        PhotoCompleteUploadRequest untrustedUrl = new PhotoCompleteUploadRequest();
        untrustedUrl.setPublicId(publicId);
        untrustedUrl.setVersion(version);
        untrustedUrl.setSignature(validSig);
        untrustedUrl.setFormat("jpg");
        untrustedUrl.setBytes(1000L);
        untrustedUrl.setWidth(800);
        untrustedUrl.setHeight(600);
        untrustedUrl.setSecureUrl("https://evil.example.com/bau-cloud/image/upload/v1730140000/haibau/photos/valid-id.jpg");
        BusinessException urlEx = assertThrows(BusinessException.class,
                () -> realService.verifyAndBuildSecureUrl("haibau/photos", untrustedUrl));
        assertEquals("INVALID_UPLOAD_URL", urlEx.getErrorCode());
    }

    private static byte[] createSampleJpeg(int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(45, 90, 65));
        g.fillRect(0, 0, width, height);
        g.setColor(new Color(220, 195, 150));
        g.fillOval(width / 4, height / 4, width / 2, height / 2);
        g.dispose();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", bos);
        img.flush();
        return bos.toByteArray();
    }
}
