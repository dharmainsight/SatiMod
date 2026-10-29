(() => {
 'use strict';if(window.top!==window)return;
 const records=new WeakMap(),workletSource=window.FlowViewWorkletSource;
 let ctx,preparing,backend='',record=null,enabled=false,busy=false,observing=false,pitch=0,tempo=1,generation=0,error='',detail='',live=true;
 const knownErrors=new Set(['NoVideo','ProtectedMedia','CrossOriginMedia','AudioAlreadyConnected','ResumeTimeout','VideoChanged','AudioUnavailable']);
 function report(){if(live)window.postMessage({channel:'flowview-music-state',state:{enabled,busy,pitch,tempo,error,detail,backend}},location.origin);}
 function video(){return [...document.querySelectorAll('video')].filter(v=>v.isConnected).sort((a,b)=>Number(a.paused)-Number(b.paused)||b.clientWidth*b.clientHeight-a.clientWidth*a.clientHeight)[0]||null;}
 function deadline(promise,ms,code){return new Promise((resolve,reject)=>{const id=setTimeout(()=>reject(new Error(code)),ms);Promise.resolve(promise).then(v=>{clearTimeout(id);resolve(v);},e=>{clearTimeout(id);reject(e);});});}
 function dry(r,restore=false){
  if(!r)return;
  if(r.source){r.source.disconnect();r.node?.disconnect();r.source.connect(ctx.destination);}
  r.wet=false;r.node?.flowReset?.();
  r.video.preservesPitch=restore?r.savedPitch:true;
  const rate=restore?r.savedRate:tempo;if(r.video.playbackRate!==rate)r.video.playbackRate=rate;
 }
 function select(v){
  if(!v)throw new Error('NoVideo');if(record?.video===v)return record;
  if(record)dry(record,true);
  let r=records.get(v);if(!r){r={video:v,source:null,node:null,wet:false};records.set(v,r);}
  r.savedRate=v.playbackRate;r.savedPitch=v.preservesPitch;record=r;dry(r);return r;
 }
 function route(){
  if(!record||!enabled)return;const r=record,wet=pitch!==0;
  if(wet&&(!r.source||!r.node))throw new Error('AudioUnavailable');
  if(wet!==r.wet){
   if(r.source){r.source.disconnect();r.node?.disconnect();if(wet){r.node.flowReset?.();r.source.connect(r.node);r.node.connect(ctx.destination);}else r.source.connect(ctx.destination);}
   r.wet=wet;
  }
  if(r.node){r.node.parameters.get('pitchSemitones').value=pitch;r.node.parameters.get('playbackRate').value=tempo;}
  r.video.preservesPitch=!wet;if(r.video.playbackRate!==tempo)r.video.playbackRate=tempo;
 }
 async function prepare(){
  if(!ctx){if(typeof AudioContext!=='function')throw new Error('AudioUnavailable');ctx=new AudioContext({latencyHint:'playback'});}
  await deadline(ctx.resume(),3000,'ResumeTimeout');if(ctx.state!=='running')throw new Error('ResumeTimeout');
  if(!preparing)preparing=(async()=>{
   if(ctx.audioWorklet&&typeof AudioWorkletNode==='function'){
    let url;
    try{url=URL.createObjectURL(new Blob([workletSource],{type:'text/javascript'}));await deadline(ctx.audioWorklet.addModule(url),2500,'ModuleTimeout');backend='worklet';return;}
    catch(e){/* Fall back to the packaged static adapter. */}
    finally{if(url)URL.revokeObjectURL(url);}
   }
   if(typeof window.FlowViewCreateCompatNode!=='function'||typeof ctx.createScriptProcessor!=='function')throw new Error('AudioUnavailable');
   backend='compat';
  })().catch(e=>{preparing=null;throw e;});
  await preparing;
 }
 function makeNode(){
  if(backend==='worklet'){
   try{return new AudioWorkletNode(ctx,'soundtouch-processor',{numberOfInputs:1,numberOfOutputs:1,outputChannelCount:[2],channelCount:2,channelCountMode:'explicit'});}
   catch(e){backend='compat';}
  }
  return window.FlowViewCreateCompatNode(ctx);
 }
 function connect(r){
  if(r.source)return;
  const v=r.video;if(v.readyState<1)throw new Error('NoVideo');if(v.mediaKeys)throw new Error('ProtectedMedia');
  const src=v.currentSrc||v.src;if(src&&!src.startsWith('blob:')&&!v.crossOrigin&&new URL(src,location.href).origin!==location.origin)throw new Error('CrossOriginMedia');
  const node=makeNode();
  try{r.source=ctx.createMediaElementSource(v);}catch(e){node.disconnect();node.onaudioprocess=null;throw new Error(e?.name==='InvalidStateError'?'AudioAlreadyConnected':'AudioUnavailable');}
  r.node=node;r.source.connect(ctx.destination);
  node.onprocessorerror=()=>{if(enabled&&record===r){generation++;busy=false;pitch=0;dry(r);error='AudioUnavailable';detail='P4';report();}};
 }
 async function changePitch(value){
  if(!enabled||!Number.isInteger(value)||value < -12||value > 12)return;
  const ticket=++generation;error='';detail='';
  if(value===0){busy=false;pitch=0;route();report();return;}
  busy=true;report();let stage='P1';
  try{
   const r=select(video());stage='P2';await prepare();if(ticket!==generation||!enabled)return;
   if(video()!==r.video)throw new Error('VideoChanged');
   stage='P3';connect(r);pitch=value;route();
  }catch(e){if(ticket!==generation)return;pitch=0;dry(record);error=knownErrors.has(e?.message)?e.message:'AudioUnavailable';detail=stage;}
  finally{if(ticket===generation){busy=false;report();}}
 }
 function disable(){generation++;enabled=false;busy=false;dry(record,true);pitch=0;tempo=1;error='';detail='';}
 function activate(){
  generation++;error='';detail='';
  try{const v=video(),rate=v?.playbackRate,preserve=v?.preservesPitch;const r=select(v);r.savedRate=rate;r.savedPitch=preserve;enabled=true;pitch=0;route();}catch(e){enabled=false;error='NoVideo';}report();
 }
 function tick(){
  if(enabled){const v=video();if(v&&record?.video!==v){const wanted=pitch;generation++;busy=false;pitch=0;select(v);route();if(wanted)changePitch(wanted);}}
  if(observing)report();
 }
 window.addEventListener('message',e=>{
  if(e.source!==window||e.origin!==location.origin||e.data?.channel!=='flowview-music-command')return;
  const m=e.data.command;if(!m||typeof m!=='object')return;
  switch(m.action){
   case 'query':observing=true;report();break;
   case 'observe':observing=m.value===true;break;
   case 'enable':if(m.value===true){if(!enabled)activate();}else{disable();report();}break;
   case 'pitch':changePitch(m.value);break;
   case 'tempo':if(enabled&&Number.isFinite(m.value)&&m.value>=.5&&m.value<=2){tempo=m.value;route();report();}break;
   case 'reset':generation++;busy=false;pitch=0;tempo=1;error='';detail='';if(enabled)route();report();break;
  }
 });
 document.addEventListener('ratechange',e=>{if(enabled&&record?.video===e.target){tempo=Math.max(.5,Math.min(2,e.target.playbackRate));route();if(observing)report();}},true);
 document.addEventListener('play',e=>{if(record?.video===e.target&&ctx?.state!=='running')ctx?.resume().catch(()=>{});},true);
 document.addEventListener('seeking',e=>{if(record?.video===e.target)record.node?.flowReset?.();},true);
 let timer=setInterval(tick,300);
 window.addEventListener('pagehide',()=>{live=false;generation++;busy=false;clearInterval(timer);});
 window.addEventListener('pageshow',()=>{if(!live){live=true;timer=setInterval(tick,300);ctx?.resume().catch(()=>{});}});
})();
