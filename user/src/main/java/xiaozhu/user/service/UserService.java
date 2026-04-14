package xiaozhu.user.service;

import xiaozhu.common.comm.ResponseResult;
import xiaozhu.user.model.response.LoginResponse;
import xiaozhu.user.model.vo.LoginVo;

public interface UserService {
    /**
     * 检查邮箱是否已存在
     */
    boolean isExistInTable(String email);

    /**
     * 登录或注册
     */
    ResponseResult<LoginResponse> toLoginOrRegister(LoginVo loginVo);

    /**
     * 发送验证码
     */
    String sendVerificationCode(String email);
}

