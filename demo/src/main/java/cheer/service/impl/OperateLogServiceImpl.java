package cheer.service.impl;

import cheer.entity.OperateLog;
import cheer.mapper.OperateLogMapper;
import cheer.service.OperateLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 操作日志服务实现
 * <p>
 * save 方法加了 @Async，在独立线程中执行，不阻塞主业务。
 * 需要在启动类上加 @EnableAsync 才生效。
 */
@Service
public class OperateLogServiceImpl implements OperateLogService {
    @Autowired
    OperateLogMapper operateLogMapper;

    /**
     * 异步写入操作日志
     */
    @Async
    @Override
    public void save(OperateLog operateLog) {
        operateLogMapper.insert(operateLog);
    }
}
