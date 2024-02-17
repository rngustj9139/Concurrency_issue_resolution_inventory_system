package koo.stock.facade;

import koo.stock.repository.StockRepository;
import koo.stock.service.NamedLockStockService;
import koo.stock.service.StockService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NamedLockStockFacade {

    private final StockRepository stockRepository;
    private final NamedLockStockService namedLockStockService;

    public NamedLockStockFacade(StockRepository stockRepository, NamedLockStockService namedLockStockService) {
        this.stockRepository = stockRepository;
        this.namedLockStockService = namedLockStockService;
    }

    @Transactional
    public void decrease(Long id, Long quantity) {
        try {
            stockRepository.getLock(id.toString());
            namedLockStockService.decrease(id, quantity);
        } finally {
            stockRepository.releaseLock(id.toString());
        }
    }

}
