import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {setTimeout as sleep} from 'node:timers/promises';
import {RemoteKeyCode} from '@kud/androidtv-remote';
test('remote library exposes common key codes',()=>{for(const k of ['HOME','BACK','DPAD_UP','DPAD_CENTER','VOLUME_UP'])assert.equal(typeof RemoteKeyCode['KEYCODE_'+k],'number');});
test('loopback server, origin checks, disconnected state',async()=>{const child=spawn(process.execPath,['server.mjs'],{stdio:'ignore'});try{let state;for(let i=0;i<40;i++){try{state=await fetch('http://127.0.0.1:8765/state').then(r=>r.json());break;}catch{await sleep(100);}}assert.equal(state?.ready,false);const url='http://127.0.0.1:8765/key';const opts={method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({key:'HOME'})};assert.equal((await fetch(url,opts)).status,403);opts.headers.Origin='http://127.0.0.1:8765';assert.equal((await fetch(url,opts)).status,400);assert.equal((await fetch('http://127.0.0.1:8765/')).status,200);}finally{child.kill();}});
