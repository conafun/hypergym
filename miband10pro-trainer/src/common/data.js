var GROUP_ORDER = ['胸', '肩', '背', '腿', '臂', '核心']

// 部位 → 颜色（与手机端肌群配色一致）
var GROUP_COLORS = {
  '胸': '#FA734F',
  '肩': '#F0A47F',
  '背': '#95DAE7',
  '腿': '#9CCFBF',
  '臂': '#A98BB4',
  '核心': '#7FB3D5',
  '其他': '#C7BEAF'
}

var DEFAULT_EXERCISES = [
  { name: "哑铃", group: "肩", minWt: 20, maxWt: 60, step: 2 },
  { name: "侧平举", group: "肩", minWt: 10, maxWt: 60, step: 2 },
  { name: "卷腹", group: "核心", minWt: 10, maxWt: 50, step: 5 },
  { name: "卧推", group: "胸", minWt: 40, maxWt: 90, step: 5 },
  { name: "水平胸推", group: "胸", minWt: 20, maxWt: 90, step: 5 },
  { name: "坐姿肩推", group: "肩", minWt: 20, maxWt: 50, step: 5 },
  { name: "坐姿飞鸟", group: "胸", minWt: 10, maxWt: 50, step: 2 },
  { name: "下斜胸推", group: "胸", minWt: 30, maxWt: 90, step: 5 },
  { name: "哈克深蹲", group: "腿", minWt: 30, maxWt: 90, step: 5 },
  { name: "高位下拉", group: "背", minWt: 20, maxWt: 60, step: 2 },
  { name: "大剪刀", group: "背", minWt: 30, maxWt: 90, step: 5 },
  { name: "高位划船", group: "背", minWt: 20, maxWt: 90, step: 5 },
  { name: "坐姿划船", group: "背", minWt: 20, maxWt: 60, step: 2 },
  { name: "俯身划船", group: "背", minWt: 20, maxWt: 60, step: 5 },
  { name: "杠铃深蹲", group: "腿", minWt: 50, maxWt: 80, step: 5 },
  { name: "倒蹬", group: "腿", minWt: 30, maxWt: 60, step: 5 },
  { name: "髋外展", group: "腿", minWt: 30, maxWt: 60, step: 2 },
  { name: "髋内收", group: "腿", minWt: 30, maxWt: 60, step: 2 },
  { name: "髋伸展", group: "腿", minWt: 30, maxWt: 60, step: 2 },
  { name: "罗马椅", group: "核心", minWt: 10, maxWt: 30, step: 5 },
  { name: "腿弯曲", group: "腿", minWt: 10, maxWt: 60, step: 5 },
  { name: "轨道划船", group: "背", minWt: 30, maxWt: 60, step: 2 },
  { name: "牧师椅", group: "臂", minWt: 30, maxWt: 60, step: 2 }
]

var exercises = DEFAULT_EXERCISES

function loadExercises(allData) {
  if (allData && allData.exercises && allData.exercises.length > 0) {
    exercises = allData.exercises
  }
}

function getWeights(exerciseName) {
  var ex = null
  for (var i = 0; i < exercises.length; i++) {
    if (exercises[i].name === exerciseName) { ex = exercises[i]; break }
  }
  if (!ex) return []
  var weights = []
  for (var w = ex.minWt; w <= ex.maxWt; w += ex.step) { weights.push(w) }
  return weights
}

function getExerciseNames() {
  var names = []
  for (var i = 0; i < exercises.length; i++) { names.push(exercises[i].name) }
  return names
}

function getExercises() {
  return exercises
}

function getStep(exerciseName) {
  for (var i = 0; i < exercises.length; i++) {
    if (exercises[i].name === exerciseName) return exercises[i].step || 5
  }
  return 5
}

// 一级分类：按 GROUP_ORDER 顺序，只返回当前动作库里真实存在的部位
function getGroups() {
  var present = {}
  for (var i = 0; i < exercises.length; i++) {
    if (exercises[i].group) present[exercises[i].group] = true
  }
  var out = []
  for (var j = 0; j < GROUP_ORDER.length; j++) {
    if (present[GROUP_ORDER[j]]) out.push(GROUP_ORDER[j])
  }
  return out
}

// 二级：某部位下的动作名列表
function getExercisesByGroup(group) {
  var names = []
  for (var i = 0; i < exercises.length; i++) {
    if (exercises[i].group === group) names.push(exercises[i].name)
  }
  return names
}

function getGroupColor(group) {
  return GROUP_COLORS[group] || GROUP_COLORS['其他']
}

export default {
  loadExercises: loadExercises,
  getWeights: getWeights,
  getExerciseNames: getExerciseNames,
  getExercises: getExercises,
  getStep: getStep,
  getGroups: getGroups,
  getExercisesByGroup: getExercisesByGroup,
  getGroupColor: getGroupColor
}
