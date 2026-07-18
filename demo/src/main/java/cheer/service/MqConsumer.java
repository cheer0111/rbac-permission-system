package cheer.service;

import cheer.common.config.RabbitMQConfig;
import cheer.entity.NotifyLog;
import cheer.mapper.NotifyLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
public class MqConsumer {
    @Autowired
    NotifyLogMapper notifyLogMapper;

    @RabbitListener(queues = RabbitMQConfig.WELCOME_QUEUE)
    public void handleWelcome(Map<String, Object> message) {
        try {
            Long userId = ((Number) message.get("userId")).longValue();
            String username = (String) message.get("username");

            NotifyLog notifyLog = new NotifyLog();
            notifyLog.setUserId(userId);
            notifyLog.setUsername(username);
            notifyLog.setNotifyType("welcome");
            notifyLog.setTitle("欢迎注册");
            notifyLog.setContent("欢迎 " + username + " 加入系统");
            notifyLog.setStatus(0);
            notifyLog.setCreateTime(LocalDateTime.now());

            notifyLogMapper.insert(notifyLog);
            log.info("欢迎通知发送成功：userId={}, username={}", userId, username);
        } catch (Exception e) {
            log.error("欢迎通知发送失败", e);
            try {
                NotifyLog notifyLog = new NotifyLog();
                notifyLog.setNotifyType("welcome");
                notifyLog.setTitle("欢迎注册");
                notifyLog.setStatus(1);
                notifyLog.setErrorMsg(e.getMessage());
                notifyLog.setCreateTime(LocalDateTime.now());
                notifyLogMapper.insert(notifyLog);
            } catch (Exception ex) {
                log.error("通知日志写入也失败", ex);
            }
        }
    }
}
