package cheer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 操作日志表
 */
@Data
@TableName("sys_operate_log")
public class OperateLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 日志ID（雪花算法）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 模块标题，如"用户管理"
     */
    private String title;

    /**
     * 操作类型：0=其他 1=新增 2=修改 3=删除
     */
    private Integer businessType;

    /**
     * 调用的Java方法全路径
     */
    private String method;

    /**
     * HTTP方法：GET/POST/PUT/DELETE
     */
    private String requestMethod;

    /**
     * 请求URL
     */
    private String operUrl;

    /**
     * 操作人用户名（从SecurityContext取）
     */
    private String operName;

    /**
     * 请求参数（JSON）
     */
    private String operParam;

    /**
     * 返回结果（JSON）
     */
    private String operResult;

    /**
     * 操作状态：0=正常 1=异常
     */
    private Integer status;

    /**
     * 异常信息（正常时为空）
     */
    private String errorMsg;

    /**
     * 操作者IP
     */
    private String operIp;

    /**
     * 操作时间
     */
    private LocalDateTime operTime;

    /**
     * 耗时（毫秒）
     */
    private Long costTime;
}