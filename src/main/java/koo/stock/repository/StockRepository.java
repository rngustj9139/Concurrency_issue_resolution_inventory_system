package koo.stock.repository;

import jakarta.persistence.LockModeType;
import koo.stock.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockRepository extends JpaRepository<Stock, Long> {

    /**
     * Pessimistic Lock
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE) // 동시성 이슈 해결을 위해 PessimisticLock 사용
    @Query("select s from Stock s where s.id = :id") // Native Query 이용
    Stock findByIdWithPessimisticLock(@Param("id") Long id); // 동시성 이슈 해결을 위해 PessimisticLock 사용

    /**
     * Optimistic Lock
     */
    @Lock(LockModeType.OPTIMISTIC)
    @Query("select s from Stock s where s.id = :id")
    Stock findByIdWithOptimisticLock(@Param("id") Long id);

    /**
     * Named Lock
     */
    @Query(value = "select get_lock(:key, 3000)", nativeQuery = true)
    void getLock(@Param("key") String key);

    @Query(value = "select release_lock(:key)", nativeQuery = true)
    void releaseLock(@Param("key") String key);

}
