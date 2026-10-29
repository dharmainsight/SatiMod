const {JSDOM}=require(process.env.FLOWVIEW_JSDOM||'jsdom');
const fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict'),test=require('node:test');
const dir=path.resolve(__dirname,'../app/src/main/assets/controller');
function fixture(){
 const dom=new JSDOM('<header id="site-header">YouTube</header><main style="transform:translateY(20px);overflow:hidden"><div class="html5-video-player" style="position:relative"><video></video><button>Pause</button></div></main>',{url:'https://m.youtube.com/watch?v=test',runScripts:'outside-only',pretendToBeVisual:true});
 const w=dom.window,v=w.document.querySelector('video'),player=v.parentElement;let command,disconnected,fsElement=null,visible=true,plays=0,revision=0,clock=100,completeExit=null;const messages=[];
 w.performance.now=()=>clock;
 const style=w.document.createElement('style');style.textContent=fs.readFileSync(path.join(dir,'content.css'),'utf8');w.document.head.append(style);
 Object.defineProperty(v,'paused',{value:false,writable:true});Object.defineProperty(v,'duration',{value:200});v.currentTime=53;
 v.getBoundingClientRect=()=>visible?({width:412,height:240,left:0,right:412,top:0,bottom:240}):({width:0,height:0,left:0,right:0,top:0,bottom:0});
 v.play=()=>{plays++;v.paused=false;v.dispatchEvent(new w.Event('play'));return Promise.resolve();};v.pause=()=>{v.paused=true;v.dispatchEvent(new w.Event('pause'));};
 Object.defineProperty(w.document,'fullscreenElement',{get:()=>fsElement});
 w.document.exitFullscreen=()=>{fsElement=null;w.document.dispatchEvent(new w.Event('fullscreenchange'));return Promise.resolve();};
 w.browser={runtime:{connectNative(){return {postMessage(m){messages.push(m);},onMessage:{addListener(f){command=f;}},onDisconnect:{addListener(f){disconnected=f;}},disconnect(){disconnected?.();}};}}};
 for(const f of ['background-play.js','gestures.js','viewport-playback.js','links.js','content.js'])w.eval(fs.readFileSync(path.join(dir,f),'utf8'));
 command({command:'presentation',mode:'inline',revision});
 return {w,v,player,messages,send:m=>command(m.command==='presentation'?{revision:++revision,...m}:m),advance:n=>clock+=n,delayExit(){w.document.exitFullscreen=()=>new Promise(resolve=>{completeExit=()=>{fsElement=null;w.document.dispatchEvent(new w.Event('fullscreenchange'));resolve();};});},finishExit:()=>completeExit(),hide:()=>visible=false,nativeFull:()=>fsElement=player,plays:()=>plays,close(){w.dispatchEvent(new w.PageTransitionEvent('pagehide'));dom.window.close();}};
}
test('native fullscreen to PiP to fullscreen retains the video, position and isolated display',async()=>{
 const f=fixture();try{
  f.nativeFull();f.send({command:'presentation',mode:'pip'});await Promise.resolve();
  assert.equal(f.w.document.fullscreenElement,null);assert.equal(f.player.hasAttribute('data-flowview-full'),true);
  assert.equal(f.w.getComputedStyle(f.w.document.querySelector('#site-header')).visibility,'hidden');
  assert.equal(f.w.getComputedStyle(f.player).visibility,'visible');
  assert.equal(f.messages.filter(m=>m.type==='presentationApplied').at(-1).ok,true);
  f.send({command:'presentation',mode:'pip'});f.send({command:'presentation',mode:'full'});f.send({command:'presentation',mode:'full'});
  assert.equal(f.w.document.documentElement.dataset.flowviewPresentation,'full');assert.equal(f.v.currentTime,53);assert.equal(f.w.document.querySelector('video'),f.v);assert.equal(f.plays(),0);
  f.send({command:'presentation',mode:'inline'});assert.equal(f.player.getAttribute('style'),'position:relative');assert.equal(f.w.document.querySelectorAll('[data-flowview-ancestor]').length,0);assert.equal(f.w.getComputedStyle(f.w.document.querySelector('#site-header')).visibility,'visible');
 }finally{f.close();}
});
test('play and pause reach the existing video even while its surface rectangle is zero',async()=>{
 const f=fixture();try{
  f.send({command:'presentation',mode:'pip'});f.hide();f.v.pause();f.send({command:'play'});await new Promise(setImmediate);
  assert.equal(f.plays(),1);assert.equal(f.v.paused,false);assert.equal(f.v.currentTime,53);assert.equal(f.messages.filter(m=>m.type==='playResult').at(-1).ok,true);
  f.send({command:'pause'});assert.equal(f.v.paused,true);assert.equal(f.plays(),1);
 }finally{f.close();}
});
test('blocked playback is reported instead of claiming that playback resumed',async()=>{
 const f=fixture();try{
  f.v.pause();f.v.play=()=>Promise.reject(new f.w.DOMException('blocked','NotAllowedError'));
  f.send({command:'play'});await new Promise(setImmediate);
  const result=f.messages.filter(m=>m.type==='playResult').at(-1);assert.equal(result.ok,false);assert.equal(result.error,'NotAllowedError');assert.equal(f.v.paused,true);
 }finally{f.close();}
});
test('missing video produces an explicit transport failure',()=>{
 const f=fixture();try{f.v.remove();f.send({command:'play'});assert.equal(f.messages.filter(m=>m.type==='playResult').at(-1).error,'NoVideo');assert.equal(f.plays(),0);}finally{f.close();}
});

function touch(f,type,x,y){
 const e=new f.w.Event(type,{bubbles:true,cancelable:true}),t={identifier:1,clientX:x,clientY:y};
 Object.defineProperties(e,{touches:{value:type==='touchend'?[]:[t]},changedTouches:{value:[t]}});f.v.dispatchEvent(e);
}
test('delayed native fullscreen exit cannot send a new fullscreen request or undo portrait',async()=>{
 const f=fixture();try{
  f.nativeFull();f.delayExit();f.send({command:'presentation',mode:'full',revision:10});
  f.send({command:'presentation',mode:'inline',revision:11});
  assert.notEqual(f.w.document.fullscreenElement,null); // engine has not finished yet
  f.send({command:'state'});f.send({command:'settings',gestures:true});
  f.finishExit();await new Promise(setImmediate);
  assert.equal(f.messages.filter(m=>m.type==='presentationRequest').length,0);
  assert.equal(f.w.document.documentElement.hasAttribute('data-flowview-presentation'),false);
  assert.equal(f.messages.filter(m=>m.type==='presentationApplied').at(-1).mode,'inline');
 }finally{f.close();}
});
test('older queued fullscreen command is ignored after a newer portrait command',()=>{
 const f=fixture();try{
  f.send({command:'presentation',mode:'full',revision:50});f.send({command:'presentation',mode:'inline',revision:51});
  f.send({command:'presentation',mode:'full',revision:50});
  assert.equal(f.player.hasAttribute('data-flowview-full'),false);
  assert.equal(f.messages.filter(m=>m.type==='presentationApplied').at(-1).revision,51);
  assert.equal(f.messages.filter(m=>m.type==='presentationRequest').length,0);
 }finally{f.close();}
});
test('player reparenting during rotation refreshes visibility without replacing the video',async()=>{
 const f=fixture();try{
  f.send({command:'presentation',mode:'full'});const old=f.player.parentElement;
  const replacement=f.w.document.createElement('section');f.w.document.body.append(replacement);replacement.append(f.player);
  await new Promise(r=>setTimeout(r,45));
  assert.equal(replacement.hasAttribute('data-flowview-ancestor'),true);assert.equal(old.hasAttribute('data-flowview-ancestor'),false);
  assert.equal(f.w.getComputedStyle(replacement).visibility,'visible');assert.equal(f.w.getComputedStyle(f.v).visibility,'visible');
  assert.equal(f.w.document.querySelector('video'),f.v);assert.equal(f.v.currentTime,53);
  assert.equal(f.messages.filter(m=>m.type==='presentationRequest').length,0);
 }finally{f.close();}
});
test('portrait keeps current site styles rather than restoring an obsolete snapshot',()=>{
 const f=fixture();try{
  f.send({command:'presentation',mode:'full'});f.player.style.cssText='position:relative;width:684px;height:385px';
  const updated=f.player.getAttribute('style');f.send({command:'presentation',mode:'inline'});
  assert.equal(f.player.getAttribute('style'),updated);assert.equal(f.v.currentTime,53);
 }finally{f.close();}
});
test('screenshot focus interruption or rotation cancels a partial swipe',()=>{
 const f=fixture();try{
  for(const event of ['blur','resize','orientationchange']){
   touch(f,'touchstart',60,220);f.advance(100);f.w.dispatchEvent(new f.w.Event(event));f.advance(100);touch(f,'touchend',60,70);
  }
  assert.equal(f.messages.filter(m=>m.type==='presentationRequest').length,0);
  touch(f,'touchstart',60,220);f.advance(200);touch(f,'touchend',60,70);
  const requests=f.messages.filter(m=>m.type==='presentationRequest');assert.equal(requests.length,1);assert.equal(requests[0].mode,'full');assert.equal(requests[0].revision,0);
  assert.equal(f.player.hasAttribute('data-flowview-full'),false); // waits for native authority
 }finally{f.close();}
});
test('repeated layout mutations and duplicate acknowledgements produce no feedback requests',async()=>{
 const f=fixture();try{
  f.send({command:'presentation',mode:'full',revision:5});
  for(let i=0;i<20;i++){const el=f.w.document.createElement('span');f.player.append(el);el.remove();}
  await new Promise(r=>setTimeout(r,45));
  for(let i=0;i<20;i++)f.send({command:'presentation',mode:'full',revision:5});
  assert.equal(f.messages.filter(m=>m.type==='presentationRequest').length,0);
  assert.equal(f.messages.filter(m=>m.type==='presentationApplied').length,22);
  assert.equal(f.v.currentTime,53);
 }finally{f.close();}
});
test('presentation without a video reports failure and leaves the page visible',()=>{
 const f=fixture();try{
  f.v.remove();f.send({command:'presentation',mode:'full'});
  const ack=f.messages.filter(m=>m.type==='presentationApplied').at(-1);assert.equal(ack.ok,false);assert.equal(ack.error,'NoVideo');
  assert.equal(f.w.document.documentElement.hasAttribute('data-flowview-presentation'),false);
 }finally{f.close();}
});
test('native screenshot interruption cancels a pending gesture without a mode change',()=>{
 const f=fixture();try{
  touch(f,'touchstart',60,220);f.advance(100);f.send({command:'cancelGesture'});f.advance(100);touch(f,'touchend',60,70);
  assert.equal(f.messages.filter(m=>m.type==='presentationRequest').length,0);
  assert.equal(f.player.hasAttribute('data-flowview-full'),false);
 }finally{f.close();}
});
test('native drag previews smoothly without requesting portrait and resets after cancellation',()=>{
 const f=fixture();try{
  f.send({command:'settings',gestures:true,nativeFullscreenSwipe:true});f.send({command:'presentation',mode:'full',revision:20});
  const video=f.v;f.send({command:'fullscreenDrag',revision:20,progress:.5});
  assert.equal(f.player.hasAttribute('data-flowview-drag'),true);
  assert.equal(f.w.document.documentElement.style.getPropertyValue('--flowview-drag-scale'),'0.92');
  assert.equal(f.messages.filter(m=>m.type==='presentationRequest').length,0);
  f.send({command:'fullscreenDrag',revision:20,progress:1});
  assert.equal(f.w.document.documentElement.dataset.flowviewPresentation,'full');assert.equal(f.v,video);assert.equal(f.v.currentTime,53);
  f.send({command:'cancelGesture'});assert.equal(f.player.hasAttribute('data-flowview-drag'),false);
  assert.equal(f.player.getAttribute('style'),'position:relative');
  f.send({command:'fullscreenDrag',revision:19,progress:1});assert.equal(f.player.hasAttribute('data-flowview-drag'),false);
 }finally{f.close();}
});
test('native owns downward swipes including the notification edge while double taps still seek',()=>{
 const f=fixture();try{
  f.send({command:'settings',gestures:true,nativeFullscreenSwipe:true});f.send({command:'presentation',mode:'full'});
  for(const y of [10,80]){touch(f,'touchstart',60,y);f.advance(200);touch(f,'touchmove',60,220);touch(f,'touchend',60,220);}
  assert.equal(f.messages.filter(m=>m.type==='presentationRequest').length,0);
  for(let i=0;i<2;i++){touch(f,'touchstart',60,100);f.advance(80);touch(f,'touchend',60,100);f.advance(80);}
  assert.equal(f.v.currentTime,43);
 }finally{f.close();}
});
test('short viewport fits the same watch player and removes compact styles on expansion or fullscreen',()=>{
 const f=fixture();try{
  Object.defineProperty(f.w,'innerHeight',{value:180,writable:true});f.w.dispatchEvent(new f.w.Event('resize'));
  assert.equal(f.player.hasAttribute('data-flowview-compact-player'),true);
  assert.equal(f.w.document.documentElement.style.getPropertyValue('--flowview-compact-height'),'172px');
  assert.equal(f.v.currentTime,53);assert.equal(f.plays(),0);
  f.send({command:'presentation',mode:'full'});assert.equal(f.player.hasAttribute('data-flowview-compact-player'),false);
  f.send({command:'presentation',mode:'inline'});assert.equal(f.player.hasAttribute('data-flowview-compact-player'),true);
  f.w.innerHeight=700;f.w.dispatchEvent(new f.w.Event('resize'));assert.equal(f.player.hasAttribute('data-flowview-compact-player'),false);
  assert.equal(f.player.getAttribute('style'),'position:relative');
 }finally{f.close();}
});
test('resize recovery is connected to page resize and native pause cancels it',async()=>{
 const f=fixture();try{
  f.w.dispatchEvent(new f.w.Event('resize'));f.v.pause();await new Promise(r=>setTimeout(r,160));
  assert.equal(f.plays(),1);assert.equal(f.v.paused,false);
  f.w.dispatchEvent(new f.w.Event('resize'));f.send({command:'pause'});await new Promise(r=>setTimeout(r,500));
  assert.equal(f.v.paused,true);assert.equal(f.plays(),1);
 }finally{f.close();}
});
