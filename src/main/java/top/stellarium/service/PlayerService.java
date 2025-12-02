package top.stellarium.service;

import top.stellarium.pojo.vo.PlayerInfoVO;

public interface PlayerService {
    /**
     * 获取播放资源信息
     * @param id
     * @return
     */
    PlayerInfoVO getPlayerInfo(Integer id);
}
