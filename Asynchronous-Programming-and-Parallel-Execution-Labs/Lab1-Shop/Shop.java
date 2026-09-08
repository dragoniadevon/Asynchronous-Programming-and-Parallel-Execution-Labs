// П1. Вісім сторінок каталогу.
//
// Той самий магазин, що на лекції. Постачальник віддає кожну сторінку
// приблизно за секунду, покупець відкрив розділ на 8 сторінок. Послідовно
// такий розділ вантажиться 8 секунд, і весь цей час програма чекає.
//
// У заготовці вже працює: loadPage(), замір часу і послідовна частина.
// Дописати треба паралельну: чотири потоки по дві сторінки, спершу всі
// start(), потім усі join(). Місце позначене TODO унизу main.
//
// Збірка і запуск:
//   javac -encoding UTF-8 Shop.java
//   java Shop

public class Shop {

    static final int PAGES = 8;     // сторінок у розділі
    static final int THREADS = 4;   // потоків у другій частині

    public static void main(String[] args) throws InterruptedException {
        System.out.println("ядер у машині: " + Runtime.getRuntime().availableProcessors());

        // Частина 1. Послідовно: головний потік несе всі сторінки по черзі. Готово.
        System.out.println("\nпослідовно:");
        long t0 = System.nanoTime();
        for (int page = 1; page <= PAGES; page++) loadPage(page);
        seconds("час", t0);



        // Частина 2. Паралельно
        System.out.println("\nу 4 потоки:");
        t0 = System.nanoTime();

// 1) створюємо масив потоків
        Thread[] threads = new Thread[THREADS];

// 2) роздаємо сторінки через крок
        for (int t = 0; t < THREADS; t++) {
            final int me = t; // 4) потрібен final
            threads[t] = new Thread(() -> {
                for (int page = me + 1; page <= PAGES; page += THREADS) {
                    loadPage(page);
                }
            }, "каталог-" + (me + 1)); // 3) ім’я потоку
        }

// 5) спершу всі start()
        for (Thread thread : threads) thread.start();
// потім усі join()
        for (Thread thread : threads) thread.join();

        System.out.println("усі " + PAGES + " сторінок готові");
        seconds("час", t0);


    }

    /** Одна сторінка каталогу: постачальник відповідає приблизно за секунду. */
    static void loadPage(int page) {
        try { Thread.sleep(1000); } catch (InterruptedException e) { }
        System.out.println("  [" + Thread.currentThread().getName() + "] сторінка " + page + " готова");
    }

    /** Друкує секунди, що минули від мітки t0 до зараз. */
    static void seconds(String label, long t0) {
        System.out.println(label + ": " + String.format("%.2f", (System.nanoTime() - t0) / 1_000_000_000.0) + " с");
    }
}
