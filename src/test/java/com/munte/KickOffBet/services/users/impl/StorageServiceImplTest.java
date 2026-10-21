package com.munte.KickOffBet.services.users.impl;

import com.munte.KickOffBet.domain.dto.api.response.StoredFile;
import com.munte.KickOffBet.exceptions.BusinessException;
import com.munte.KickOffBet.exceptions.StorageException;
import org.apache.tika.Tika;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceImplTest {

    private static final byte[] PNG_HEADER = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 'I', 'H', 'D', 'R',
            0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01, 0x08, 0x02, 0x00, 0x00, 0x00
    };
    private static final int FIVE_MB = 5 * 1024 * 1024;

    @Mock
    private S3Client s3Client;

    private StorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageService = new StorageServiceImpl(s3Client, new Tika());
        ReflectionTestUtils.setField(storageService, "publicUrl", "http://localhost:9000");
    }

    private static byte[] pngOfSize(int size) {
        byte[] bytes = Arrays.copyOf(PNG_HEADER, size);
        return bytes;
    }

    @Test
    void imageIsStoredUnderUniqueKeyAndPublicUrlIsReturned() {
        // client file name and Content-Type are ignored: key and stored type come from the detected bytes
        MockMultipartFile file = new MockMultipartFile("file", "../x/evil.html", "text/html", pngOfSize(1024));

        String url = storageService.uploadFile(file, "id-cards");

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
        PutObjectRequest put = captor.getValue();
        assertThat(put.bucket()).isEqualTo("id-cards");
        assertThat(put.key()).matches("[0-9a-f-]{36}\\.png");
        assertThat(put.contentType()).isEqualTo("image/png");
        assertThat(url).isEqualTo("http://localhost:9000/id-cards/" + put.key());
    }

    @Test
    void twoUploadsOfTheSameFileNameGetDifferentKeys() {
        MockMultipartFile file = new MockMultipartFile("file", "id.png", "image/png", pngOfSize(64));

        String first = storageService.uploadFile(file, "id-cards");
        String second = storageService.uploadFile(file, "id-cards");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void nonImageContentIsRejected() {
        MockMultipartFile pdf = new MockMultipartFile("file", "doc.pdf", "application/pdf",
                "%PDF-1.4\n1 0 obj\n<<>>\nendobj\n".getBytes(StandardCharsets.US_ASCII));
        MockMultipartFile svg = new MockMultipartFile("file", "crest.svg", "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storageService.uploadFile(pdf, "id-cards"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only images");
        assertThatThrownBy(() -> storageService.uploadFile(svg, "teams"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Only images");
        verifyNoInteractions(s3Client);
    }

    @Test
    void contentTypeIsDetectedFromBytesSoASpoofedHeaderDoesNotHelp() {
        MockMultipartFile disguised = new MockMultipartFile("file", "evil.png", "image/png",
                "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> storageService.uploadFile(disguised, "id-cards"))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(s3Client);
    }

    @Test
    void imageLargerThan5MbIsRejected() {
        MockMultipartFile big = new MockMultipartFile("file", "big.png", "image/png", pngOfSize(FIVE_MB + 1));

        assertThatThrownBy(() -> storageService.uploadFile(big, "id-cards"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5MB");
        verifyNoInteractions(s3Client);
    }

    @Test
    void imageOfExactly5MbIsAccepted() {
        MockMultipartFile limit = new MockMultipartFile("file", "limit.png", "image/png", pngOfSize(FIVE_MB));

        storageService.uploadFile(limit, "id-cards");

        verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void unreadableUploadIsReportedAsStorageError() throws IOException {
        MultipartFile broken = mock(MultipartFile.class);
        when(broken.getBytes()).thenThrow(new IOException("stream closed"));

        assertThatThrownBy(() -> storageService.uploadFile(broken, "teams"))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("teams");
        verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void deleteUsesTheObjectKeyFromThePublicUrl() {
        storageService.deleteFile("teams", "http://localhost:9000/teams/abc_crest.png");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("teams");
        assertThat(captor.getValue().key()).isEqualTo("abc_crest.png");
    }

    @Test
    void deleteFailureIsWrappedInStorageException() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder().message("boom").build());

        assertThatThrownBy(() -> storageService.deleteFile("teams", "http://x/teams/a.png"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void downloadReturnsBytesContentTypeAndOriginalFileName() {
        byte[] bytes = pngOfSize(32);
        GetObjectResponse response = GetObjectResponse.builder().contentType("image/png").build();
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenReturn(ResponseBytes.fromByteArray(response, bytes));

        StoredFile file = storageService.downloadFile("id-cards", "0b1c2d_my_card.png");

        assertThat(file.filename()).isEqualTo("my_card.png");
        assertThat(file.contentType()).isEqualTo("image/png");
        assertThat(file.bytes()).isEqualTo(bytes);
        ArgumentCaptor<GetObjectRequest> captor = ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(s3Client).getObjectAsBytes(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("id-cards");
        assertThat(captor.getValue().key()).isEqualTo("0b1c2d_my_card.png");
    }

    @Test
    void downloadFailureIsWrappedInStorageException() {
        when(s3Client.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow((S3Exception) S3Exception.builder().message("missing").build());

        assertThatThrownBy(() -> storageService.downloadFile("id-cards", "x.png"))
                .isInstanceOf(StorageException.class);
    }

    @Test
    void existingBucketIsNotRecreated() {
        storageService.createBucketIfNotExists("leagues");

        verify(s3Client).headBucket(any(HeadBucketRequest.class));
        verify(s3Client, never()).createBucket(any(CreateBucketRequest.class));
    }

    @Test
    void missingBucketIsCreated() {
        when(s3Client.headBucket(any(HeadBucketRequest.class)))
                .thenThrow(NoSuchBucketException.builder().message("no bucket").build());

        storageService.createBucketIfNotExists("leagues");

        ArgumentCaptor<CreateBucketRequest> captor = ArgumentCaptor.forClass(CreateBucketRequest.class);
        verify(s3Client).createBucket(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo("leagues");
    }
}
