// П8. Свій event loop. Варіант на JavaScript.
//
// Той самий цикл, що й у Java-варіанті (EventLoop.java): один виконавець,
// черга задач, черга таймерів. setTimeout лише кладе задачу в чергу, а чекає
// за неї сам цикл.
//
// У заготовці вже працюють готовий приклад MiniLoop і три сценарії у файлі
// scenarios.js, які викликають ваш Loop. Дописати треба чотири методи класу
// Loop: submit, setTimeout, poll, run. Разом близько 20 рядків, місця
// позначені TODO.
//
// Запуск: node scenarios.js

class Loop {
  // Один відкладений таймер: коли його час і що робити.
  // У Java це record Timer(dueAt, job). Тут достатньо об'єкта { dueAt, job }.

  constructor() {
    this.tasks = [];  // черга готових задач
    this.timers = []; // черга таймерів: об'єкти { dueAt, job }
  }

  // Мілісекунди від старту процесу: еквівалент Loop.now() у Java-варіанті.
  static now() {
    return Date.now() - Loop.start;
  }

  // ---------------------- TODO [П8], чотири методи ----------------------
  // Разом близько 20 рядків.
  //
  //   submit(r)             покласти r у кінець черги задач.
  //   setTimeout(delay, r)  покласти в чергу таймерів об'єкт
  //                         { dueAt: Loop.now() + delay, job: r }.
  //                         dueAt абсолютний, а не залишок затримки.
  //   poll()                зняти й повернути наступну готову задачу, або null:
  //                         спершу черга задач (вона має пріоритет), і лише
  //                         якщо там порожньо — готовий таймер із найменшим
  //                         dueAt, і лише якщо його dueAt уже настав.
  //   run()                 крутити poll(): є задача — виконати її; немає,
  //                         але є таймери — почекати простим циклом очікування
  //                         до найближчого dueAt; обидві черги порожні — вийти.
  //
  // poll() має саме ЗНІМАТИ задачу з черги (shift або splice). Якщо тільки
  // подивитися, цикл виконає ту саму задачу назавжди і не завершиться:
  // зупиніть його Ctrl+C. Вартового, як у Java-варіанті, тут немає.
  // -----------------------------------------------------------------------

    submit(r) {
      this.tasks.push(r);
    }

    setTimeout(delay, r) {
      this.timers.push({ dueAt: Loop.now() + delay, job: r });
    }

    poll() {
      // 1) задачи в приоритете, и снимаем
      if (this.tasks.length > 0) return this.tasks.shift();

      // 2) готовый таймер с наименьшим dueAt
      let best = -1;
      for (let i = 0; i < this.timers.length; i++) {
        const t = this.timers[i];
        if (t.dueAt <= Loop.now() && (best === -1 || t.dueAt < this.timers[best].dueAt)) {
          best = i;
        }
      }
      if (best !== -1) return this.timers.splice(best, 1)[0].job;

      // 3) готовой работы нет
      return null;
    }

    run() {
      while (true) {
        const r = this.poll();
        if (r) {
          r();
          continue;
        }
        if (this.timers.length === 0) break; // обе очереди пусты

        let minDue = this.timers[0].dueAt;
        for (const t of this.timers) if (t.dueAt < minDue) minDue = t.dueAt;
        while (Loop.now() < minDue) {}       // простое ожидание
      }
    }
}

Loop.start = Date.now();

module.exports = Loop;
