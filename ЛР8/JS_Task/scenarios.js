// П8, варіант на JavaScript. Готовий приклад MiniLoop і три сценарії,
// які викликають ваш Loop із loop.js.
//
// Запуск: node scenarios.js
// Очікуваний вивід і пояснення: README.md у папці на рівень вище.

const Loop = require('./loop');

/** Задача, яка нічого не чекає і просто рахує. */
function burn(ms) {
  const end = Date.now() + ms;
  while (Date.now() < end) {}
}

/* ===== Готовий приклад: черга задач без таймерів. Вивід [A, B, C]. ===== */

function demo() {
  const mini = {
    queue: [],
    post(r) { this.queue.push(r); },
    run() { while (this.queue.length > 0) this.queue.shift()(); }
  };
  const out = [];
  mini.post(() => { out.push('A'); mini.post(() => out.push('C')); out.push('B'); });
  mini.run();
  console.log('MiniLoop без таймерів: [' + out.join(', ') + ']');
  console.log('Задачу C поставили посеред задачі A, виконалася вона останньою.\n');
}

/* ========================= Три сценарії ========================= */

/** Друк, setTimeout на 0, друк. Повертає вивід до run() і після нього. */
function order() {
  const loop = new Loop();
  const out = [];
  loop.submit(() => out.push('1'));
  loop.setTimeout(0, () => out.push('2'));
  loop.submit(() => out.push('3'));
  const beforeRun = out.join(',');
  loop.run();
  return [beforeRun, out.join(',')];
}

/** Три таймери, зареєстровані в порядку 60, 20, 40. Хто коли спрацював. */
function timers() {
  const loop = new Loop();
  const fired = {};
  const t0 = Loop.now();
  loop.setTimeout(60, () => { fired.c = Loop.now() - t0; });
  loop.setTimeout(20, () => { fired.a = Loop.now() - t0; });
  loop.setTimeout(40, () => { fired.b = Loop.now() - t0; });
  loop.run();
  return fired;
}

/** Таймер на 100 мс у лупі, зайнятому обчисленням на blockMs. Коли спрацював. */
function timerUnderLoad(blockMs) {
  const loop = new Loop();
  const state = { firedAt: -1 };
  const t0 = Loop.now();
  if (blockMs > 0) loop.submit(() => burn(blockMs));
  loop.setTimeout(100, () => { state.firedAt = Loop.now() - t0; });
  loop.run();
  return state.firedAt;
}

/* ========================= Запуск ========================= */

demo();
const o = order();
console.log('1. Вивід до run(): [' + o[0] + '], після run(): [' + o[1] + ']');
console.log('   Задачі мають пріоритет над таймерами, тому 1,3,2, а не 1,2,3.');
const fired = timers();
console.log('2. Таймери, зареєстровані 60/20/40: { ' +
  Object.keys(fired).map(k => k + '=' + fired[k]).join(', ') + ' }');
console.log('   Порядок задає дедлайн, а не реєстрація.');
console.log('3. Таймер на 100 мс у вільному лупі: ' + timerUnderLoad(0) + ' мс');
console.log('   Той самий таймер, коли задача рахує 800 мс: ' + timerUnderLoad(800) + ' мс');
console.log('   Один виконавець: поки задача рахує, таймер чекає. Це і є лаг event loop.');
