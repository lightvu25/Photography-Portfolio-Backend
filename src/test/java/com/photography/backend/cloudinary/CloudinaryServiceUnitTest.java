package com.photography.backend.cloudinary;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.photography.backend.common.exception.BusinessException;
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
import java.io.InputStream;
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
                "Files <= 9.5MB must be uploaded with 100% untouched original bytes");
    }

    @Test
    void uploadLargeResolutionJpegUnderLimit_preservesOriginalResolutionAndBytes() throws IOException {
        byte[] highResJpeg = createSampleJpeg(3600, 2400);
        assertTrue(highResJpeg.length < CloudinaryService.SAFE_TARGET_BYTES);
        MockMultipartFile file = new MockMultipartFile(
                "file", "studio-4k.jpg", "image/jpeg", highResJpeg);

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/v1/studio-4k.jpg",
                "public_id", "portfolio/studio-4k"
        ));

        cloudinaryService.uploadFile(file, "portfolio/studio");

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(uploader).upload(bytesCaptor.capture(), anyMap());
        assertArrayEquals(highResJpeg, bytesCaptor.getValue(),
                "High-resolution JPEG under 9.5MB must not be re-encoded or downscaled");
    }

    @Test
    void uploadOversizedImage_downscalesFromStreamWithoutCallingGetBytes() throws IOException {
        byte[] sourceJpeg = createSampleJpeg(4000, 3000);
        MultipartFile oversizedFile = mock(MultipartFile.class);
        when(oversizedFile.getSize()).thenReturn(CloudinaryService.SAFE_TARGET_BYTES + 2_000_000L);
        when(oversizedFile.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(sourceJpeg));

        when(uploader.upload(any(byte[].class), anyMap())).thenReturn(Map.of(
                "secure_url", "https://res.cloudinary.com/demo/image/upload/v1/downscaled.jpg",
                "public_id", "portfolio/downscaled"
        ));

        cloudinaryService.uploadFile(oversizedFile, "portfolio/ngoai-canh");

        // Verify getBytes() was NEVER called for an oversized (>9.5MB) file to avoid duplicate heap byte[]
        verify(oversizedFile, never()).getBytes();

        ArgumentCaptor<byte[]> bytesCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(uploader).upload(bytesCaptor.capture(), anyMap());
        byte[] uploadedBytes = bytesCaptor.getValue();
        assertTrue(uploadedBytes.length <= CloudinaryService.SAFE_TARGET_BYTES);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(uploadedBytes));
        assertNotNull(decoded);
        assertEquals(3200, decoded.getWidth());
        assertEquals(2400, decoded.getHeight());
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
