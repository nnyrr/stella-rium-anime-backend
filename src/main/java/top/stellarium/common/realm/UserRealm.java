package top.stellarium.common.realm;

import io.jsonwebtoken.Claims;
import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.stellarium.common.constant.JwtConstant;
import top.stellarium.common.properties.JwtProperties;
import top.stellarium.common.token.JwtToken;
import top.stellarium.common.utils.JwtUtil;
import top.stellarium.service.AuthService;


@Component
public class UserRealm extends AuthorizingRealm {

    @Autowired
    private JwtProperties jwtProperties;

    @Override
    public boolean supports(AuthenticationToken token) {
        return token instanceof JwtToken;
    }

    // 授权：获取用户权限 (可以从 Token 解析，也可以查数据库)
    @Override
    protected AuthorizationInfo doGetAuthorizationInfo(PrincipalCollection principals) {
        String token = (String) principals.getPrimaryPrincipal();
        // 伪代码：String username = JwtUtil.getUsername(token);
        SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
        return info;
    }

    // 认证：验证 Token 有效性
    @Override
    protected AuthenticationInfo doGetAuthenticationInfo(AuthenticationToken auth) throws AuthenticationException {
        String token = (String) auth.getCredentials();

        if (token == null) {
            throw new AuthenticationException("Token is null");
        }

        // 1. 校验 Token 是否有效 (签名、过期)
        String username = null;
        try {
            Claims claims = JwtUtil.parseJWT(jwtProperties.getSecurityKey(), token);
            username = claims.get(JwtConstant.USER_NAME).toString();
        }
        catch (Exception ex){
            throw new AuthenticationException("Token invalid or expired");
        }
        if(username == null){
            throw new AuthenticationException("Token invalid or expired");
        }

        // 返回认证信息，Shiro 会自动匹配 Token

        return new SimpleAuthenticationInfo(token, token, getName());
    }
}
