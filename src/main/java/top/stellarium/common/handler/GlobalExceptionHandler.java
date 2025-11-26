package top.stellarium.common.handler;

import lombok.extern.slf4j.Slf4j;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authz.AuthorizationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import top.stellarium.common.exception.BusinessException;
import top.stellarium.common.result.Result;

@Slf4j
@RestControllerAdvice // 核心注解：拦截所有 Controller 的异常
public class GlobalExceptionHandler {

    /**
     * 1. 拦截我们自己定义的业务异常
     * 场景：我们在代码里 throw new BusinessException("账号已存在");
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 2. 拦截 Shiro 权限不足异常
     * 场景：用户没有 @RequiresPermissions("user:add") 权限
     */
    @ExceptionHandler(AuthorizationException.class)
    public Result<?> handleAuthorizationException(AuthorizationException e) {
        log.warn("权限不足: {}", e.getMessage());
        return Result.error(403, "您没有操作权限");
    }

    /**
     * 3. 拦截 Shiro 认证失败异常
     * 场景：Token 错误、Token 过期、账号密码错误
     */
    @ExceptionHandler(AuthenticationException.class)
    public Result<?> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证失败: {}", e.getMessage());
        return Result.error(401, "认证失败，请重新登录");
    }

    /**
     * 4. 拦截其他所有未知的系统异常 (兜底方案)
     * 场景：空指针(NullPointerException)、SQL 语法错误等
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 打印堆栈信息，方便后台排查 Bug
        log.error("系统未知错误", e);
        // 给前端返回友好的提示，不要直接把 "NullPointerException" 给用户看
        return Result.error(500, "系统繁忙，请稍后再试");
    }
}
