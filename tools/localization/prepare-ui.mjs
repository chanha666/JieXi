// Read-only migration planner. The caller applies the returned edits with apply_patch.
import fs from 'node:fs';
import path from 'node:path';
const root = process.cwd();
const han = /[\u3400-\u9fff]/;
function quoted(s, start) {
  if(s.startsWith('"""', start)) { const end=s.indexOf('"""',start+3); return {end:end+3, raw:true}; }
  let i=start+1, text='', args=[];
  while(i<s.length) {
    if(s[i]==='"') return {end:i+1,text,args};
    if(s[i]==='\\') { text+=s.slice(i,i+2); i+=2; continue; }
    if(s[i]==='$' && s[i+1]==='{') {
      const end=balanced(s,i+1,'{','}');
      text+='{'+args.length+'}'; args.push(s.slice(i+2,end-1)); i=end; continue;
    }
    if(s[i]==='$' && /[A-Za-z_]/.test(s[i+1]||'')) {
      const m=s.slice(i+1).match(/^\w+/)[0]; text+='{'+args.length+'}'; args.push(m); i+=m.length+1; continue;
    }
    text+=s[i++];
  }
  throw Error('Unterminated Kotlin string');
}
function balanced(s,start,open='(',close=')') {
  let depth=1,i=start+1;
  while(i<s.length) {
    if(s[i]==='"') { i=quoted(s,i).end; continue; }
    if(s.startsWith('//',i)) { i=s.indexOf('\n',i); if(i<0) return s.length; continue; }
    if(s.startsWith('/*',i)) { i=s.indexOf('*/',i+2)+2; continue; }
    if(s[i]===open)depth++;
    if(s[i]===close) { depth--; if(depth===0) return i+1; }
    i++;
  }
  throw Error('Unbalanced call');
}
// Separate implementation avoids treating braces in interpolation as call separators.
function argsOf(s,start,end) {
  let parts=[],a=start+1,i=a,stack=[];
  while(i<end-1) {
    if(s[i]==='"') { i=quoted(s,i).end; continue; }
    if('([{'.includes(s[i]))stack.push(s[i]);
    if(')]}'.includes(s[i]))stack.pop();
    if(s[i]===',' && !stack.length) { parts.push([a,i]); a=i+1; }
    i++;
  }
  parts.push([a,end-1]); return parts;
}
function walk(dir) { return fs.readdirSync(dir,{withFileTypes:true}).flatMap(e=>e.isDirectory()?walk(path.join(dir,e.name)):[path.join(dir,e.name)]); }
const files=[...walk('app/src/main/kotlin/com/yunx/app/ui'), 'app/src/main/kotlin/com/fuke/mobile/MediaActivity.kt', ...walk('desktopApp/src/main/kotlin/com/yunx/desktop').filter(p=>!p.includes(path.sep+'core'+path.sep)&&!p.includes(path.sep+'media'+path.sep)&&!p.includes(path.sep+'security'+path.sep)&&!p.includes(path.sep+'system'+path.sep)&&!p.includes(path.sep+'browser'+path.sep)&&!p.includes(path.sep+'update'+path.sep)&&!p.includes(path.sep+'settings'+path.sep)&&!p.includes(path.sep+'download'+path.sep))].filter(p=>p.endsWith('.kt')&&!p.includes('i18n'));
const helpers=new Set(['PageFrame','EmptyState','MineRow','MineSection','MineEntry','MediaToolCard','FeatureCard','StatusLine','SettingsSection','SectionHeader','SettingRow','SettingsRow','PreferenceRow','SectionTitle','InfoRow','SettingSwitch','SettingItem','SettingsCard','AccountCard','DetailRow','SettingsToggle','SectionLabel','RadioThreadRow','PrimaryButton','SecondaryButton']);
helpers.add('SettingsItem');
const entries=new Set(), changes=[];
for(const file of files) {
  const old=fs.readFileSync(file,'utf8'); let edits=[];
  for(let i=0;i<old.length;) {
    if(old.startsWith('//',i)) { i=old.indexOf('\n',i); if(i<0)break; continue; }
    if(old.startsWith('/*',i)) { i=old.indexOf('*/',i+2)+2; continue; }
    if(old[i]==='"') { i=quoted(old,i).end; continue; }
    const m=old.slice(i).match(new RegExp('^(Text|Icon|'+[...helpers].join('|')+')\\s*\\('));
    if(!m || /[\w.]/.test(old[i-1]||'')) { i++; continue; }
    const name=m[1], start=i+m[0].lastIndexOf('('),end=balanced(old,start);
    const args=argsOf(old,start,end);
    let ranges=[];
    if(name==='Text') ranges=[args.find(([a,b])=>/^\s*text\s*=/.test(old.slice(a,b)))||args[0]];
    else if(name==='Icon') ranges=args.filter(([a,b],j)=>/^\s*contentDescription\s*=/.test(old.slice(a,b)) || j===1);
    else if(helpers.has(name)) ranges=args.filter(([a,b])=>/^\s*(?:\w+\s*=\s*)?"/.test(old.slice(a,b)));
    for(const [a,b] of ranges) {
      for(let j=a;j<b;) {
        if(old[j]!=='"') { j++; continue; }
        const q=quoted(old,j);
        if(!q.raw && han.test(q.text) && !/tr\(\s*$/.test(old.slice(Math.max(a,j-8),j)) && !edits.some(e=>e.a===j)) {
          entries.add(q.text);
          edits.push({a:j,b:q.end,text:'tr("'+q.text+'"'+(q.args.length?', '+q.args.join(', '):'')+')'});
        }
        j=q.end;
      }
    }
    i=start+1;
  }
  if(!edits.length)continue;
  let next=old;
  for(const e of edits.sort((a,b)=>b.a-a.a))next=next.slice(0,e.a)+e.text+next.slice(e.b);
  const pkg=file.startsWith('app')?'com.yunx.app.ui.i18n.tr':'com.yunx.desktop.i18n.tr';
  next=next.replace(/^(package [^\r\n]+)(\r?\n)/,'$1$2\nimport '+pkg+'\n');
  changes.push({file:file.replaceAll('\\','/'),old,next});
}
if(process.argv[2]==='entries') console.log(JSON.stringify([...entries].sort()));
else if(process.argv[2]==='files') console.log(JSON.stringify(changes.map(x=>x.file)));
else if(process.argv[2]==='one') console.log(JSON.stringify(changes.find(x=>x.file===process.argv[3])));
else console.log(JSON.stringify({entries:[...entries].sort(),changes}));
