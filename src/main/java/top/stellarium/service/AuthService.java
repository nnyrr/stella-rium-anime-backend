package top.stellarium.service;

import top.stellarium.pojo.dto.AuthLoginDTO;
import top.stellarium.pojo.dto.AuthRegisterDTO;
import top.stellarium.pojo.entity.User;
import top.stellarium.pojo.vo.AuthLoginVO;

/**
 * 授权接口服务
 */
public interface AuthService {
    /**
     * 用户注册
     * @param authRegisterDTO
     * @return
     */
    boolean register(AuthRegisterDTO authRegisterDTO);

    /**
     * 用户登录
     * @param authLoginDTO
     * @return
     */
    AuthLoginVO login(AuthLoginDTO authLoginDTO);

    /**
     * 获取用户信息
     * @param id
     * @return
     */
    User getUserInfo(Long id);
}
