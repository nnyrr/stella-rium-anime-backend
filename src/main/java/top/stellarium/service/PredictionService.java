package top.stellarium.service;

import top.stellarium.pojo.vo.CollectionAnimeVO;
import top.stellarium.pojo.vo.ListVO;

public interface PredictionService {
    /**
     * 获取预测的动漫
     * @param userId
     * @return
     */
    ListVO<CollectionAnimeVO> getPredictionAnime(Integer userId) throws Exception;
}
