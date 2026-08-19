var DEFAULT_EXERCISES = [
  { name: '卧推',     minWt: 40, maxWt: 90, step: 5 },
  { name: '杠铃深蹲', minWt: 40, maxWt: 90, step: 5 },
  { name: '六角杠铃', minWt: 50, maxWt: 90, step: 5 },
  { name: '哑铃',     minWt: 10, maxWt: 50, step: 2 },
  { name: '高位下拉', minWt: 20, maxWt: 50, step: 2 },
  { name: '坐姿划船', minWt: 20, maxWt: 50, step: 2 },
  { name: '大剪刀',   minWt: 30, maxWt: 60, step: 5 },
  { name: '倒蹬',     minWt: 40, maxWt: 80, step: 10},
  { name: 'T杠划船',  minWt: 15, maxWt: 40, step: 5 }
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

export default {
  loadExercises: loadExercises,
  getWeights: getWeights,
  getExerciseNames: getExerciseNames,
  getExercises: getExercises,
  getStep: getStep
}
