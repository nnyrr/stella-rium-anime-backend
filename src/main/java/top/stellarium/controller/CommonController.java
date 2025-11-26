package top.stellarium.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import top.stellarium.common.result.Result;
import top.stellarium.common.utils.MinioUtil;
import top.stellarium.pojo.vo.UploadVO;

/**
 * 通用接口控制器
 */
@RestController
@RequestMapping("/common")
@Slf4j
@Tag(name = "通用接口")
public class CommonController {

    @Autowired
    private MinioUtil minioUtil;

    /**
     * 上传文件
     * @param file
     * @return
     * @throws Exception
     */
    @PostMapping("/file")
    @Operation(summary = "上传文件")
    public Result<UploadVO> upload(MultipartFile file) throws Exception {
        log.info("上传文件: {}", file);
        String[] result = minioUtil.uploadFile(file);
        UploadVO uploadVO = UploadVO.builder().uuid(result[0]).image(result[1]).build();
        return Result.success(uploadVO);
    }

    /**
     * 删除文件
     * @param json
     * @return
     */
    @DeleteMapping("/file")
    @Operation(summary = "删除文件")
    public Result delete(@RequestBody String json) throws Exception {
        // json
        log.info("删除文件: {}", json);
        // json: {"uuid":"???"}
        JsonNode jsonNode = new ObjectMapper().readTree(json);
        minioUtil.deleteFile(jsonNode.get("uuid").asText());
        return Result.success();
    }
}
