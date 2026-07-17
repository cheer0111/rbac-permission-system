package cheer.service;

import cheer.entity.OperateLog;

/**
 * 操作日志服务接口
 */
public interface OperateLogService {

    /**
     * 异步保存操作日志（由 @Async 在 ServiceImpl 中实现）
     *
     * @param operateLog 操作日志实体
     */
    void save(OperateLog operateLog);
}
