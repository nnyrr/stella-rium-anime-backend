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
import top.stellarium.pojo.vo.PlayerInfoVO;
import top.stellarium.service.PlayerService;

@RestController
@RequestMapping("/player")
@Slf4j
@Tag(name = "播放器相关接口")
public class PlayerController {

    @Autowired
    private PlayerService playerService;

    /**
     * 获取播放信息
     * @param id
     * @return
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取播放信息")
    public Result<PlayerInfoVO> getPlayerInfo(@PathVariable Integer id){
        log.info("获取播放信息: {}", id);
        PlayerInfoVO playerInfoVO = playerService.getPlayerInfo(id);
        return Result.success(playerInfoVO);
    }
}
