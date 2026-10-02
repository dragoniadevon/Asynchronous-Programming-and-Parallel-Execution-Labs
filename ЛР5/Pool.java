// П5. Скільки потоків насправді потрібно.
//
// 32 запити по ≈120 мс кожен. Варіант «потік на запит» створює під них 32 потоки:
// perThread() це вміє, і в таблиці він перший рядок. Ми робимо ту саму роботу на
// пулі фіксованого розміру і дивимось, що змінюється з його розміром.
//
// У заготовці вже працюють: підроблений сервіс fetch(), варіант «потік на запит»,
// фабрика потоків із лічильниками, таблиця і готова CPU-крива. Дописати треба
// runOnPool(), близько 12 рядків, місце позначене TODO.
//
// Збірка і запуск:
//   javac -encoding UTF-8 Pool.java
//   java Pool

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Pool {

    static final int REQUESTS = 32;

    static final AtomicInteger inService = new AtomicInteger();
    static final AtomicInteger peak = new AtomicInteger();

    /** Підроблений сервіс: відповідає приблизно за 120 мс. Готово. */
    static String fetch(String path) {
        peak.accumulateAndGet(inService.incrementAndGet(), Math::max);
        try { Thread.sleep(120); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        inService.decrementAndGet();
        return path + " готово";
    }

    /**
     * Фабрика потоків із лічильником. Пул, створений без неї, нічого не рахує:
     * колонка «створено потоків» у таблиці буде нульова.
     */
    static final class CountingFactory implements ThreadFactory {
        private final AtomicInteger created = new AtomicInteger();
        @Override public Thread newThread(Runnable body) {
            return new Thread(body, "пул-" + created.incrementAndGet());
        }
        int created() { return created.get(); }
    }

    // ------------------------------------------------------- єдиний TODO

    /** Виконати всі REQUESTS запитів на пулі розміром size. Повертає витрачений час у мс. */

        // TODO [П5]: близько 12 рядків.
        //   1) ExecutorService pool = Executors.newFixedThreadPool(size, factory);
        //      фабрику передати обов'язково, інакше колонка «створено» буде нульова;
        //   2) заміряти час: long t0 = System.currentTimeMillis();
        //   3) на кожен шлях "/сторінка/" + i покласти в пул задачу
        //      pool.submit(() -> fetch(path)) і зібрати всі Future у список;
        //   4) окремим циклом забрати результати через future.get();
        //      спершу всі submit, і тільки потім усі get, інакше задачі підуть по черзі;
        //   5) pool.shutdown() і pool.awaitTermination(5, TimeUnit.SECONDS);
        //      без shutdown потоки пула лишаються живі, і JVM не завершується;
        //   6) повернути System.currentTimeMillis() - t0.

    static long runOnPool(int size, CountingFactory factory) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(size, factory);
        long t0 = System.currentTimeMillis();

        List<Future<String>> futures = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            final String path = "/сторінка/" + i;
            futures.add(pool.submit(() -> fetch(path)));
        }

        for (Future<String> f : futures) {
            f.get();
        }

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        return System.currentTimeMillis() - t0;
    }

    // ---------------------------------------------------------- дано нижче

    /** «Потік на запит»: 32 потоки на 32 запити. Перший рядок таблиці. */
    static long perThread() throws Exception {
        long t0 = System.currentTimeMillis();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < REQUESTS; i++) {
            final String path = "/сторінка/" + i;
            Thread t = new Thread(() -> fetch(path));
            threads.add(t);
            t.start();
        }
        for (Thread t : threads) t.join();
        return System.currentTimeMillis() - t0;
    }

    /** Готова CPU-крива. Відтворювати її не треба, вона потрібна для порівняння. */
    static final String CPU_CURVE = """
            CPU-задача без звернень у пам'ять: 256 порцій по 3 млн обчислень sqrt на пулі.
            Zulu 17.0.17, macOS, 10 ядер (4 швидких і 6 економних):
               1 потік  -> 776 мс     2 ->  277 мс     4 -> 129 мс     8 -> 72 мс
              10 потоків ->  64 мс    16 ->   64 мс   64 ->  69 мс
            Час падає до числа ядер і там зупиняється: 10 і 16 потоків дають ті самі
            64 мс, на 64 потоках уже 69. Наша задача не така: вона не рахує, а чекає,
            і тому виграє від потоків, яких більше за ядра.""";

    public static void main(String[] args) throws Exception {
        System.out.println("ядер у машині: " + Runtime.getRuntime().availableProcessors());
        System.out.println(REQUESTS + " запитів по ≈120 мс кожен.\n");

        System.out.printf("%16s | %8s | %10s | %s%n", "варіант", "час, мс", "створено", "пік у сервісі");
        peak.set(0);
        System.out.printf("%16s | %8d | %10d | %d%n", "потік на запит", perThread(), REQUESTS, peak.get());

        for (int size : new int[]{1, 4, 32}) {
            peak.set(0);
            CountingFactory factory = new CountingFactory();
            long ms = runOnPool(size, factory);
            System.out.printf("%16s | %8d | %10d | %d%n", "пул " + size, ms, factory.created(), peak.get());
        }

        System.out.println("\nЯкщо програма не завершилася сама, у пулі лишилися живі потоки:");
        System.out.println("це означає, що десь немає shutdown().\n");
        System.out.println(CPU_CURVE);
    }
}
