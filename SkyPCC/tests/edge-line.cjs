const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname, '../src/main/resources/web/app.js'), 'utf8');
const start = source.indexOf('  function lineOfEdge(edge) {');
const end = source.indexOf('  function lineColor(line)', start);
assert(start >= 0 && end > start);
const state = { graph: {}, nodes: new Map([['a', {line:'A'}], ['b', {line:'B'}]]) };
const context = vm.createContext({state});
vm.runInContext(source.slice(start, end), context);
const edge = {id:'ab', from:'a', to:'b'};
const line = () => context.lineOfEdge(edge);
assert.equal(line(), '');
state.nodes.set('b', {line:'A'});
assert.equal(line(), 'A');
state.nodes.set('b', {line:''});
assert.equal(line(), '');
state.graph = {lineModelVersion:1, lineAssignments:{ab:{status:'CONFIRMED', line:'C'}}};
assert.equal(line(), 'C');
for (const status of ['UNASSIGNED', 'AMBIGUOUS', 'UNKNOWN']) {
  state.graph.lineAssignments.ab = {status, line:'A'};
  assert.equal(line(), '');
}
state.graph.lineAssignments = {};
assert.equal(line(), '');
delete state.graph.lineAssignments;
assert.equal(line(), '');
state.graph = {lineAssignments:{ab:{status:'CONFIRMED', line:null}}};
assert.equal(line(), '');
if (process.argv[2]) {
  state.graph = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
  state.nodes = new Map(state.graph.nodes.map(n => [n.id, n]));
  let checked = 0;
  for (const e of state.graph.edges) {
    const a = state.graph.lineAssignments[e.id];
    assert.equal(context.lineOfEdge(e), a?.status === 'CONFIRMED' ? a.line : '');
    checked++;
  }
  console.log(`PASS ${checked} actual graph edges use authoritative line assignments`);
}
console.log('PASS PCC edge line: confirmed, unassigned, ambiguous, missing, legacy and endpoint conflicts');
