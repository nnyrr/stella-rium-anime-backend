package top.stellarium.service;

import top.stellarium.pojo.vo.IndexAnimeVO;
import top.stellarium.pojo.vo.ListVO;
import top.stellarium.pojo.vo.TodaysPickVO;

/**
 * 主页接口服务
 */
public interface IndexService {
    /**
     * 获取每日放送
     * @return
     */
    ListVO<IndexAnimeVO> getCalendar();

    /**
     * 获取今日推荐
     * @return
     */
    TodaysPickVO getTodaysPick();

    /**
     * 获得热门动漫
     * @return
     */
    ListVO<IndexAnimeVO> getPopular();
}
