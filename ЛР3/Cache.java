// П3.
//
// Два завдання.
//
// Перше — кеш котирувань. Схема звичайна: подивився в мапу, не знайшов, сходив
// у сервіс, поклав у мапу. Але коли за одним символом одночасно приходять 200
// клієнтів, у сервіс іде не 1 запит і не 200, а щоразу інше число. Кожен, хто
// встиг глянути в мапу до першого put, платить за котирування окремо.
//
// Друге — переказ. На гаманці покупця 1000, у касі магазину стільки ж. Один
// потік платить 50 000 разів, другий у той самий час стільки ж повертає. Без
// захисту сума попливе, а має завжди стояти 2000. Це інваріант, і його треба
// захистити.
//
// У заготовці вже працюють підроблений сервіс quote(), навантаження hammer()
// і частина з RacyCache. Дописати треба два класи: SafeCache (TODO A і TODO B,
// близько 4 рядків) і Till (TODO C, близько 10 рядків). Разом — рядків 15
// власного коду.
//
// Збірка і запуск:
//   javac -encoding UTF-8 Cache.java
//   java Cache

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

public class Cache {

    static final String SYMBOL = "AAPL";
    static final int CLIENTS = 200;

    /** Скільки разів справді ходили в сервіс. Обнуляється перед кожним прогоном. */
    static final AtomicInteger calls = new AtomicInteger();

    /** Підроблений сервіс котирувань: відповідає приблизно за 300 мс. Готово. */
    static String quote(String symbol) {
        calls.incrementAndGet();
        try { Thread.sleep(300); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        return symbol + "=173.42";
    }

    /** Схема «подивився, немає, сходив, поклав». Не правити: це рядок «до» у звіті. */
    static final class RacyCache {
        private final Map<String, String> map = new HashMap<>(64);

        String get(String symbol) {
            String hit = map.get(symbol);            // подивився
            if (hit != null) return hit;             // знайшов, віддав
            String body = quote(symbol);             // сходив, ≈300 мс
            map.put(symbol, body);                   // поклав
            return body;
        }
    }

    /**
     * Ваша версія кеша. На 200 клієнтів має піти рівно один запит, і кеш має
     * порахувати, скільки разів його просили саме цей символ.
     */
    static final class SafeCache {
        private final ConcurrentHashMap<String, String> map = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<String, Integer> hits = new ConcurrentHashMap<>();

        String get(String symbol) {
            // TODO A [П3]: спершу порахувати звернення: hits.merge(symbol, 1, Integer::sum).
            //   Потім віддати значення з map, а якщо ключа немає, узяти його з quote(symbol)
            //   і покласти назад, усе однією операцією computeIfAbsent(ключ, функція).
            //   Функція виконується рівно один раз на ключ, решта потоків чекає на її
            //   результат. Дві атомарні операції поспіль тут не страшні: лічильник і
            //   значення живуть окремо, і гонка між ними нічого не ламає.
            hits.merge(symbol, 1, Integer::sum);
            return map.computeIfAbsent(symbol, s -> quote(s));
        }

        /** Скільки разів кеш просили саме цей символ. */
        int hits(String symbol) {
            // TODO B [П3]: одне число з мапи hits, а якщо ключа ще немає, нуль.
            return hits.getOrDefault(symbol, 0);
        }
    }

    /**
     * Частина 4. Каса з інваріантом: гаманець плюс каса завжди 2000.
     * Один потік платить 50 000 разів, другий стільки ж повертає, і після
     * обох на рахунках мусить знову стояти 2000.
     */
    static final class Till {
        // TODO C [П3]: два поля по 1000: wallet (гаманець) і cashbox (каса).
        //   pay(): мінус одиниця з гаманця, плюс одиниця в касу.
        //   refund(): те саме в зворотний бік.
        //   total(): сума двох рахунків.
        //   Захист на вибір: або synchronized на всіх трьох методах, або один
        //   ReentrantLock на клас (для нього потрібен окремий імпорт) з unlock()
        //   у finally. Пам'ятайте з лекції: два різні lock на pay і refund
        //   захищають кожен своє, а разом не захищають нічого.

        private int wallet = 1000;
        private int cashbox = 1000;

        synchronized void pay() { wallet--; cashbox++; }

        synchronized void refund() { wallet++; cashbox--; }

        synchronized int total() { return wallet + cashbox; }
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println(CLIENTS + " клієнтів просять " + SYMBOL + ", приходять з інтервалом 2 мс.");
        System.out.println("Котирування коштує ≈300 мс.\n");

        // Частина 1. RacyCache: три прогони, три різні числа. Готово.
        System.out.println("RacyCache, схема «подивився, немає, сходив, поклав»:");
        for (int run = 1; run <= 3; run++) {
            calls.set(0);
            hammer(new RacyCache()::get);
            System.out.println("  прогін " + run + ": запитів до сервісу " + calls.get());
        }

        // Частина 2. SafeCache: три прогони, скрізь одиниця і рівно 200 звернень.
        System.out.println("\nSafeCache, атомарні операції мапи:");
        for (int run = 1; run <= 3; run++) {
            calls.set(0);
            SafeCache cache = new SafeCache();
            String[] got = hammer(cache::get);
            System.out.println("  прогін " + run + ": запитів до сервісу " + calls.get()
                    + ", звернень до кеша " + cache.hits(SYMBOL)
                    + ", однакове в усіх: " + same(got));
        }

        // Частина 3. Прогрітий кеш більше не платить. Готово.
        calls.set(0);
        SafeCache warm = new SafeCache();
        for (int i = 0; i < 5; i++) warm.get(SYMBOL);
        System.out.println("\nп'ять звернень до прогрітого кеша: запитів до сервісу "
                + calls.get() + " (має бути 1)");

        // Частина 4. Каса: 50 000 оплат і 50 000 повернень одночасно, три прогони.
        System.out.println("\nTill, переказ під захистом:");
        for (int run = 1; run <= 3; run++) {
            Till till = new Till();
            Thread pay    = new Thread(() -> { for (int i = 0; i < 50_000; i++) till.pay(); });
            Thread refund = new Thread(() -> { for (int i = 0; i < 50_000; i++) till.refund(); });
            pay.start(); refund.start();
            pay.join(); refund.join();
            System.out.println("  прогін " + run + ": гаманець+каса " + till.total()
                    + ", мало бути 2000");
        }
    }

    /**
     * CLIENTS клієнтів просять той самий символ. Приходять не строго разом, а з
     * інтервалом 2 мс: саме через це число запитів у RacyCache щоразу інше.
     */
    static String[] hammer(UnaryOperator<String> cache) throws InterruptedException {
        String[] out = new String[CLIENTS];
        Thread[] w = new Thread[CLIENTS];
        for (int i = 0; i < CLIENTS; i++) {
            final int me = i;
            w[i] = new Thread(() -> {
                try { Thread.sleep(me * 2L); } catch (InterruptedException e) { return; }
                out[me] = cache.apply(SYMBOL);
            });
        }
        for (Thread x : w) x.start();
        for (Thread x : w) x.join();
        return out;
    }

    /** Чи всі клієнти отримали одне й те саме значення. */
    static boolean same(String[] got) {
        for (String s : got) if (s == null || !s.equals(got[0])) return false;
        return true;
    }
}
