// 生成符合手环协议格式（training-records）的伪数据：2026-05 ~ 2026-08（约4个月）
// 每3天训练一次，4套动作轮换；每个动作重量每出现3次 +step（制造PR）
const fs = require('fs');
const path = require('path');

const END = new Date(2026, 7, 15);   // 今天 = 2026-08-15
const START = new Date(2026, 4, 2);  // 首个训练日 = 2026-05-02（此后每3天一次，最后一天=08-15）

// [动作, 基础重量kg, 递增步长, 次数数组]
const TEMPLATES = [
  [['杠铃深蹲', 80, 5, [8, 8, 7]], ['腿举', 120, 10, [10, 10, 10]]],
  [['杠铃卧推', 60, 5, [8, 8, 6, 6]], ['哑铃飞鸟', 14, 2, [12, 12, 10]]],
  [['高位下拉', 45, 2, [10, 10, 8, 8]], ['坐姿划船', 40, 2, [10, 10, 10]]],
  [['杠铃推举', 35, 5, [8, 8, 8]], ['哑铃侧平举', 12, 2, [12, 12, 12]]],
];

function iso(d) { return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0'); }

const counts = {};
function weightFor(name, base, step) {
  const c = counts[name] || 0;
  return base + Math.floor(c / 3) * step;
}

const days = [];
let ti = 0;
const cur = new Date(START);
while (cur <= END) {
  const tmpl = TEMPLATES[ti % TEMPLATES.length];
  const exs = tmpl.map(([name, base, step, reps]) => {
    const w = weightFor(name, base, step);
    counts[name] = (counts[name] || 0) + 1;
    return [name, w, reps];
  });
  days.push([iso(cur), exs]);
  ti++;
  cur.setDate(cur.getDate() + 3);
}

function dayLine(date, exs) {
  const records = exs.map(([exercise, weight, reps]) => ({
    exercise,
    weight,
    sets: reps.map((r, i) => ({ set: i + 1, reps: r, volume: weight * r })),
  }));
  return JSON.stringify({ type: 'training-records', date, records });
}

const jsonl = days.map(([d, exs]) => dayLine(d, exs)).join('\n');
fs.writeFileSync(path.join(__dirname, 'mock-data.jsonl'), jsonl + '\n', 'utf8');
fs.writeFileSync(path.join(__dirname, 'mock-data.js'), 'window.MOCK_JSONL = `' + jsonl + '`;\n', 'utf8');
console.log('wrote ' + days.length + ' days (' + days[0][0] + ' .. ' + days[days.length - 1][0] + ')');
