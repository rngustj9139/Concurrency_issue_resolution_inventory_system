package koo.stock.service;

import koo.stock.domain.Stock;
import koo.stock.repository.StockRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StockServiceTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private StockRepository stockRepository;

    @BeforeEach
    public void before() {
        stockRepository.saveAndFlush(new Stock(1L, 100L));
    }

    @AfterEach
    public void after() {
        stockRepository.deleteAll();
    }

    @Test
    public void 재고감소() {
        stockService.decrease(1L, 1L);

        Stock stock = stockRepository.findById(1L).orElseThrow();
        Assertions.assertThat(stock.getQuantity()).isEqualTo(99);
    }

    @Test
    public void 동시에_100개의_요청() throws InterruptedException { // 멀티 스레드 이용
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 비동기 처리를 위한 java의 API 이용
        CountDownLatch latch = new CountDownLatch(threadCount);// 100개의 요청이 끝날 때 까지 기다려야하므로 CountDownLatch 이용 (다른 스레드에서 수행중인 작업이 완료될 때 까지 대기)

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    stockService.decrease(1L, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();
        Assertions.assertThat(stock.getQuantity()).isEqualTo(0); // 테스트 fail 발생 (둘 이상의 스레드가 공유자원에 엑세스하는 Race Condition 때문이다. ex - 스레드1이 select를 하고 update 하기 전 스레드2가 select를 하는 상황 발생)
    }

}