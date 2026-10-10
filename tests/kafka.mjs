import assert from 'node:assert/strict';import {kafkaRecord,producerAcknowledged} from '../lib/kafka-record.ts';
const r=kafkaRecord({id:'event-1',order_id:'order-1',kind:'order.shipped',created:'2026-10-09T00:00:00.000Z',payload:'{"status":"shipped"}'});
assert.equal(r.key,'order-1');assert.equal(r.value.eventId,'event-1');assert.equal(r.value.schemaVersion,1);assert.deepEqual(r.value.data,{status:'shipped'});
assert.equal(producerAcknowledged({offsets:[{offset:2,error_code:null}]}),true);
assert.equal(producerAcknowledged({offsets:[{offset:null,error_code:2}]}),false);
assert.equal(producerAcknowledged({offsets:[]}),false);assert.equal(producerAcknowledged(null),false);
console.log('Kafka event envelope and gateway acknowledgement tests passed.');
