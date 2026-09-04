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

        // Частина 2. Ті самі сторінки у чотирьох потоках.
        System.out.println("\nу 4 потоки:");
        t0 = System.nanoTime();

        // TODO [П1]: дописати паралельну частину, близько 10 рядків.
        //   1) створити масив Thread[THREADS]
        //   2) роздавати сторінки через крок, як карти з колоди: потік me бере
        //      сторінки me + 1, me + 1 + THREADS, me + 1 + 2 * THREADS, ...
        //      такий розподіл працює для будь-якої кількості сторінок
        //   3) кожен потік отримує ім'я "каталог-1" ... "каталог-4"
        //   4) Java не дасть захопити змінну циклу в лямбду:
        //      всередині циклу потрібен рядок final int me = t;
        //   5) спершу всі start(), і тільки потім усі join()

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
