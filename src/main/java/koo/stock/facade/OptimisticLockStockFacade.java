package koo.stock.facade;

import koo.stock.service.OptimisticLockStockService;
import org.springframework.stereotype.Component;

@Component
public class OptimisticLockStockFacade { // 버전이 달라져 업데이트에 실패했을 때 다시 데이터부터 가져와야하므로 해당 클래스 작성 필요

    private final OptimisticLockStockService optimisticLockStockService;

    public OptimisticLockStockFacade(OptimisticLockStockService optimisticLockStockService) {
        this.optimisticLockStockService = optimisticLockStockService;
    }

    public void decrease(Long id, Long quantity) throws InterruptedException {
        while (true) {
            try {
                optimisticLockStockService.decrease(id, quantity);
                break; // 정상적으로 업데이트가 된 경우 while문 탈출
            } catch (Exception e) {
                Thread.sleep(50); // 버전이 달라져 업데이트에 실패했을 때 50밀리 세컨드 이후 다시 데이터부터 가져오는 작업을 수행
            }
        }
    }

}
