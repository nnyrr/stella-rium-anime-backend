package top.stellarium.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.stellarium.common.result.Result;
import top.stellarium.pojo.dto.CollectionDTO;
import top.stellarium.pojo.vo.CollectionAnimeVO;
import top.stellarium.pojo.vo.CollectionCharacterVO;
import top.stellarium.pojo.vo.ListVO;
import top.stellarium.service.CollectionService;

/**
 * 收藏相关接口
 */
@RestController
@RequestMapping("/collection")
@Slf4j
@Tag(name = "收藏相关接口")
public class CollectionController {

    @Autowired
    private CollectionService collectionService;

    /**
     * 获取收藏的动漫
     * @param collectionDTO
     * @return
     */
    @GetMapping("/anime")
    @Operation(summary = "获取收藏的动漫")
    public Result<ListVO<CollectionAnimeVO>> getCollectedAnime(CollectionDTO collectionDTO){
        log.info("获取收藏的动漫: {}", collectionDTO);
        ListVO<CollectionAnimeVO> list = collectionService.getCollectedAnime(collectionDTO);
        return Result.success(list);
    }

    /**
     * 获取收藏的角色
     * @param collectionDTO
     * @return
     */
    @GetMapping("/character")
    @Operation(summary = "获取收藏的角色")
    public Result<ListVO<CollectionCharacterVO>>getCollectedCharacter(CollectionDTO collectionDTO){
        log.info("获取收藏的角色: {}", collectionDTO);
        ListVO<CollectionCharacterVO> list = collectionService.getCollectedCharacter(collectionDTO);
        return Result.success(list);
    }
}
