package top.stellarium.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import top.stellarium.common.result.Result;
import top.stellarium.pojo.vo.IndexAnimeVO;
import top.stellarium.pojo.vo.ListVO;
import top.stellarium.pojo.vo.TodaysPickVO;
import top.stellarium.service.IndexService;

@RestController
@RequestMapping("/index")
@Slf4j
@Tag(name = "主页相关接口")
public class IndexController {

    @Autowired
    private IndexService indexService;

    /**
     * 获取每日放送
     * @return
     */
    @GetMapping("/calendar")
    @Operation(summary = "获取每日放送")
    public Result<ListVO<IndexAnimeVO>> getCalendar(){
        log.info("获取每日放送");
        ListVO<IndexAnimeVO> listVO = indexService.getCalendar();
        return Result.success(listVO);
    }

    /**
     * 获得今日推荐
     * @return
     */
    @GetMapping("/today")
    @Operation(summary = "获得今日推荐")
    public Result<TodaysPickVO> getTodaysPick(){
        log.info("获取今日推荐");
        TodaysPickVO todaysPickVO = indexService.getTodaysPick();
        return Result.success(todaysPickVO);
    }

    /**
     * 获得热门动漫
     * @return
     */
    @GetMapping("/popular")
    @Operation(summary = "获得热门动漫")
    public Result<ListVO<IndexAnimeVO>> getPopular(){
        log.info("获得热门动漫");
        ListVO<IndexAnimeVO> list = indexService.getPopular();
        return Result.success(list);
    }
}
