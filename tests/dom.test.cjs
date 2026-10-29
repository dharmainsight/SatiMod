const {JSDOM}=require(process.env.FLOWVIEW_JSDOM || 'jsdom');
const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
const base=path.resolve(__dirname,'../app/src/main/assets/controller');
const dom=new JSDOM(`<div class="html5-video-player" style="position:relative"><video></video><button>Pause</button></div><ytm-engagement-panel><p>Comment</p></ytm-engagement-panel>`,{url:'https://m.youtube.com/watch?v=test',runScripts:'outside-only',pretendToBeVisual:true});
const w=dom.window,v=w.document.querySelector('video'),player=v.parentElement;
let clock=100,fsElement=null,connections=0,commands,disconnect,messages=[],revision=0,mode="inline";
w.performance.now=()=>clock;
v.getBoundingClientRect=()=>({left:0,top:0,right:412,bottom:260,width:412,height:260});
Object.defineProperty(v,'duration',{value:200});v.currentTime=62;
Object.defineProperty(v,'paused',{value:false,writable:true});
Object.defineProperty(w.document,'fullscreenElement',{get:()=>fsElement});
player.requestFullscreen=()=>{fsElement=player;w.document.dispatchEvent(new w.Event('fullscreenchange'));return Promise.resolve();};
w.document.exitFullscreen=()=>{fsElement=null;w.document.dispatchEvent(new w.Event('fullscreenchange'));return Promise.resolve();};
w.browser={runtime:{connectNative(){connections++;return {postMessage(x){messages.push(x);if(x.type==='presentationRequest'&&x.revision===revision){if(mode!==x.mode){mode=x.mode;revision++;}commands({command:'presentation',mode,revision});}},onMessage:{addListener(f){commands=f}},onDisconnect:{addListener(f){disconnect=f}},disconnect(){disconnect?.()}};}}};
for(const f of ['background-play.js','gestures.js','viewport-playback.js','links.js','content.js'])w.eval(fs.readFileSync(path.join(base,f),'utf8'));
commands({command:'presentation',mode,revision});
function touch(target,type,x,y,multi=false){const e=new w.Event(type,{bubbles:true,cancelable:true});const t={identifier:1,clientX:x,clientY:y};Object.defineProperties(e,{touches:{value:type==='touchend'?[]:multi?[t,{identifier:2,clientX:x+30,clientY:y}]:[t]},changedTouches:{value:[t]}});target.dispatchEvent(e);return e;}
function swipe(target,x=60,y=220,x2=60,y2=80){touch(target,'touchstart',x,y);clock+=120;const move=touch(target,'touchmove',x2,y2);clock+=80;const end=touch(target,'touchend',x2,y2);return {move,end};}
(async()=>{try{
 await new Promise(r=>setTimeout(r,25));
 let e=swipe(w.document.querySelector('p'));assert.equal(e.move.defaultPrevented,false);assert.equal(e.end.defaultPrevented,false);assert.equal(fsElement,null);console.log('PASS comment gestures remain untouched');
 e=swipe(w.document.querySelector('button'));assert.equal(e.move.defaultPrevented,false);assert.equal(fsElement,null);console.log('PASS player controls remain untouched');
 const original=v;e=swipe(v);await Promise.resolve();assert.equal(player.hasAttribute('data-flowview-full'),true);assert.equal(v.currentTime,62);assert.equal(w.document.querySelector('video'),original);assert.equal(e.end.defaultPrevented,true);console.log('PASS up swipe enters fullscreen without replacing or seeking video');
 swipe(v,60,70,60,230);assert.equal(fsElement,null);assert.equal(player.hasAttribute('data-flowview-full'),false);assert.equal(v.currentTime,62);console.log('PASS down swipe exits fullscreen and preserves position');
 commands({command:'fullscreen'});assert.equal(player.hasAttribute('data-flowview-full'),true);commands({command:'exit'});assert.equal(player.getAttribute('style'),'position:relative');console.log('PASS native fullscreen restores original inline style');
 commands({command:'seek',delta:10});assert.equal(v.currentTime,72);commands({command:'seek',delta:-10});assert.equal(v.currentTime,62);commands({command:'speed',value:1.5});assert.equal(v.playbackRate,1.5);commands({command:'speed',value:20});assert.equal(v.playbackRate,1.5);console.log('PASS playback commands validate and apply values');
 commands({command:'settings',gestures:false});swipe(v);assert.equal(fsElement,null);assert.equal(v.hasAttribute('data-flowview-gesture'),false);console.log('PASS gesture switch disables interception');
 commands({command:'settings',gestures:true});touch(v,'touchstart',60,220,true);clock+=200;touch(v,'touchend',60,80);assert.equal(fsElement,null);console.log('PASS multi-touch does not trigger fullscreen');
 w.history.pushState({},'', '/shorts/test');swipe(v);assert.equal(fsElement,null);console.log('PASS Shorts keeps normal swipe behavior');
 const count=connections;w.dispatchEvent(new w.PageTransitionEvent('pagehide',{persisted:true}));w.dispatchEvent(new w.PageTransitionEvent('pageshow',{persisted:true}));assert.equal(connections,count+1);console.log('PASS back/forward cache reconnects the controller');
 assert.equal(w.location.pathname,'/shorts/test');console.log('10 DOM integration checks passed');
 }finally{w.dispatchEvent(new w.PageTransitionEvent('pagehide'));dom.window.close();}
})().catch(e=>{console.error(e);process.exit(1)});
