package com.atguigu.process.attachment;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import org.mockito.ArgumentCaptor;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class S3AttachmentObjectStoreTest {

    private final S3Client client = mock(S3Client.class);
    private final S3Presigner presigner = mock(S3Presigner.class);
    private final AttachmentS3Properties properties = new AttachmentS3Properties();
    private final S3AttachmentObjectStore store =
            new S3AttachmentObjectStore(client, presigner, properties);

    @Test
    void presignsAnUploadForTheConfiguredBucketWithTheRequestedType() throws MalformedURLException {
        PresignedPutObjectRequest presigned = mock(PresignedPutObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://s3.test/put"));
        when(presigner.presignPutObject(any(PutObjectPresignRequest.class))).thenReturn(presigned);

        String url = store.presignUpload("pending/abc", "text/plain", Duration.ofMinutes(5));

        assertEquals("https://s3.test/put", url);
        ArgumentCaptor<PutObjectPresignRequest> captor =
                ArgumentCaptor.forClass(PutObjectPresignRequest.class);
        verify(presigner).presignPutObject(captor.capture());
        PutObjectRequest request = captor.getValue().putObjectRequest();
        assertEquals("guigu-oa-attachments", request.bucket());
        assertEquals("pending/abc", request.key());
        assertEquals("text/plain", request.contentType());
        assertEquals(Duration.ofMinutes(5), captor.getValue().signatureDuration());
    }

    @Test
    void headsObjectsAndMapsSizeAndType() {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
                .contentLength(12L)
                .contentType("application/pdf")
                .build());

        AttachmentObjectStore.StoredObject stored = store.head("pending/abc");

        assertEquals(12L, stored.sizeBytes());
        assertEquals("application/pdf", stored.contentType());
        ArgumentCaptor<HeadObjectRequest> captor = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(client).headObject(captor.capture());
        assertEquals("guigu-oa-attachments", captor.getValue().bucket());
        assertEquals("pending/abc", captor.getValue().key());
    }

    @Test
    void copiesAndDeletesWithinTheConfiguredBucket() {
        store.copy("pending/a", "attachments/a");
        store.delete("pending/a");

        ArgumentCaptor<CopyObjectRequest> copy = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(client).copyObject(copy.capture());
        assertEquals("guigu-oa-attachments", copy.getValue().sourceBucket());
        assertEquals("pending/a", copy.getValue().sourceKey());
        assertEquals("guigu-oa-attachments", copy.getValue().destinationBucket());
        assertEquals("attachments/a", copy.getValue().destinationKey());

        ArgumentCaptor<DeleteObjectRequest> delete = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(delete.capture());
        assertEquals("guigu-oa-attachments", delete.getValue().bucket());
        assertEquals("pending/a", delete.getValue().key());
    }

    @Test
    void presignsADownloadWithAnAttachmentContentDisposition() throws MalformedURLException {
        PresignedGetObjectRequest presigned = mock(PresignedGetObjectRequest.class);
        when(presigned.url()).thenReturn(new URL("https://s3.test/get"));
        when(presigner.presignGetObject(any(GetObjectPresignRequest.class))).thenReturn(presigned);

        String url = store.presignDownload("attachments/a", "请假单.pdf", Duration.ofMinutes(15));

        assertEquals("https://s3.test/get", url);
        ArgumentCaptor<GetObjectPresignRequest> captor =
                ArgumentCaptor.forClass(GetObjectPresignRequest.class);
        verify(presigner).presignGetObject(captor.capture());
        assertEquals("attachments/a", captor.getValue().getObjectRequest().key());
        assertEquals(Duration.ofMinutes(15), captor.getValue().signatureDuration());
        String disposition = captor.getValue().getObjectRequest().responseContentDisposition();
        assertTrue(disposition.startsWith("attachment"));
        assertTrue(disposition.contains("UTF-8"));
    }
}
