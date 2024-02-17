package koo.stock.service;

import koo.stock.domain.Stock;
import koo.stock.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {

    private final StockRepository stockRepository;

    public StockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    // Stock 조회
    // 재고 감소
    // 갱신된 값 저장
    // @Transactional
    public synchronized void decrease(Long id, Long quantity) { // synchronized를 적용해 한개의 공유자원에 한개의 주체만 접근 가능하게 하여 race condition 해소 (but synchronized는 프로세스(서버) 단위로 동작하기 때문에 스레드들의 race condition이 해소되지 않는다. -> @Transactional 어노테이션을 지우면 race condition이 해소 된다.)
        Stock stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }

}
