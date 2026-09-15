// П2. Лічильник, який бреше.
//
// Чотири потоки роблять по 50 000 інкрементів одного лічильника, разом 200 000.
// Звичайне поле int щоразу показує менше, і щоразу інше число: broken++ це три
// операції (прочитати, додати, записати), і між ними встигає влізти інший потік.
//
// У заготовці вже працюють: навантаження load(), перша частина зі звичайним int
// і третя частина з volatile. Дописати треба другу, з AtomicInteger, близько
// 10 рядків, місце позначене TODO.
//
// Збірка і запуск:
//   javac -encoding UTF-8 Counter.java
//   java Counter

import java.util.concurrent.atomic.AtomicInteger;

public class Counter {

    static final int THREADS = 4;        // потоків
    static final int HITS = 50_000;      // інкрементів на кожен потік
    static final int TOTAL = THREADS * HITS;

    static int broken;                             // звичайне поле
    static final AtomicInteger safe = new AtomicInteger();
    static volatile int visible;                   // volatile, але все одно ++

    public static void main(String[] args) throws InterruptedException {
        System.out.println(THREADS + " потоки × " + HITS + " інкрементів = " + TOTAL + " очікуваних");

        // Частина 1. Звичайне поле int. Готово.
        System.out.println("\nзвичайний int, broken++:");
        for (int run = 1; run <= 3; run++) {
            broken = 0;
            load(() -> broken++);
            line(run, broken);
        }

        // Частина 2. Той самий лічильник, але атомарний.
        System.out.println("\nAtomicInteger, safe.incrementAndGet():");

        // TODO [П2]: дописати другу частину, близько 10 рядків.
        //   1) такий самий цикл на 3 прогони, як у першій частині;
        //   2) на початку прогону обнулити лічильник: safe.set(0);
        //   3) навантаження запускається так само: load(...), а всередині
        //      лямбди одна атомарна операція safe.incrementAndGet();
        //   4) надрукувати рядок прогону через line(run, safe.get()).
        //   Усі три прогони мають дати рівно TOTAL і нуль загублених.
        for (int run = 1; run <= 3; run++) {
            safe.set(0);
            load(() -> safe.incrementAndGet());
            line(run, safe.get());
        }

        // Частина 3. volatile не рятує: ++ лишається трьома операціями. Готово.
        System.out.println("\nvolatile int, visible++:");
        for (int run = 1; run <= 3; run++) {
            visible = 0;
            load(() -> visible++);
            line(run, visible);
        }
        System.out.println("\nvolatile робить запис видимим іншим потокам,");
        System.out.println("але не робить ++ однією операцією. Числа знову неповні.");
    }

    /** THREADS потоків, кожен виконує inc рівно HITS разів. Чекає всіх. */
    static void load(Runnable inc) throws InterruptedException {
        Thread[] w = new Thread[THREADS];
        for (int t = 0; t < THREADS; t++) {
            w[t] = new Thread(() -> { for (int i = 0; i < HITS; i++) inc.run(); });
        }
        for (Thread x : w) x.start();      // спершу стартуємо всіх
        for (Thread x : w) x.join();       // і тільки потім чекаємо
    }

    /** Один рядок звіту: скільки нарахували і скільки загубили. */
    static void line(int run, int got) {
        System.out.println("  прогін " + run + ": " + got + ", загублено " + (TOTAL - got));
    }
}
