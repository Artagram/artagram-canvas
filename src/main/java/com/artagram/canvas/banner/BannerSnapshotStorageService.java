package com.artagram.canvas.banner;

import java.net.URI;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Sube los snapshots PNG del banner a S3 (HU-16, latencia): el cliente carga
 * cada region como una peticion HTTP cacheable e independiente en vez de un
 * blob base64 embebido en el JSON de estado, que crecia con cada region del
 * mural. Postgres sigue siendo la fuente de verdad para el renderizado
 * incremental (BannerSnapshotJob necesita los bytes previos), S3 es solo el
 * camino de lectura para el cliente.
 */
@Component
public class BannerSnapshotStorageService {

    private static final Logger log = LoggerFactory.getLogger(BannerSnapshotStorageService.class);
    private static final Duration TTL_URL_PRESIGNADA = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public BannerSnapshotStorageService(
            @Value("${artagram.storage.s3.endpoint:}") String endpoint,
            @Value("${artagram.storage.s3.region:us-east-1}") String regionName,
            @Value("${artagram.storage.s3.bucket:artagram-uploads}") String bucket,
            @Value("${artagram.storage.s3.access-key:}") String accessKey,
            @Value("${artagram.storage.s3.secret-key:}") String secretKey,
            @Value("${artagram.storage.s3.path-style-access:true}") boolean pathStyleAccess) {
        this.bucket = bucket;
        AwsCredentialsProvider creds = (accessKey != null && !accessKey.isBlank())
                ? StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
                : DefaultCredentialsProvider.create();
        var region = Region.of(regionName);

        S3ClientBuilder clientBuilder = S3Client.builder()
                .region(region)
                .credentialsProvider(creds)
                .forcePathStyle(pathStyleAccess);
        if (endpoint != null && !endpoint.isBlank()) {
            clientBuilder.endpointOverride(URI.create(endpoint));
        }
        this.s3Client = clientBuilder.build();

        S3Presigner.Builder presignerBuilder = S3Presigner.builder()
                .region(region)
                .credentialsProvider(creds)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(pathStyleAccess)
                        .build());
        if (endpoint != null && !endpoint.isBlank()) {
            presignerBuilder.endpointOverride(URI.create(endpoint));
        }
        this.s3Presigner = presignerBuilder.build();

        asegurarBucket();
    }

    private void asegurarBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (NoSuchBucketException ex) {
            log.info("Creando bucket S3: {}", bucket);
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
            } catch (Exception e) {
                log.warn("No se pudo auto-crear bucket S3 {}: {}", bucket, e.getMessage());
            }
        } catch (Exception ex) {
            log.warn("No se pudo verificar bucket S3 {} (se continua igual): {}", bucket, ex.getMessage());
        }
    }

    public String claveDe(UUID bannerId, int regionIndex) {
        return "banners/" + bannerId + "/" + regionIndex + ".png";
    }

    /** @return true si la subida fue exitosa; false si fallo (el llamador no debe exponer esta key). */
    public boolean subir(String key, byte[] png) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType("image/png").build(),
                    RequestBody.fromBytes(png));
            return true;
        } catch (Exception ex) {
            log.warn("No se pudo subir snapshot a S3 (key={}), el cliente seguira usando el base64 de respaldo: {}",
                    key, ex.getMessage());
            return false;
        }
    }

    public String presignarUrl(String key) {
        try {
            var presigned = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(TTL_URL_PRESIGNADA)
                    .getObjectRequest(b -> b.bucket(bucket).key(key))
                    .build());
            return presigned.url().toString();
        } catch (Exception ex) {
            log.warn("No se pudo presignar URL de snapshot (key={}): {}", key, ex.getMessage());
            return null;
        }
    }
}
