package koo.stock.service;

import koo.stock.domain.Stock;
import koo.stock.facade.LettuceLockStockFacade;
import koo.stock.facade.NamedLockStockFacade;
import koo.stock.facade.OptimisticLockStockFacade;
import koo.stock.facade.RedissonLockStockFacade;
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
    private PessimisticLockStockService pessimisticLockStockService;

    @Autowired
    private OptimisticLockStockFacade optimisticLockStockFacade;

    @Autowired
    private NamedLockStockFacade namedLockStockFacade;

    @Autowired
    private LettuceLockStockFacade lettuceLockStockFacade;

    @Autowired
    private RedissonLockStockFacade redissonLockStockFacade;

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
    public void 동시에_100개의_요청_V1() throws InterruptedException { // 멀티 스레드 이용
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 비동기 처리를 위해 java의 API 이용
        // CountDownLatch 는 테스트코드에서 모든 실행이 완료될때까지 사용할 용도로 사용한 것으로 레이스 컨디션과는 무관
        // 100개의 요청이 끝날 때 까지 기다려야하므로 CountDownLatch 이용 (다른 스레드에서 수행중인 테스트가 완료될 때 까지 대기)
        CountDownLatch latch = new CountDownLatch(threadCount);

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
        /**
        테스트 fail 발생 (둘 이상의 스레드가 공유자원에 엑세스하는 Race Condition 때문이다. ex - 스레드1이 select를 하고 update 하기 전 스레드2가 select를 하는 상황 발생)

         해결 방법 1: synchronized 적용 (but 이 방법도 테스트 fail 발생 => @Transactional은 StockService 클래스를 새로 만들어 실행 하기 때문이다 따라서
                   여러개의 스레드가 동시에 동작하게 된다. @Transactional 어노테이션을 제거하면 테스트는 통과된다. synchronized는 각각 하나의 프로세스(서버)에만 보장되지만 스레드에는 적용되지 않는다.
                   즉 이는 여러 스레드가 동시에 하나의 데이터에 접근이 가능하다는 것을 의미한다.)

         해결 방법 2: Database의 Lock 활용
                    1.Pessimistic Lock(Exclusive Lock): 공유자원에 Lock을 걸어 다른 스레드가 공유자원에 접근하지 못하게 함 but 데드락이 발생 가능하다.
                    2.Optimistic Lock: 실제로 Lock 이용하지 않고 버전을 이용해 정합성을 맞추는 방법 (먼저 데이터를 select해서 불러들인다음에 update를 수행할 때 현재 읽은 버전이 맞는지 확인하여 업데이트한다. 만약 읽은 버전에서 수정사항이 생겼을 경우 다시 select 작업을 수행 한뒤 update한다.)
                    3.Named Lock: 이름을 가진 Metadata Locking 이다. 이름을 가진 lock을 획득한 후 해제할때까지 다른 세션이 lock을 획득할 수 없도록 한다. 주의할점으로는 Transaction이 종료될 때 lock이 자동으로 해제되지 않기 때문에 별도의 명령어로 해제를 수행해야한다. Pessimistic Lock은 로우나 테이블 단위로 Lock을 걸지만 해당 기법은 메타데이터에 Lock을 건다

         해결 방법 3: Redis 이용
            docker pull redis
            docker run --name myredis -d -p 6379:6379 redis
            이후 의존성 추가 필요(implementation 'org.springframework.boot:spring-boot-starter-data-redis')

            docker exec -it (docker ps를 통한 redis의 컨테이너 id) redis-cli
            setnx 1 lock # key == 1, value == lock (키가 같은 데이터가 중복해서 존재할 수 없음)
            del 1

            Redisson 의존성 추가 (implementation 'org.redisson:redisson-spring-boot-starter:3.25.2')
            pub-sub 시스템이므로 터미널 2개 필요, 각각의 터미널에 아래 명령어 입력
            docker exec -it (docker ps를 통한 redis의 컨테이너 id) redis-cli
            1번 터미널에서는 subscribe ch1 입력(channel 1을 구독)
            2번 터미널에서는 publish ch1 hello 입력(hello라는 메세지 송신)

            1.Lettuce: Spin Lock 방식(특정 스레드가 공유자원에 작업을 위해 락을 건 상태이면 다른 스레드는 일정시간이 지나면 다시 락이 풀려있는지 확인한다.), setnx 명령어 사용
            2.Redisson: Pub-Sub 방식 (스레드1의 작업이 끝날 경우 작업이 끝나서 락을 해제했다는 것을 공유자원에 접근하려는 다른 스레드에게 메세지를 통해 알려준다.)
         **/
         Assertions.assertThat(stock.getQuantity()).isEqualTo(0);
    }

    @Test
    public void 동시에_100개의_요청_V2() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 비동기 처리를 위해 java의 API 이용
        // CountDownLatch 는 테스트코드에서 모든 실행이 완료될때까지 사용할 용도로 사용한 것으로 레이스 컨디션과는 무관
        // 100개의 요청이 끝날 때 까지 기다려야하므로 CountDownLatch 이용 (다른 스레드에서 수행중인 테스트가 완료될 때 까지 대기)
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    pessimisticLockStockService.decrease(1L, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();

        Assertions.assertThat(stock.getQuantity()).isEqualTo(0);
    }

    @Test
    public void 동시에_100개의_요청_V3() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 비동기 처리를 위해 java의 API 이용
        // CountDownLatch 는 테스트코드에서 모든 실행이 완료될때까지 사용할 용도로 사용한 것으로 레이스 컨디션과는 무관
        // 100개의 요청이 끝날 때 까지 기다려야하므로 CountDownLatch 이용 (다른 스레드에서 수행중인 테스트가 완료될 때 까지 대기)
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    optimisticLockStockFacade.decrease(1L, 1L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();

        Assertions.assertThat(stock.getQuantity()).isEqualTo(0);
    }

    @Test
    public void 동시에_100개의_요청_V4() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 비동기 처리를 위해 java의 API 이용
        // CountDownLatch 는 테스트코드에서 모든 실행이 완료될때까지 사용할 용도로 사용한 것으로 레이스 컨디션과는 무관
        // 100개의 요청이 끝날 때 까지 기다려야하므로 CountDownLatch 이용 (다른 스레드에서 수행중인 테스트가 완료될 때 까지 대기)
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    namedLockStockFacade.decrease(1L, 1L);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();

        Assertions.assertThat(stock.getQuantity()).isEqualTo(0);
    }

    @Test
    public void 동시에_100개의_요청_V5() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 비동기 처리를 위해 java의 API 이용
        // CountDownLatch 는 테스트코드에서 모든 실행이 완료될때까지 사용할 용도로 사용한 것으로 레이스 컨디션과는 무관
        // 100개의 요청이 끝날 때 까지 기다려야하므로 CountDownLatch 이용 (다른 스레드에서 수행중인 테스트가 완료될 때 까지 대기)
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    lettuceLockStockFacade.decrease(1L, 1L);
                } catch (RuntimeException | InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();

        Assertions.assertThat(stock.getQuantity()).isEqualTo(0);
    }

    @Test
    public void 동시에_100개의_요청_V6() throws InterruptedException {
        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(32); // 비동기 처리를 위해 java의 API 이용
        // CountDownLatch 는 테스트코드에서 모든 실행이 완료될때까지 사용할 용도로 사용한 것으로 레이스 컨디션과는 무관
        // 100개의 요청이 끝날 때 까지 기다려야하므로 CountDownLatch 이용 (다른 스레드에서 수행중인 테스트가 완료될 때 까지 대기)
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    redissonLockStockFacade.decrease(1L, 1L);
                } catch (RuntimeException e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        Stock stock = stockRepository.findById(1L).orElseThrow();

        Assertions.assertThat(stock.getQuantity()).isEqualTo(0);
    }


}