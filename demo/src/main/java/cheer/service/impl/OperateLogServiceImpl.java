package cheer.service.impl;

import cheer.entity.OperateLog;
import cheer.mapper.OperateLogMapper;
import cheer.service.OperateLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class OperateLogServiceImpl implements OperateLogService {
    @Autowired
    OperateLogMapper operateLogMapper;

    @Async
    public void save(OperateLog operateLog) {
        operateLogMapper.insert(operateLog);
    }
}
