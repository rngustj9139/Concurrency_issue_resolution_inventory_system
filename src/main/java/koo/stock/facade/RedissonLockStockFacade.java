package koo.stock.facade;

import koo.stock.service.StockService;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedissonLockStockFacade {

    private final RedissonClient redissonClient;
    private final StockService stockService;

    public RedissonLockStockFacade(RedissonClient redissonClient, StockService stockService) {
        this.redissonClient = redissonClient;
        this.stockService = stockService;
    }

    public void decrease(Long id, Long quantity) {
        RLock lock = redissonClient.getLock(id.toString());

        try {
            boolean available = lock.tryLock(30, 1, TimeUnit.SECONDS); // 대기 시간: 30, 유지 시간: 1

            if (!available) { // lock 획득에 실패한 경우
                System.out.println("Lock 획득 실패");

                return;
            }

            // lock 획득에 성공한 경우
            stockService.decrease(id, quantity);
        } catch (RuntimeException | InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            lock.unlock(); // 로직 수행이 끝난 후 lock 해제
        }
    }

}
