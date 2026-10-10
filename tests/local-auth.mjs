import fs from 'node:fs';import vm from 'node:vm';import ts from 'typescript';import assert from 'node:assert/strict';import {createRequire} from 'node:module';
const req=createRequire(import.meta.url);const source=fs.readFileSync('build/sites-vite-plugin.ts','utf8');const code=ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;const module={exports:{}};vm.runInNewContext(code,{exports:module.exports,require:req,process,URL,Set,Object,Boolean,encodeURIComponent});let handler;
module.exports.sites({mockAuth:true}).configureServer({config:{server:{},logger:{info(){}}},middlewares:{use(fn){handler=fn}}});
function invoke(path,headers={},remote='127.0.0.1'){const request={url:path,method:'GET',headers:{host:'127.0.0.1:5173',...headers},rawHeaders:[],socket:{remoteAddress:remote}};const response={headers:{},setHeader(k,v){this.headers[k]=v},end(x){this.body=x}};let next=false;handler(request,response,()=>{next=true});return {request,response,next};}
assert.equal(invoke('/__local').response.statusCode,200);
assert.match(invoke('/signin-with-chatgpt?local_role=admin&return_to=/admin').response.headers['Set-Cookie'],/=admin;/);
assert.equal(invoke('/api/studio',{cookie:'__sites_local_auth=admin'}).request.headers['oai-authenticated-user-email'],'admin@crochet.local');
assert.equal(invoke('/api/studio',{cookie:'__sites_local_auth=customer'}).request.headers['oai-authenticated-user-id'],'local_customer');
assert.equal(invoke('/api/studio',{cookie:'__sites_local_auth=customer2'}).request.headers['oai-authenticated-user-id'],'local_customer2');
assert.equal(invoke('/api/studio',{'oai-authenticated-user-email':'admin@crochet.local'}).request.headers['oai-authenticated-user-email'],undefined);
assert.equal(invoke('/__local',{},'192.0.2.1').response.statusCode,403);
assert.equal(invoke('/signin-with-chatgpt?local_role=invalid').response.statusCode,400);
assert.equal(invoke('/signin-with-chatgpt',{origin:'https://untrusted.example'}).response.statusCode,403);
assert.equal(invoke('/signin-with-chatgpt?return_to=https://untrusted.example').response.headers.Location,'/');
console.log('Local admin/customer identities, loopback restriction, header stripping and redirect checks passed.');
