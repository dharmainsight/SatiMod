const {JSDOM}=require(process.env.FLOWVIEW_JSDOM || 'jsdom');
const fs=require('node:fs');const path=require('node:path');
const assert=require('node:assert/strict');const test=require('node:test');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller/background-play.js'),'utf8');
function page(url,run){const dom=new JSDOM('<video></video>',{url,runScripts:'outside-only'});try{dom.window.eval(source);run(dom.window);}finally{dom.window.close();}}
test('hidden video page remains logically visible to site code',()=>page('https://m.youtube.com/watch?v=abc',w=>{
 assert.equal(w.document.hidden,false);assert.equal(w.document.visibilityState,'visible');
 let pausedByVisibility=false;w.document.addEventListener('visibilitychange',()=>pausedByVisibility=true);
 w.document.dispatchEvent(new w.Event('visibilitychange',{bubbles:true}));assert.equal(pausedByVisibility,false);
}));
test('homepage visibility behavior is preserved including SPA transitions',()=>page('https://m.youtube.com/',w=>{
 assert.equal(w.document.hidden,true);let events=0;w.document.addEventListener('visibilitychange',()=>events++);
 w.document.dispatchEvent(new w.Event('visibilitychange',{bubbles:true}));assert.equal(events,1);
 w.history.pushState({},'','/shorts/test');assert.equal(w.document.hidden,false);
 w.document.dispatchEvent(new w.Event('visibilitychange',{bubbles:true}));assert.equal(events,1);
 w.history.pushState({},'','/');assert.equal(w.document.hidden,true);
}));
test('explicit pause and fullscreen exit remain available',()=>page('https://m.youtube.com/watch?v=abc',w=>{
 let pauses=0,fullChanges=0;
 w.document.querySelector('video').addEventListener('pause',()=>pauses++);
 w.document.addEventListener('fullscreenchange',()=>fullChanges++);
 w.document.querySelector('video').dispatchEvent(new w.Event('pause'));
 w.document.dispatchEvent(new w.Event('fullscreenchange',{bubbles:true}));
 assert.equal(pauses,1);assert.equal(fullChanges,1);
}));
