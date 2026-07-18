package cheer.service;

import cheer.common.enums.ResultCode;
import cheer.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件存储服务（本地存储）
 */
@Service
public class FileService {

    @Value("${file.upload.path}")
    private String uploadPath;

    /**
     * 保存文件到本地磁盘，返回可访问的 URL 路径
     *
     * @param file 上传的文件
     * @return 可访问路径，如 /uploads/2026/07/18/abc.jpg
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "文件不能为空");
        }

        String date = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));

        String originalFilename = file.getOriginalFilename();
        String suffix = originalFilename.substring(originalFilename.lastIndexOf("."));
        String filename = UUID.randomUUID() + suffix;

        Path path = Paths.get(uploadPath, date, filename);

        try {
            Files.createDirectories(path.getParent());
            file.transferTo(path);
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "文件上传失败");
        }

        return "/uploads/" + date + "/" + filename;
    }
}
