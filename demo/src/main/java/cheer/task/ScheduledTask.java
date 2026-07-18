package cheer.task;

import cheer.entity.NotifyLog;
import cheer.mapper.NotifyLogMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Set;

@Component
@Slf4j
public class ScheduledTask {
    @Autowired
    NotifyLogMapper notifyLogMapper;
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredNotifyLog() {
        LambdaQueryWrapper<NotifyLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.lt(NotifyLog::getCreateTime, LocalDateTime.now().minusDays(30));
        int count = notifyLogMapper.delete(queryWrapper);
        log.info("删除了{}条", count);
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void refreshMenuCache() {
        Set<String> menuKeys = stringRedisTemplate.keys("menu:tree:*");
        if (menuKeys != null && !menuKeys.isEmpty()) {
            stringRedisTemplate.delete(menuKeys);
        }
        Set<String> userMenuKeys = stringRedisTemplate.keys("user:menuTree:*");
        if (userMenuKeys != null && !userMenuKeys.isEmpty()) {
            stringRedisTemplate.delete(userMenuKeys);
        }
        Set<String> userPermsKeys = stringRedisTemplate.keys("user:perms:*");
        if (userPermsKeys != null && !userPermsKeys.isEmpty()) {
            stringRedisTemplate.delete(userPermsKeys);
        }
        log.info("菜单缓存已清空");
    }

}
