// Optional local/backend worker. Never run in a browser or publish credentials.
import {DatabaseSync} from 'node:sqlite';import {mkdirSync} from 'node:fs';import {dirname} from 'node:path';
const endpoint=process.env.KAFKA_REST_URL;
if(!endpoint)throw Error('Set KAFKA_REST_URL in your environment');
const base=new URL(endpoint);if(base.protocol!=='https:'&&!(base.protocol==='http:'&&['localhost','127.0.0.1'].includes(base.hostname)))throw Error('Use HTTPS for remote gateways');
const topic=process.env.KAFKA_TOPIC||'crochet.order-events';const group=process.env.KAFKA_CONSUMER_GROUP||'crochet.tracking';
const headers={'Content-Type':'application/vnd.kafka.v2+json','Accept':'application/vnd.kafka.v2+json'};
if(process.env.KAFKA_REST_USERNAME&&process.env.KAFKA_REST_PASSWORD)headers.Authorization='Basic '+Buffer.from(process.env.KAFKA_REST_USERNAME+':'+process.env.KAFKA_REST_PASSWORD).toString('base64');
const statePath=process.env.KAFKA_CONSUMER_STATE||'./.sites-runtime/kafka-consumer.sqlite';mkdirSync(dirname(statePath),{recursive:true});const state=new DatabaseSync(statePath);state.exec('CREATE TABLE IF NOT EXISTS consumed_events (id TEXT PRIMARY KEY, processed_at TEXT NOT NULL)');
async function request(url,options={}){const r=await fetch(url,{...options,headers:{...headers,...options.headers},signal:AbortSignal.timeout(20000),redirect:'error'});if(!r.ok)throw Error('Kafka gateway HTTP '+r.status);return r;}
const r=await request(base.href.replace(/\/$/,'')+'/consumers/'+encodeURIComponent(group),{method:'POST',body:JSON.stringify({name:'crochet-'+crypto.randomUUID(),format:'json','auto.offset.reset':'earliest','auto.commit.enable':false})});
const instance=new URL((await r.json()).base_uri);if(instance.origin!==base.origin)throw Error('Consumer URI must use the configured gateway origin');
await request(instance.href+'/subscription',{method:'POST',body:JSON.stringify({topics:[topic]})});
let active=true;process.on('SIGINT',()=>{active=false});process.on('SIGTERM',()=>{active=false});
try{do{const r=await request(instance.href+'/records?timeout=5000&max_bytes=100000',{headers:{Accept:'application/vnd.kafka.json.v2+json'}});const records=await r.json();for(const record of records){const event=record.value;if(!event||typeof event.eventId!=='string'||typeof event.orderId!=='string'||typeof event.type!=='string')throw Error('Unexpected event schema');if(state.prepare('SELECT id FROM consumed_events WHERE id=?').get(event.eventId))continue;
// Replace this handler with a durable notification task when a delivery channel is configured.
console.log(JSON.stringify({eventId:event.eventId,orderId:event.orderId,type:event.type,occurredAt:event.occurredAt}));
state.prepare('INSERT INTO consumed_events VALUES (?,?)').run(event.eventId,new Date().toISOString());}
await request(instance.href+'/offsets',{method:'POST',body:JSON.stringify({})});if(process.argv.includes('--once'))break;}while(active);}finally{await request(instance.href,{method:'DELETE'}).catch(()=>{});state.close();}
