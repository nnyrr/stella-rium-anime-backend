package top.stellarium.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.stellarium.common.result.Result;
import top.stellarium.pojo.dto.LibraryDTO;
import top.stellarium.pojo.vo.LibraryAnimeVO;
import top.stellarium.pojo.vo.ListVO;
import top.stellarium.service.LibraryService;

@RestController
@RequestMapping("/library")
@Tag(name = "动漫库相关接口")
@Slf4j
public class LibraryController {

    @Autowired
    private LibraryService libraryService;


    // TODO 利用分桶ZSET优化查询性能，几种筛选映射状态。
    /**
     * 查询排行榜
     * @param libraryDTO
     * @return
     */
    @GetMapping
    @Operation(summary = "查询排行榜")
    public Result<ListVO<LibraryAnimeVO>> getLibrary(
             LibraryDTO libraryDTO
    ){
        log.info("查询排行榜: {}", libraryDTO);
        ListVO<LibraryAnimeVO> list = libraryService.getLibrary(libraryDTO);
        return Result.success(list);
    }
}
