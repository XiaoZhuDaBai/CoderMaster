package xiaozhu.user.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import xiaozhu.common.comm.ResponseResult;
import xiaozhu.user.model.response.LoginResponse;
import xiaozhu.user.model.vo.LoginVo;
import xiaozhu.user.service.UserService;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 发送验证码
     */
    @PostMapping("/sendCode")
    public ResponseResult<String> sendCode(@RequestParam String email) {
        String result = userService.sendVerificationCode(email);
        if ("验证码发送成功".equals(result)) {
            return ResponseResult.success(result);
        } else {
            return ResponseResult.fail(result);
        }
    }

    /**
     * 登录或注册
     * 返回用户信息和JWT token
     */
    @PostMapping("/loginOrRegister")
    public ResponseResult<LoginResponse> loginOrRegister(@RequestBody LoginVo loginVo) {
        return userService.toLoginOrRegister(loginVo);
    }
}

