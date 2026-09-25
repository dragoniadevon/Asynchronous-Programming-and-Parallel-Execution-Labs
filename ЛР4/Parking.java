// П4. П'ять місць і два замки. Дві незалежні частини в одному файлі.
//
// ЧАСТИНА А. Постачальник тримає не більше п'яти з'єднань, а enter() зараз пускає
// в сервіс усі 12 задач одразу. Треба впустити не більше п'яти і повернути дозвіл
// навіть тоді, коли робота впала з виключенням. Один TODO, близько 6 рядків.
// Поруч лежить контрприклад leaky(): там release стоїть після роботи, а не у
// finally, і після п'яти виключень дозволів не лишається зовсім.
//
// ЧАСТИНА Б. park() і leave() беруть два замки, шлагбаум і касу. Поодинці кожен
// працює, разом програма висне. TODO на дефекті немає: знайти і виправити його
// треба самому, приблизно 2 рядки.
//
// ЯК ЗНЯТИ ДАМП, поки програма висить (у другому вікні терміналу):
//   jps            знайти рядок Parking і взяти pid
//   jstack <pid>   шукати «Found one Java-level deadlock:»
// У дампі будуть потоки park і leave: кожен тримає один замок (Gate або Till)
// і чекає на другий. Через 20 секунд програма завершиться сама.
//
// Збірка і запуск:
//   javac -encoding UTF-8 Parking.java
//   java Parking

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class Parking {

    static final int PERMITS = 5;   // скільки з'єднань дозволяє постачальник
    static final int TASKS = 12;    // скільки задач хочуть до нього одночасно
    static final int ROUNDS = 50;   // раундів у частині Б

    static final Semaphore permits = new Semaphore(PERMITS);

    static final AtomicInteger inService = new AtomicInteger();   // зараз у сервісі
    static final AtomicInteger peak = new AtomicInteger();        // найбільше за прогін

    // ------------------------------------------------- ЧАСТИНА А: обмежувач

    /** Робота, яку треба обмежити: ≈250 мс. На шляху «/зламаний» кидає виключення. */
    static String work(String path) {
        peak.accumulateAndGet(inService.incrementAndGet(), Math::max);
        try { Thread.sleep(250); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        inService.decrementAndGet();
        if (path.startsWith("/зламаний")) throw new IllegalStateException(path + " відповів 503");
        return path + " готово";
    }

    static String enter(String path) throws InterruptedException {
        // TODO [П4-А]: впустити в work() не більше PERMITS потоків одночасно.
        //   1) permits.acquire() до try, а не всередині нього;
        //   2) return work(path); усередині try;
        //   3) permits.release() у finally, і тільки там: work() інколи кидає
        //      виключення, і без finally дозвіл не повернеться (див. leaky());
        //   4) ліміт саме 5, а не 1: synchronized пропустив би по одному.
        return work(path);
    }

    /** Як не треба. Не правити: це контрприклад у звіті. */
    static String leaky(String path) throws InterruptedException {
        permits.acquire();
        String body = work(path);      // на зламаному шляху вилітає звідси
        permits.release();             // і цей рядок не виконується
        return body;
    }

    // ------------------------------------------------- ЧАСТИНА Б: два замки

    // Окремі класи, щоб у дампі jstack замок називався своїм іменем,
    // а не безликим «a java.lang.Object».
    static final class Gate {}
    static final class Till {}
    static final Gate GATE = new Gate();     // шлагбаум
    static final Till TILL = new Till();     // каса
    static int parked;   // скільки машин заїхало
    static int paid;     // скільки заплатило на виїзді

    static void park() {
        synchronized (GATE) {
            hold();
            synchronized (TILL) { parked++; }
        }
    }

    static void leave() {
        synchronized (TILL) {
            hold();
            synchronized (GATE) { paid++; }
        }
    }

    /** Пауза між захопленням першого і другого замка. */
    static void hold() {
        try { Thread.sleep(1); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    // ---------------------------------------------------------------- звіт

    public static void main(String[] args) throws InterruptedException {
        System.out.println("ЧАСТИНА А. " + TASKS + " задач по ≈250 мс, дозволів " + PERMITS + ".");
        peak.set(0);
        int done = batch(Parking::enter, "/склад/", TASKS);
        System.out.println("  виконано " + done + " з " + TASKS + ", пік одночасних у сервісі " + peak.get()
                + ", вільних дозволів у кінці " + permits.availablePermits());

        System.out.println("\nКонтрприклад leaky(): release стоїть після роботи, а не у finally.");
        batch(Parking::leaky, "/зламаний/", PERMITS);
        System.out.println("  після " + PERMITS + " виключень вільних дозволів " + permits.availablePermits()
                + " з " + PERMITS);
        permits.release(PERMITS - permits.availablePermits());   // повертаємо семафор до норми

        System.out.println("\nЧАСТИНА Б. " + ROUNDS + " раундів park() і leave() у два потоки.");
        System.out.println("Якщо вивід обірвався тут, ви бачите дедлок. Знімайте дамп зараз,");
        System.out.println("у другому вікні: jps, далі jstack <pid>. Є 20 секунд.");
        parked = 0;
        paid = 0;
        Thread a = daemon("park",   () -> { for (int i = 0; i < ROUNDS; i++) park(); });
        Thread b = daemon("leave",  () -> { for (int i = 0; i < ROUNDS; i++) leave(); });
        long t0 = System.nanoTime();
        a.start(); b.start();
        long deadline = System.currentTimeMillis() + 20_000;
        a.join(Math.max(1, deadline - System.currentTimeMillis()));
        b.join(Math.max(1, deadline - System.currentTimeMillis()));
        boolean ok = !a.isAlive() && !b.isAlive();
        System.out.printf("  %s за %.2f с: заїхало %d з %d, заплатило %d з %d%n",
                ok ? "завершилися" : "НЕ завершилися", (System.nanoTime() - t0) / 1e9,
                parked, ROUNDS, paid, ROUNDS);
    }

    /** n задач у n потоках. Повертає, скільки з них дійшли до кінця. */
    static int batch(Task task, String prefix, int n) throws InterruptedException {
        AtomicInteger ok = new AtomicInteger();
        Thread[] w = new Thread[n];
        for (int i = 0; i < n; i++) {
            final String path = prefix + (i + 1);
            w[i] = new Thread(() -> {
                try { if (task.run(path) != null) ok.incrementAndGet(); }
                catch (Exception ignored) { }
            });
        }
        for (Thread x : w) x.start();
        for (Thread x : w) x.join();
        return ok.get();
    }

    interface Task { String run(String path) throws InterruptedException; }

    static Thread daemon(String name, Runnable body) {
        Thread t = new Thread(body, name);
        t.setDaemon(true);
        return t;
    }
}
