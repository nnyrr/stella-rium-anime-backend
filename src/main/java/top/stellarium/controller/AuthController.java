package top.stellarium.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import top.stellarium.common.result.Result;
import top.stellarium.pojo.dto.AuthLoginDTO;
import top.stellarium.pojo.dto.AuthRegisterDTO;
import top.stellarium.pojo.entity.User;
import top.stellarium.pojo.vo.AuthLoginVO;
import top.stellarium.service.AuthService;

/**
 * 授权接口
 */
@RestController
@RequestMapping("/auth")
@Tag(name = "授权接口")
@Slf4j
public class AuthController {

    @Autowired
    private AuthService authService;

    /**
     * 用户注册
     * @param authRegisterDTO
     * @return
     */
    @PostMapping("/register")
    @Operation(summary = "注册")
    public Result register(@RequestBody AuthRegisterDTO authRegisterDTO){
        log.info("用户注册: {}", authRegisterDTO.getUsername());
        if(!authService.register(authRegisterDTO))return Result.error("验证码错误");
        return Result.success();
    }

    /**
     * 用户登录
     * @param authLoginDTO
     * @return
     */
    @PostMapping("/login")
    @Operation(summary = "登录")
    public Result<AuthLoginVO> login(@RequestBody AuthLoginDTO authLoginDTO){
        log.info("用户登录: {}", authLoginDTO.getUsername());
        AuthLoginVO authLoginVO = authService.login(authLoginDTO);
        return Result.success(authLoginVO);
    }

    /**
     * 获取用户信息
     * @param id
     * @return
     */
    @GetMapping
    @Operation(summary = "获取用户信息")
    public Result<User> getUserInfo(Long id){
        log.info("获取用户信息: {}", id);
        User user = authService.getUserInfo(id);
        return Result.success(user);
    }

}