import http from 'node:http';
import {readFileSync,writeFileSync,renameSync} from 'node:fs';
import {isIPv4} from 'node:net';
import {createAndroidRemote,RemoteKeyCode} from '@kud/androidtv-remote';
import {Bonjour} from 'bonjour-service';
const host='127.0.0.1',port=8765,origin='http://'+host+':'+port;
const keyFile=new URL('./.remote-keys.json',import.meta.url),tmpFile=new URL('./.remote-keys.json.tmp',import.meta.url);
const html=readFileSync(new URL('./index.html',import.meta.url));
let certs={};try{certs=JSON.parse(readFileSync(keyFile,'utf8'));}catch(e){if(e.code!=='ENOENT')console.error('Could not read pairings:',e.message);}
let remote=null,ip='',status='Disconnected',ready=false,pairing=false;
const devices=new Map();let bonjour,browser;
function privateIP(s){if(typeof s!=='string'||!isIPv4(s))return false;const [a,b]=s.split('.').map(Number);return a===10||(a===192&&b===168)||(a===172&&b>=16&&b<=31)||(a===169&&b===254);}
function discover(){browser?.stop();bonjour?.destroy();devices.clear();bonjour=new Bonjour({},e=>{console.error('Discovery:',e.message);});browser=bonjour.find({type:'androidtvremote2'},service=>{for(const address of service.addresses||[])if(privateIP(address))devices.set(address,{ip:address,name:service.name||address});});}
function stop(){const old=remote;remote=null;ready=false;pairing=false;try{old?.stop();}catch{}status='Disconnected';}
function save(){writeFileSync(tmpFile,JSON.stringify(certs),{mode:0o600});renameSync(tmpFile,keyFile);}
function connect(address,forget=false){
 if(!privateIP(address))throw Error('Enter a private IPv4 address from your home network.');
 stop();ip=address;if(forget){delete certs[ip];save();}
 const opts={service_name:'LAN Remote'};if(certs[ip])opts.cert=certs[ip];
 const r=createAndroidRemote(ip,opts);remote=r;status='Connecting…';
 const active=fn=>(...args)=>{if(remote===r)fn(...args);};
 r.on('secret',active(()=>{ready=false;pairing=true;status='Enter the PIN shown on your TV';}));
 r.on('ready',active(()=>{ready=true;pairing=false;status='Connected';try{certs[address]=r.getCertificate();save();}catch(e){status='Connected; could not save pairing';}}));
 r.on('unpaired',active(()=>{ready=false;pairing=false;status='Pairing rejected. Tap Re-pair.';}));
 r.on('error',active(e=>{ready=false;status='Connection error: '+(e?.message||e);}));
 Promise.resolve().then(()=>r.start()).then(active(ok=>{if(ok===false&&!ready&&!pairing)status='Connection failed. Check IP and remote service.';})).catch(active(e=>{ready=false;pairing=false;status='Connection failed: '+e.message;}));
}
const allowed=new Set(['DPAD_UP','DPAD_DOWN','DPAD_LEFT','DPAD_RIGHT','DPAD_CENTER','HOME','BACK','MENU','VOLUME_UP','VOLUME_DOWN','VOLUME_MUTE','MEDIA_PLAY_PAUSE','MEDIA_REWIND','MEDIA_FAST_FORWARD','CHANNEL_UP','CHANNEL_DOWN','POWER',...Array.from({length:10},(_,i)=>String(i))]);
function json(res,code,value){res.writeHead(code,{'Content-Type':'application/json','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});res.end(JSON.stringify(value));}
async function body(req){let s='';for await(const chunk of req){s+=chunk;if(s.length>4096)throw Error('Request too large');}return JSON.parse(s||'{}');}
const server=http.createServer(async(req,res)=>{
 if(req.headers.host!==host+':'+port)return json(res,403,{error:'Invalid host'});
 try{
  if(req.method==='GET'&&req.url==='/'){res.writeHead(200,{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Content-Security-Policy':"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'"});return res.end(html);}
  if(req.method==='GET'&&req.url==='/state')return json(res,200,{ip,status,ready,pairing,devices:[...devices.values()]});
  if(req.method!=='POST'||req.headers.origin!==origin||!req.headers['content-type']?.startsWith('application/json'))return json(res,403,{error:'Only same-origin JSON requests allowed'});
  const data=await body(req);
  switch(req.url){
   case '/discover':discover();break;
   case '/connect':connect(data.ip);break;
   case '/forget':connect(data.ip,true);break;
   case '/disconnect':stop();break;
   case '/pin':if(!remote||!pairing)throw Error('Start pairing first');if(typeof data.pin!=='string'||!/^[0-9A-F]{6}$/i.test(data.pin))throw Error('Enter the six-character TV code');if(!remote.sendCode(data.pin.toUpperCase()))throw Error('Code rejected; check the TV');status='Pairing…';break;
   case '/key':{if(!remote||!ready)throw Error('Connect first');if(!allowed.has(data.key))throw Error('Unknown key');const code=RemoteKeyCode['KEYCODE_'+data.key];if(typeof code!=='number')throw Error('Key not supported by library');remote.sendKey(code);break;}
   default:return json(res,404,{error:'Not found'});
  }
  return json(res,200,{ok:true});
 }catch(e){return json(res,400,{error:e.message});}
});
server.listen(port,host,()=>{console.log('Open '+origin+' in your browser. Ctrl+C to quit.');try{discover();}catch(e){console.error('Discovery unavailable:',e.message);}});
process.on('SIGINT',()=>{stop();browser?.stop();bonjour?.destroy();server.close(()=>process.exit(0));});
