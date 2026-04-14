package xiaozhu.user.model;

import java.io.Serializable;
import java.util.Date;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
* 用户表
*/
@TableName("user")
@Setter
@Getter
public class User implements Serializable {

    /**
    * 用户ID（自增主键）
     */
    @TableId(value = "user_id", type = IdType.AUTO)
    private Long userId;
    /**
    * 用户UUID（业务ID）
     */
    private String uuid;
    /**
    * 第三方OpenID
     */
    private String openid;
    /**
    * 第三方UnionID（跨应用唯一）
     */
    private String unionid;
    /**
    * 登录提供方：WECHAT/QQ/EMAIL
     */
    @TableField("login_provider")
    private String loginProvider;
    /**
    * 邮箱（可选）
     */

    private String email;
    /**
    * 手机号（可选）
     */
    private String phone;
    /**
    * 昵称
     */
    private String nickname;
    /**
    * 头像URL
     */
    private String avatar;
    /**
    * 性别：0-未知，1-男，2-女
     */
    private Integer gender;
    /**
    * 是否管理员：0-否，1-是
     */
    @TableField("is_admin")
    private Integer isAdmin;
    /**
     *  用户类型：NORMAL/INTERNAL/ROBOT/BANNED
     */
    @TableField("user_type")
    private String userType;
    /**
     * 禁用原因/备注
     */
    @TableField("ban_reason")
    private String banReason;
    /**
    * 注销/禁用时间
     */
    @TableField("deactivated_at")
    private Date deactivatedAt;
    /**
    * 最近登录时间
     */
    @TableField("last_login_time")
    private Date lastLoginTime;
    /**
    * 最近登录 IP
     */
    @TableField("last_login_ip")
    private String lastLoginIp;
    /**
    * 创建时间
     */
    @TableField("create_time")
    private Date createTime;
    /**
    * 更新时间
     */
    @TableField("update_time")
    private Date updateTime;


}
