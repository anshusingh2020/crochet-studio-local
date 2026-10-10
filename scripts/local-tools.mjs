import {spawnSync} from 'node:child_process';
import {existsSync,mkdirSync,readdirSync,writeFileSync} from 'node:fs';
import {resolve,join} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));process.chdir(root);
const [command,...args]=process.argv.slice(2);
function runWrangler(extra){const r=spawnSync(process.execPath,['--import','./scripts/sites-env.mjs','./node_modules/wrangler/bin/wrangler.js','d1',...extra,'--local','--config','wrangler.local.json','--persist-to','.wrangler/state'],{stdio:'inherit',env:{...process.env,CI:'1'}});if(r.error)throw r.error;if(r.status!==0)process.exit(r.status||1);}
function findDatabase(){const base=join(root,'.wrangler/state/v3/d1');const files=[];function walk(dir){if(!existsSync(dir))return;for(const e of readdirSync(dir,{withFileTypes:true})){const path=join(dir,e.name);if(e.isDirectory())walk(path);else if(e.name.endsWith('.sqlite')&&e.name!=='metadata.sqlite')files.push(path);}}walk(base);if(!files.length)throw Error('Run pnpm local:setup first.');if(files.length!==1)throw Error('Multiple SQLite databases found. Inspect .wrangler/state/v3/d1 and select the DB binding.');return files[0];}
if(command==='setup'||command==='migrate'){
 if(!existsSync('node_modules/wrangler/bin/wrangler.js'))throw Error('Install dependencies first: pnpm install --frozen-lockfile');
 if(command==='setup'){
  mkdirSync('.sites-runtime',{recursive:true});
  const profile='.sites-runtime/execution-profile.json';
  if(!existsSync(profile))writeFileSync(profile,JSON.stringify({executionProfile:'portable'},null,2)+'\n');
 }
 runWrangler(['migrations','apply','DB']);
 console.log('\nLocal SQLite database ready. Images will be stored under .wrangler/state/v3/r2.');
 console.log('Run pnpm dev, then open http://127.0.0.1:5173/__local for test accounts.');
}else if(command==='query'){
 const sql=args.join(' ').trim();if(!sql)throw Error('Usage: pnpm db:query "SELECT name FROM sqlite_master WHERE type=\'table\'"');
 runWrangler(['execute','DB','--command',sql]);
}else if(command==='path'){
 console.log(findDatabase());
}else if(command==='snapshot'){
 const {DatabaseSync}=await import('node:sqlite');mkdirSync('.local',{recursive:true});
 const target=resolve('.local','crochet-'+new Date().toISOString().replaceAll(':','-')+'.sqlite');
 const db=new DatabaseSync(findDatabase(),{readOnly:true});try{db.prepare('VACUUM INTO ?').run(target);}finally{db.close();}console.log(target);
}else throw Error('Expected setup, migrate, query, path or snapshot');
