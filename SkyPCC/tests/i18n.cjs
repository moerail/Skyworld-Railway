const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const root = path.join(__dirname, '../src/main/resources/web');
const storage = new Map();
const ctx = vm.createContext({window:{}, navigator:{language:'fr-FR'},
  document:{documentElement:{}, querySelectorAll:()=>[]},
  localStorage:{getItem:k=>storage.get(k), setItem:(k,v)=>storage.set(k,v)}});
vm.runInContext(fs.readFileSync(path.join(root,'i18n.js'),'utf8'),ctx);
const i = ctx.window.PccI18n;
assert.equal(i.language,'fr');
for (const [key, row] of Object.entries(i.catalog)) {
  const placeholders = s => [...s.matchAll(/\{(\w+)\}/g)].map(m=>m[1]).sort();
  for (const lang of i.languages) {
    assert(row[lang]?.length, `${key}: missing ${lang}`);
    assert.deepEqual(placeholders(row[lang]),placeholders(row.en),`${key}: ${lang} placeholders`);
  }
}
const app = fs.readFileSync(path.join(root,'app.js'),'utf8');
const html = fs.readFileSync(path.join(root,'index.html'),'utf8');
for (const m of app.matchAll(/\bt\('([^']+)'/g)) assert(i.catalog[m[1]],`Missing dynamic key ${m[1]}`);
for (const m of html.matchAll(/data-i18n(?:-title|-aria)?="([^"]+)"/g)) assert(i.catalog[m[1]],`Missing HTML key ${m[1]}`);
for (const lang of i.languages) {
  i.set(lang);
  assert.equal(i.language,lang);
  assert.equal(storage.get('skypcc.language'),lang);
  assert.equal(i.code('FUTURE_UNKNOWN_CODE'),'FUTURE_UNKNOWN_CODE');
  assert.equal(i.t('Train {name}',{name:'$& <A>'}),'Train $& <A>');
  assert(!i.t('{n} active',{n:3}).includes('{n}'));
  assert.notEqual(i.code('NO_EXIT_CAPACITY'),'NO_EXIT_CAPACITY');
}
i.set('de'); assert.equal(i.language,'en');
ctx.localStorage.getItem=()=>{throw new Error('denied');};
ctx.localStorage.setItem=()=>{throw new Error('denied');};
vm.runInContext(fs.readFileSync(path.join(root,'i18n.js'),'utf8'),ctx);
ctx.window.PccI18n.set('ja');
assert.equal(ctx.window.PccI18n.language,'ja');
console.log(`PASS ${Object.keys(i.catalog).length} four-language keys, placeholders, code fallback and storage failure`);
