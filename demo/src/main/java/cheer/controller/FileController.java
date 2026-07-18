package cheer.controller;

import cheer.common.enums.ResultCode;
import cheer.common.result.Result;
import cheer.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping("/upload")
    public Result<String> uploads(@RequestParam MultipartFile file) {
        String url = fileService.store(file);
        return Result.success(url, ResultCode.SUCCESS, "上传成功");
    }
}
