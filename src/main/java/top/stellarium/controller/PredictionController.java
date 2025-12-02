package top.stellarium.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.stellarium.common.result.Result;
import top.stellarium.pojo.dto.CollectionDTO;
import top.stellarium.pojo.vo.CollectionAnimeVO;
import top.stellarium.pojo.vo.CollectionCharacterVO;
import top.stellarium.pojo.vo.ListVO;
import top.stellarium.service.PredictionService;

@RestController
@RequestMapping("/prediction")
@Slf4j
@Tag(name = "预测相关接口")
public class PredictionController {

    @Autowired
    private PredictionService predictionService;

    /**
     * 获取预测的动漫
     * @param userId
     * @return
     */
    @GetMapping("/anime")
    @Operation(summary = "获取预测的动漫")
    public Result<ListVO<CollectionAnimeVO>> getPredictionAnime(Integer userId) throws Exception {
        log.info("获取预测动漫: {}", userId);
        ListVO<CollectionAnimeVO> list = predictionService.getPredictionAnime(userId);
        return Result.success(list);
    }

    /**
     * 获取预测的角色
     * @param userId
     * @return
     */
    @GetMapping("/character")
    @Operation(summary = "获取预测的角色")
    public Result<ListVO<CollectionCharacterVO>> getPredictionCharacter(Integer userId){
        log.info("获取预测角色: {}", userId);
        ListVO<CollectionCharacterVO> listVO = predictionService.getPredictionCharacter(userId);
        return Result.success(listVO);
    }

    /**
     * 获取动漫的相似动漫
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取动漫的相似动漫")
    public Result<ListVO<CollectionAnimeVO>> getRelatedAnime(@PathVariable Integer id){
        log.info("获取相似动漫: {}", id);
        ListVO<CollectionAnimeVO> list = predictionService.getRelatedAnime(id);
        return Result.success(list);
    }
}
