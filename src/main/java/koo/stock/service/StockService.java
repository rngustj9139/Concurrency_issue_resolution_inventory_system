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
    public synchronized void decrease(Long id, Long quantity) { // synchronized를 적용해 한개의 데이터에 한개의 스레드만 접근 가능하게 하여 race condition 해소
        Stock stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }

}
