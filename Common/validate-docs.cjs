const fs=require('node:fs'),path=require('node:path');
const root=path.resolve(process.argv[2]||path.join(__dirname,'..'));
const ignored=new Set(['target','artifacts','dist','node_modules','.git','__pycache__','Source']);
const walk=d=>fs.readdirSync(d,{withFileTypes:true}).flatMap(e=>e.isDirectory()?(ignored.has(e.name)?[]:walk(path.join(d,e.name))):[path.join(d,e.name)]);
const strip=s=>s.replace(/^```[^\n]*\n[\s\S]*?^```\s*$/gm,'');
const anchorCache=new Map();
function anchors(file){
 if(anchorCache.has(file))return anchorCache.get(file);
 const content=strip(fs.readFileSync(file,'utf8')),ids=new Set(),counts=new Map();
 for(const m of content.matchAll(/\b(?:id|name)=["']([^"']+)["']/g))ids.add(m[1]);
 for(const m of content.matchAll(/^#{1,6}\s+(.+?)\s*#*$/gm)){
  let h=m[1].replace(/\[([^\]]+)\]\([^)]+\)/g,'$1').replace(/<[^>]*>/g,'').replace(/&amp;/g,'&').toLowerCase().replace(/[^\p{L}\p{M}\p{N}_\-\s]/gu,'').replace(/\s/g,'-');
  const n=counts.get(h)||0;counts.set(h,n+1);if(n)h+='-'+n;ids.add(h);
 }anchorCache.set(file,ids);return ids;
}
let count=0;const errors=[];
const files=walk(root).filter(f=>f.endsWith('.md')&&!f.includes(path.sep+'Source'+path.sep));
for(const f of files){const content=strip(fs.readFileSync(f,'utf8'));
 const urls=[...content.matchAll(/!?\[[^\]\n]*\]\((<[^>]+>|[^)\n]+)\)/g)].map(m=>m[1].replace(/^<|>$/g,''));
 urls.push(...[...content.matchAll(/<(?:img|a)\b[^>]*\b(?:src|href)=["']([^"']+)["']/gi)].map(m=>m[1]));
 for(let u of urls){u=u.trim().replace(/\s+["'][^"']*["']$/,'');if(/^(https?|mailto|data):/i.test(u))continue;
  if(/^[a-z]:|^file:|^\/\//i.test(u)){errors.push([path.relative(root,f),u,'non-portable']);continue;}
  let [name,frag]=u.split('#');let dest=path.resolve(path.dirname(f),decodeURIComponent(name||''));if(!name)dest=f;
  if(!dest.startsWith(root+path.sep)&&dest!==root){errors.push([path.relative(root,f),u,'outside distribution']);continue;}
  if(!fs.existsSync(dest)){errors.push([path.relative(root,f),u,'missing file']);continue;}
  if(frag&&dest.endsWith('.md')&&!anchors(dest).has(decodeURIComponent(frag)))errors.push([path.relative(root,f),u,'missing anchor']);
  count++;
 }
}
console.log(JSON.stringify({root,markdownFiles:files.length,localLinksChecked:count,errors},null,2));
if(errors.length)process.exitCode=1;
