package top.stellarium.common.utils;

import io.minio.*;
import io.minio.http.Method;
import io.minio.messages.Bucket;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;
import java.util.UUID;

/**
 * Minio工具类
 */

@Data
@AllArgsConstructor
public class MinioUtil {

    // 从配置文件读取参数
    private String url;
    private String accessKey;
    private String secretKey;
    private String bucketName;

    // 初始化MinIO客户端
    private MinioClient getMinioClient() {
        return MinioClient.builder()
                .endpoint(url)
                .credentials(accessKey, secretKey)
                .build();
    }

    /**
     * 检查存储桶是否存在，不存在则创建
     */
    public void checkAndCreateBucket() throws Exception {
        MinioClient client = getMinioClient();
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
        }
    }

    /**
     * 上传文件到MinIO
     * @param file 前端上传的文件
     * @return 上传后的文件访问路径（URL）
     */
    /**
     * 上传文件
     * @return String[] { "文件名(作为UUID使用)", "完整访问URL" }
     */
    public String[] uploadFile(MultipartFile file) throws Exception {
        checkAndCreateBucket();

        String originalFileName = file.getOriginalFilename();
        // 防止文件名为空
        if (originalFileName == null) {
            throw new RuntimeException("文件名不能为空");
        }

        String suffix = originalFileName.substring(originalFileName.lastIndexOf("."));
        String uuid = UUID.randomUUID().toString().replace("-", "");

        // 【关键修改】fileName 包含了后缀，例如 "550e8400e29b.jpg"
        String fileName = uuid + suffix;

        MinioClient client = getMinioClient();
        client.putObject(
                PutObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .stream(file.getInputStream(), file.getSize(), -1)
                        .contentType(file.getContentType())
                        .build()
        );

        // 返回给前端：
        // index 0: 作为 uuid 存储在前端 (实际是文件名: xxx.jpg)
        // index 1: 用于展示的 url
        return new String[]{fileName, url + "/" + bucketName + "/" + fileName};
    }

    /**
     * 下载文件
     * @param fileName MinIO中的文件名
     * @param response 响应对象
     */
    public void downloadFile(String fileName, HttpServletResponse response) throws Exception {
        MinioClient client = getMinioClient();
        try (InputStream in = client.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .build()
        )) {
            // 设置响应头，触发浏览器下载
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            response.setContentType("application/octet-stream");
            // 写入响应流
            org.apache.commons.io.IOUtils.copy(in, response.getOutputStream());
        } catch (IOException e) {
            throw new RuntimeException("下载文件失败", e);
        }
    }

    /**
     * 删除文件
     * @param fileName MinIO中的文件名
     */
    public void deleteFile(String fileName) throws Exception {
        MinioClient client = getMinioClient();
        client.removeObject(
                RemoveObjectArgs.builder()
                        .bucket(bucketName)
                        .object(fileName)
                        .build()
        );
    }

}
