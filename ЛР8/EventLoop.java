// П8. Свій event loop.
//
// Пишемо той цикл, на якому тримається Node.js: один потік, черга задач, черга
// таймерів. Ніяких додаткових потоків заводити не треба: setTimeout лише кладе
// задачу в чергу, а чекає за неї сам цикл.
//
// У заготовці вже працюють: готовий приклад MiniLoop (черга задач без таймерів,
// читайте його першим) і три сценарії, які викликають ваш Loop. Дописати треба
// чотири тіла в класі Loop, разом близько 20 рядків, місце позначене TODO.
//
// Збірка і запуск:
//   javac -encoding UTF-8 EventLoop.java
//   java EventLoop

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class EventLoop {

    /* ===== Готовий приклад: черга задач без таймерів. Вивід [A, B, C]. ===== */

    static final class MiniLoop {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        void post(Runnable r) { queue.addLast(r); }
        void run() { while (!queue.isEmpty()) queue.pollFirst().run(); }
    }

    static void demo() {
        MiniLoop mini = new MiniLoop();
        List<String> out = new ArrayList<>();
        mini.post(() -> { out.add("A"); mini.post(() -> out.add("C")); out.add("B"); });
        mini.run();
        System.out.println("MiniLoop без таймерів: " + out);
        System.out.println("Задачу C поставили посеред задачі A, виконалася вона останньою.\n");
    }

    /* ============================ Ваш луп ============================ */

    static final class Loop {

        /** Одна відкладена задача: коли її час і що робити. */
        record Timer(long dueAt, Runnable job) {}

        final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
        final PriorityQueue<Timer> timers =
                new PriorityQueue<>(Comparator.comparingLong(Timer::dueAt));

        /** Мілісекунди від старту JVM. */
        static long now() { return System.nanoTime() / 1_000_000L; }

        /** Сон потоку лупа. */
        static void sleepMs(long ms) {
            try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        // ---------------------- TODO [П8], єдине місце у файлі ----------------------
        // Чотири тіла нижче, разом близько 20 рядків. Приклад черги: MiniLoop вище.
        //
        //   submit(r)             покласти r у кінець черги задач.
        //   setTimeout(delay, r)  покласти в чергу таймерів new Timer(now() + delay, r).
        //                         Час у Timer.dueAt() абсолютний, а не залишок затримки.
        //   poll()                зняти й повернути наступну готову задачу, або null:
        //                         спершу дивимось у чергу задач (вона має пріоритет),
        //                         і лише якщо там порожньо — у голову черги таймерів,
        //                         і лише якщо її dueAt() уже настав.
        //   run()                 крутити poll(): є задача — виконати її; немає, але є
        //                         таймери — поспати sleepMs() до найближчого dueAt;
        //                         обидві черги порожні — вийти.
        //
        // poll() має саме ЗНІМАТИ задачу з черги. Якщо тільки подивитися (peekFirst),
        // цикл виконає ту саму задачу назавжди і програма не завершиться.
        // ---------------------------------------------------------------------------

        void submit(Runnable r) {
            tasks.addLast(r);
        }

        void setTimeout(long delayMs, Runnable r) {
            // dueAt абсолютний: now() + delayMs
            timers.add(new Timer(now() + delayMs, r));
        }

        Runnable poll() {
            // 1) задачі мають пріоритет, і задачу знімаємо з черги
            if (!tasks.isEmpty()) return tasks.pollFirst();

            // 2) готовий таймер: peek дивиться, poll знімає
            Timer head = timers.peek();
            if (head != null && head.dueAt() <= now()) {
                return timers.poll().job();
            }

            // 3) готової роботи ще немає
            return null;
        }

        void run() {
            while (true) {
                Runnable r = poll();
                if (r != null) {
                    r.run();
                    continue;
                }
                if (timers.isEmpty()) break; // обидві черги порожні
                sleepMs(Math.max(0, timers.peek().dueAt() - now()));
            }
        }
    }

    /* ========================= Три сценарії ========================= */

    /** Друк, setTimeout на 0, друк. Повертає вивід до run() і після нього. */
    static String[] order() {
        Loop loop = new Loop();
        List<String> out = new ArrayList<>();
        loop.submit(() -> out.add("1"));
        loop.setTimeout(0, () -> out.add("2"));
        loop.submit(() -> out.add("3"));
        String beforeRun = String.join(",", out);
        loop.run();
        return new String[]{ beforeRun, String.join(",", out) };
    }

    /** Три таймери, зареєстровані в порядку 60, 20, 40. Хто коли спрацював. */
    static Map<String, Long> timers() {
        Loop loop = new Loop();
        Map<String, Long> fired = new LinkedHashMap<>();
        long t0 = Loop.now();
        loop.setTimeout(60, () -> fired.put("c", Loop.now() - t0));
        loop.setTimeout(20, () -> fired.put("a", Loop.now() - t0));
        loop.setTimeout(40, () -> fired.put("b", Loop.now() - t0));
        loop.run();
        return fired;
    }

    /** Таймер на 100 мс у лупі, зайнятому обчисленням на blockMs. Коли спрацював. */
    static long timerUnderLoad(long blockMs) {
        Loop loop = new Loop();
        long[] firedAt = { -1 };
        long t0 = Loop.now();
        if (blockMs > 0) loop.submit(() -> burn(blockMs));
        loop.setTimeout(100, () -> firedAt[0] = Loop.now() - t0);
        loop.run();
        return firedAt[0];
    }

    /** Задача, яка нічого не чекає і просто рахує. */
    static void burn(long ms) {
        long end = System.nanoTime() + ms * 1_000_000L;
        while (System.nanoTime() < end) { }
    }

    public static void main(String[] args) {
        watchdog(20_000);
        demo();
        String[] o = order();
        System.out.println("1. Вивід до run(): [" + o[0] + "], після run(): [" + o[1] + "]");
        System.out.println("   Задачі мають пріоритет над таймерами, тому 1,3,2, а не 1,2,3.");
        System.out.println("2. Таймери, зареєстровані 60/20/40: " + timers());
        System.out.println("   Порядок задає дедлайн, а не реєстрація.");
        System.out.println("3. Таймер на 100 мс у вільному лупі: " + timerUnderLoad(0) + " мс");
        System.out.println("   Той самий таймер, коли задача рахує 800 мс: " + timerUnderLoad(800) + " мс");
        System.out.println("   Один потік: поки задача рахує, таймер чекає. Це і є лаг event loop.");
    }

    /** Якщо процес не завершився за limitMs, друкує підказку і гасить JVM. */
    static void watchdog(long limitMs) {
        Thread w = new Thread(() -> {
            try { Thread.sleep(limitMs); } catch (InterruptedException e) { return; }
            System.out.println("\nВАРТОВИЙ: " + limitMs + " мс без завершення.");
            System.out.println("Найчастіше так буває, коли poll() не знімає задачу з черги");
            System.out.println("або run() не виходить на порожніх чергах.");
            System.out.flush();
            Runtime.getRuntime().halt(1);
        }, "вартовий");
        w.setDaemon(true);
        w.start();
    }
}
