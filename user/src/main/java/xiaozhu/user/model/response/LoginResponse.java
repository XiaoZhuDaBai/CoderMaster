package xiaozhu.user.model.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 * 包含用户信息和JWT token
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    /**
     * 用户信息（JSON字符串）
     */
    private String userInfo;
    
    /**
     * JWT Token
     */
    private String token;
}

