package koo.stock.repository;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class RedisLockRepository {

    private final RedisTemplate<String, String> redisTemplate;

    public RedisLockRepository(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Boolean lock(Long key) { // 로직을 실행 전 공유자원에 lock 메서드를 통해 락 설정
        return redisTemplate
                .opsForValue()
                .setIfAbsent(generateKey(key), "lock", Duration.ofMillis(3_000)); // key는 Stock의 id, value는 문자열 lock
    }

    public Boolean unlock(Long key) { // 로직이 끝나면 unlock 메서드를 통해 락 해제
        return redisTemplate
                .delete(generateKey(key));
    }

    private String generateKey(Long key) {
        return key.toString();
    }

}
