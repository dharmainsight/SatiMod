(() => {
 'use strict';if(window.top!==window)return;
 const records=new WeakMap(),workletSource=window.FlowViewWorkletSource;
 const frequencies=[60,230,910,3600,14000];let eq=[0,0,0,0,0];
 let ctx,preparing,backend='',record=null,enabled=true,busy=false,observing=false,pitch=0,tempo=1,generation=0,error='',detail='',live=true;
 const knownErrors=new Set(['NoVideo','ProtectedMedia','CrossOriginMedia','AudioAlreadyConnected','ResumeTimeout','VideoChanged','AudioUnavailable']);
 function report(){if(live){const v=video(),rate=record?tempo:(v?.playbackRate||1);window.postMessage({channel:'flowview-music-state',state:{enabled,busy,pitch,tempo:rate,error:(!v&&enabled)?'NoVideo':error,detail,backend,eq:[...eq]}},location.origin);}}
 function video(){return [...document.querySelectorAll('video')].filter(v=>v.isConnected).sort((a,b)=>Number(a.paused)-Number(b.paused)||b.clientWidth*b.clientHeight-a.clientWidth*a.clientHeight)[0]||null;}
 function deadline(promise,ms,code){return new Promise((resolve,reject)=>{const id=setTimeout(()=>reject(new Error(code)),ms);Promise.resolve(promise).then(v=>{clearTimeout(id);resolve(v);},e=>{clearTimeout(id);reject(e);});});}
 function dry(r,restore=false){
  if(!r)return;
  if(r.source){r.source.disconnect();r.node?.disconnect();r.limiter?.disconnect();r.filters?.forEach(f=>f.disconnect());r.source.connect(ctx.destination);}
  r.wet=false;r.node?.flowReset?.();
  r.video.preservesPitch=restore?r.savedPitch:true;
  const rate=restore?r.savedRate:tempo;if(r.video.playbackRate!==rate)r.video.playbackRate=rate;
 }
 function select(v){
  if(!v)throw new Error('NoVideo');if(record?.video===v)return record;
  if(record)dry(record,true);
  else tempo=Number.isFinite(v.playbackRate)?Math.max(.5,Math.min(2,v.playbackRate)):1;
  let r=records.get(v);if(!r){r={video:v,source:null,node:null,wet:false};records.set(v,r);}
  r.savedRate=v.playbackRate;r.savedPitch=v.preservesPitch;record=r;dry(r);return r;
 }
 function route(){
  if(!record||!enabled)return;const r=record,wet=pitch!==0;
  if(wet&&(!r.source||!r.node))throw new Error('AudioUnavailable');
  if(r.source){
   r.source.disconnect();r.node?.disconnect();r.limiter?.disconnect();r.filters?.forEach(f=>f.disconnect());
   let tail=r.source;
   if(wet){if(!r.wet)r.node.flowReset?.();tail.connect(r.node);tail=r.node;}
   if(eq.some(g=>g!==0)){
    if(!r.filters){r.limiter=ctx.createDynamicsCompressor();r.limiter.threshold.value=-1;r.limiter.knee.value=0;r.limiter.ratio.value=20;r.limiter.attack.value=0;r.limiter.release.value=.1;r.filters=frequencies.map(hz=>{const f=ctx.createBiquadFilter();f.type='peaking';f.frequency.value=hz;f.Q.value=1;return f;});}
    r.filters.forEach((f,i)=>{f.gain.value=eq[i];tail.connect(f);tail=f;});
    // Control peaks after EQ; never subtract the slider gains from all bands.
    tail.connect(r.limiter);tail=r.limiter;
   }
   tail.connect(ctx.destination);
  }
  r.wet=wet;
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
  node.onprocessorerror=()=>{if(enabled&&record===r){generation++;busy=false;pitch=0;eq=[0,0,0,0,0];dry(r);error='AudioUnavailable';detail='P4';report();}};
 }
 async function changePitch(value,nextEq=eq){
  if(!enabled||!Number.isInteger(value)||value < -12||value > 12)return;
  const ticket=++generation;error='';detail='';
  if(value===0&&!nextEq.some(g=>g!==0)){busy=false;pitch=0;eq=[...nextEq];route();report();return;}
  const previousPitch=pitch,previousEq=eq;busy=true;report();let stage='P1';
  try{
   const r=select(video());stage='P2';await prepare();if(ticket!==generation||!enabled)return;
   if(video()!==r.video)throw new Error('VideoChanged');
   stage='P3';connect(r);pitch=value;eq=[...nextEq];route();
  }catch(e){if(ticket!==generation)return;pitch=previousPitch;eq=previousEq;route();error=knownErrors.has(e?.message)?e.message:'AudioUnavailable';detail=stage;}
  finally{if(ticket===generation){busy=false;report();}}
 }
 function disable(){generation++;enabled=false;busy=false;dry(record,true);pitch=0;eq=[0,0,0,0,0];tempo=1;error='';detail='';}
 function activate(){
  generation++;error='';detail='';
  try{const v=video(),rate=v?.playbackRate,preserve=v?.preservesPitch;const r=select(v);r.savedRate=rate;r.savedPitch=preserve;enabled=true;pitch=0;route();}catch(e){enabled=false;error='NoVideo';}report();
 }
 function tick(){
  if(enabled&&record){const v=video();if(v&&record?.video!==v){const wanted=pitch;generation++;busy=false;pitch=0;select(v);route();if(wanted||eq.some(g=>g!==0))changePitch(wanted);}}
  if(observing)report();
 }
 window.addEventListener('message',e=>{
  if(e.source!==window||e.origin!==location.origin||e.data?.channel!=='flowview-music-command')return;
  const m=e.data.command;if(!m||typeof m!=='object')return;
  switch(m.action){
   case 'query':observing=true;report();break;
   case 'observe':observing=m.value===true;break;
   case 'enable':if(m.value===true){if(!enabled)activate();else report();}else{disable();report();}break;
   case 'pitch':changePitch(m.value);break;
   case 'eq':if(Array.isArray(m.value)&&m.value.length===5&&m.value.every(g=>Number.isInteger(g)&&g>=-12&&g<=12))changePitch(pitch,m.value);break;
   case 'tempo':if(enabled&&Number.isFinite(m.value)&&m.value>=.5&&m.value<=2){try{select(video());tempo=m.value;error='';route();}catch(e){error='NoVideo';}report();}break;
   case 'reset':generation++;busy=false;pitch=0;error='';detail='';if(enabled){try{select(video());tempo=1;route();}catch(e){error='NoVideo';}}else tempo=1;report();break;
  }
 });
 document.addEventListener('ratechange',e=>{if(enabled&&record?.video===e.target){tempo=Math.max(.5,Math.min(2,e.target.playbackRate));route();if(observing)report();}},true);
 document.addEventListener('play',e=>{if(record?.video===e.target&&ctx?.state!=='running')ctx?.resume().catch(()=>{});},true);
 document.addEventListener('seeking',e=>{if(record?.video===e.target)record.node?.flowReset?.();},true);
 let timer=setInterval(tick,300);
 window.addEventListener('pagehide',()=>{live=false;generation++;busy=false;clearInterval(timer);});
 window.addEventListener('pageshow',()=>{if(!live){live=true;timer=setInterval(tick,300);ctx?.resume().catch(()=>{});}});
})();
