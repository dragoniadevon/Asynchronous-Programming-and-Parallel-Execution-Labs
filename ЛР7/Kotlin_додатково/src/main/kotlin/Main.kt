import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

// Стани читаємо після завершення coroutineScope.
data class Result(val livedMs: Long, val sawCancellation: Boolean, val cleaned: Boolean)

suspend fun task(name: String, kind: String, ms: Long, results: MutableMap<String, Result>) {
    val start = System.nanoTime()
    var sawCancellation = false
    try {
        when (kind) {
            "fail" -> { delay(ms); error("$name: джерело впало") }
            "wait" -> delay(ms) // delay реагує на скасування
            "cpu" -> {
                val end = System.nanoTime() + ms * 1_000_000
                while (System.nanoTime() < end) { /* навмисно без перевірки */ }
            }
            else -> error("Невідомий тип задачі: $kind")
        }
    } catch (e: CancellationException) {
        sawCancellation = true
        throw e // не приховуємо скасування
    } finally {
        val elapsed = (System.nanoTime() - start) / 1_000_000
        results[name] = Result(elapsed, sawCancellation, cleaned = true)
    }
}

fun main() = runBlocking {
    val results = ConcurrentHashMap<String, Result>()
    val start = System.nanoTime()
    try {
        coroutineScope {
            // TODO: запустіть чотири дочірні корутини через launch(Dispatchers.Default):
            // task("ціна", "fail", 100, results)
            // task("рецензії", "wait", 2000, results)
            // task("сусіди", "wait", 2000, results)
            // task("рейтинг", "cpu", 600, results)
            // Усі launch мають належати цій coroutineScope.
            launch(Dispatchers.Default) { task("ціна", "fail", 100, results) }
            launch(Dispatchers.Default) { task("рецензії", "wait", 2000, results) }
            launch(Dispatchers.Default) { task("сусіди", "wait", 2000, results) }
            launch(Dispatchers.Default) { task("рейтинг", "cpu", 600, results) }
        }
    } catch (e: IllegalStateException) {
        println("впала: ${e.message}")
    }
    val elapsed = (System.nanoTime() - start) / 1_000_000
    println("область завершилась: $elapsed мс")
    for (name in listOf("ціна", "рецензії", "сусіди", "рейтинг")) {
        println("$name: ${results[name] ?: "немає результату"}")
    }
}
