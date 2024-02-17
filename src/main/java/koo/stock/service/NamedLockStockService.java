package koo.stock.service;

import koo.stock.domain.Stock;
import koo.stock.repository.StockRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NamedLockStockService {

    private final StockRepository stockRepository;

    public NamedLockStockService(StockRepository stockRepository) {
        this.stockRepository = stockRepository;
    }

    // Stock 조회
    // 재고 감소
    // 갱신된 값 저장
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /** [부모의 트랜잭션과 독립적으로 실행되어야 하기 때문에 propagation 설정(전파 속성 설정) 수행]
    해당 클래스가 부모의 트랜잭션과 동일한 범위로 묶인다면 Database에 commit 되기전에 락이 풀리는 현상이 발생한다.
    그렇기때문에 별도의 트랜잭션으로 분리를 해주어 Database에 정상적으로 commit이 된 이후에 락을 해제하는것을 의도함
    핵심은 lock 을 해제하기전에 Database에 commit이 되도록 하는 것이다.
    */
    public void decrease(Long id, Long quantity) {
        Stock stock = stockRepository.findById(id).orElseThrow();
        stock.decrease(quantity);
        stockRepository.saveAndFlush(stock);
    }

}
