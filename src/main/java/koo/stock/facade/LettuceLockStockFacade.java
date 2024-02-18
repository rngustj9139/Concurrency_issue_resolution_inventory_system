package koo.stock.facade;

import koo.stock.repository.RedisLockRepository;
import koo.stock.service.StockService;
import org.springframework.stereotype.Component;

@Component
public class LettuceLockStockFacade {

    private final RedisLockRepository redisLockRepository;
    private final StockService stockService;

    public LettuceLockStockFacade(RedisLockRepository redisLockRepository, StockService stockService) {
        this.redisLockRepository = redisLockRepository;
        this.stockService = stockService;
    }

    public void decrease(Long id, Long quantity) throws InterruptedException {
        while (!redisLockRepository.lock(id)) {
            Thread.sleep(100); // lock이 걸려있을 경우는 100 milis의 텀을 발생시켜 redis의 부하를 줄인다. (Lettuce는 Spin Lock 방식이므로 부하를 줄여야한다.)
        }

        // lock이 걸려 있지 않은 경우
        try {
            stockService.decrease(id, quantity);
        } finally {
            redisLockRepository.unlock(id);
        }
    }

}
