import {test} from 'node:test';import assert from 'node:assert/strict';import {candidates,privateIP} from '../discovery.mjs';
test('local candidates remain in same /24 and exclude phone',()=>{const ips=candidates('192.168.12.30');assert.equal(ips.length,253);assert(!ips.includes('192.168.12.30'));assert(ips.every(ip=>ip.startsWith('192.168.12.')));});
test('reject non-local addresses and malformed inputs',()=>{for(const ip of ['8.8.8.8','127.0.0.1','192.168.0.300','']){assert.equal(privateIP(ip),false);assert.throws(()=>candidates(ip));}});
