package top.stellarium.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import top.stellarium.common.constant.JwtConstant;
import top.stellarium.common.exception.BusinessException;
import top.stellarium.common.properties.JwtProperties;
import top.stellarium.common.result.Result;
import top.stellarium.common.utils.JwtUtil;
import top.stellarium.mapper.UserMapper;
import top.stellarium.pojo.dto.AuthLoginDTO;
import top.stellarium.pojo.dto.AuthRegisterDTO;
import top.stellarium.pojo.entity.User;
import top.stellarium.pojo.vo.AuthLoginVO;
import top.stellarium.service.AuthService;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {
    @Autowired
    @Lazy
    private UserMapper userMapper;
    @Autowired
    private JwtProperties jwtProperties;

    /**
     * 用户注册
     *
     * @param authRegisterDTO
     * @return
     */
    @Override
    public boolean register(AuthRegisterDTO authRegisterDTO) {
        // TODO 验证captchaToken
        PasswordEncoder passwordEncoder = JwtUtil.passwordEncoder();
        String password = passwordEncoder.encode(authRegisterDTO.getPassword());
        authRegisterDTO.setPassword(password);
        User user = new User();
        BeanUtils.copyProperties(authRegisterDTO, user);
        user.setCreateTime(LocalDateTime.now());
        userMapper.insert(user);
        return true;
    }

    /**
     * 用户登录
     * @param authLoginDTO
     * @return
     */
    @Override
    public AuthLoginVO login(AuthLoginDTO authLoginDTO) {
        // 1. 使用 LambdaQueryWrapper 查询 (类型安全，防拼写错误)
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, authLoginDTO.getUsername());

        // selectOne: 查询单条数据。如果查到多条会报错(说明数据库脏数据)，没查到返回 null
        User user = userMapper.selectOne(queryWrapper);

        // 2. 校验用户是否存在
        if (user == null) {
            throw new BusinessException(400,"账号不存在"); // 建议自定义异常，如 AccountNotFoundException
        }
        PasswordEncoder passwordEncoder = JwtUtil.passwordEncoder();
        if(!passwordEncoder.matches(authLoginDTO.getPassword(), user.getPassword())){
            throw new BusinessException(400,"密码错误");
        }
        Map<String, Object> claims = new HashMap<>();
        claims.put(JwtConstant.USER_NAME, user.getUsername());
        String token = JwtUtil.createJWT(
                jwtProperties.getSecurityKey(),
                jwtProperties.getTtl(),
                claims);
        return AuthLoginVO.builder()
                .token(token)
                .avatar(user.getImage())
                .userId(user.getUserId())
                .nickname(user.getNickname())
                .build();
    }

    /**
     * 获取用户信息
     * @param id
     * @return
     */
    @Override
    public User getUserInfo(Long id) {
        return userMapper.selectById(id);
    }


}
