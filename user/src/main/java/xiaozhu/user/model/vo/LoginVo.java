package xiaozhu.user.model.vo;

import lombok.Data;

@Data
public class LoginVo {
    /**
     * 邮箱
     */
    private String email;

    /**
     * 输入的验证码
     */
    private String inputCode;
}

