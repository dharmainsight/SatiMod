const {chromium}=require('playwright');
const fs=require('node:fs');const path=require('node:path');const assert=require('node:assert/strict');
const base=path.resolve(__dirname,'../app/src/main/assets/controller');
const html=`<!doctype html><meta name="viewport" content="width=device-width, initial-scale=1"><style>
*{box-sizing:border-box}body{margin:0;background:#101416;color:white;font-family:sans-serif}.html5-video-player{position:relative;width:100%;height:250px;background:#000}video{display:block;width:100%;height:100%;object-fit:contain}button{height:48px}.controls{position:absolute;bottom:0;left:30%;z-index:2}ytm-engagement-panel{position:fixed;display:block;top:300px;bottom:0;width:100%;background:#202826;overflow:auto;z-index:5}p{padding:16px;border-bottom:1px solid #52625e}.spacer{height:1600px}
</style><div class="html5-video-player"><video muted playsinline></video><button class="controls">Pause</button></div><h3>動画テスト</h3><ytm-engagement-panel>${Array.from({length:70},(_,i)=>'<p>コメント '+i+'</p>').join('')}</ytm-engagement-panel><div class="spacer"></div><script>
const c=document.createElement('canvas');c.width=640;c.height=360;const ctx=c.getContext('2d');let t=0;
setInterval(()=>{ctx.fillStyle='#15392c';ctx.fillRect(0,0,640,360);ctx.fillStyle='#78e1be';ctx.font='40px sans-serif';ctx.fillText('FlowView '+(++t),60,190);},80);
const v=document.querySelector('video');v.srcObject=c.captureStream(12);v.play();window.originalVideo=v;
<\/script>`;
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.FLOWVIEW_CHROME,headless:true,args:['--no-sandbox','--autoplay-policy=no-user-gesture-required']});
 try{
 const ctx=await browser.newContext({viewport:{width:412,height:915},hasTouch:true,isMobile:true});
 const page=await ctx.newPage();let loads=0;const errors=[];
 page.on('pageerror',e=>errors.push(e.message));page.on('framenavigated',f=>{if(f===page.mainFrame())loads++;});
 await page.route('https://m.youtube.com/**',r=>r.fulfill({contentType:'text/html',body:html}));
 await page.addInitScript({content:`window.messages=[];window.nativeRevision=0;window.nativeMode="inline";window.browser={runtime:{connectNative(){return {postMessage(m){window.messages.push(m);if(m.type==='ready')window.nativeCommand({command:'presentation',mode:window.nativeMode,revision:window.nativeRevision});else if(m.type==='presentationRequest'&&m.revision===window.nativeRevision){if(window.nativeMode!==m.mode){window.nativeMode=m.mode;window.nativeRevision++;}window.nativeCommand({command:'presentation',mode:window.nativeMode,revision:window.nativeRevision});}},onMessage:{addListener(f){window.nativeCommand=f}},onDisconnect:{addListener(){}},disconnect(){}}}};`+fs.readFileSync(path.join(base,'background-play.js'),'utf8')+'\n'+fs.readFileSync(path.join(base,'gestures.js'),'utf8')+'\n'+fs.readFileSync(path.join(base,'links.js'),'utf8')+'\n'+fs.readFileSync(path.join(base,'content.js'),'utf8')});
 await page.goto('https://m.youtube.com/watch?v=fixture');await page.addStyleTag({content:fs.readFileSync(path.join(base,'content.css'),'utf8')});
 await page.waitForFunction(()=>window.nativeCommand&&document.querySelector('video').currentTime>0.2);
 const cdp=await ctx.newCDPSession(page);
 async function swipe(x,y,x2,y2){
  await cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x,y}]});
  for(let i=1;i<=6;i++){await page.waitForTimeout(25);await cdp.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:x+(x2-x)*i/6,y:y+(y2-y)*i/6}]});}
  await cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await page.waitForTimeout(100);
 }
 // Scroll a deep comment sheet downwards: no fullscreen, no document navigation.
 await page.evaluate(()=>document.querySelector('ytm-engagement-panel').scrollTop=900);
 await swipe(200,440,200,720);
 assert.equal(loads,1);assert.equal(await page.evaluate(()=>!!document.fullscreenElement),false);
 assert.ok(await page.evaluate(()=>document.querySelector('ytm-engagement-panel').scrollTop)<900);
 console.log('PASS comments scroll upward without navigation');
 // Overlay covering the video must not become a player gesture.
 await page.evaluate(()=>{const p=document.querySelector('ytm-engagement-panel');p.style.top='0';p.scrollTop=500;});
 await swipe(200,200,200,80);assert.equal(await page.evaluate(()=>!!document.fullscreenElement),false);
 console.log('PASS overlapping comment sheet does not enter fullscreen');
 await page.evaluate(()=>document.querySelector('ytm-engagement-panel').style.display='none');
 let before=await page.evaluate(()=>document.querySelector('video').currentTime);
 await swipe(70,210,70,80);
 assert.ok(await page.evaluate(()=>!!document.fullscreenElement||document.querySelector('[data-flowview-full]')));
 assert.equal(await page.evaluate(()=>document.querySelector('video')===window.originalVideo),true);
 assert.ok(await page.evaluate(()=>document.querySelector('video').currentTime)>=before);assert.equal(loads,1);
 console.log('PASS up swipe retains video node and playback time');
 await swipe(70,100,70,250);
 await page.waitForFunction(()=>!document.fullscreenElement&&!document.querySelector('[data-flowview-full]'));
 assert.equal(loads,1);console.log('PASS down swipe exits fullscreen without navigation');
 await page.evaluate(()=>window.nativeCommand({command:'fullscreen'}));
 assert.ok(await page.evaluate(()=>!!document.querySelector('[data-flowview-full]')));
 await page.evaluate(()=>window.nativeCommand({command:'exit'}));
 assert.equal(await page.evaluate(()=>document.querySelector('.html5-video-player').getAttribute('style')),null);
 console.log('PASS toolbar fullscreen restores original player style');
 await page.evaluate(()=>window.nativeCommand({command:'speed',value:1.5}));
 assert.equal(await page.evaluate(()=>document.querySelector('video').playbackRate),1.5);
 await page.evaluate(()=>window.nativeCommand({command:'settings',gestures:false}));await swipe(70,210,70,80);
 assert.equal(await page.evaluate(()=>!!document.fullscreenElement),false);
 console.log('PASS speed and gesture toggle');
 await page.evaluate(()=>{window.nativeCommand({command:'settings',gestures:true});history.pushState({},'', '/shorts/fixture');});await page.waitForTimeout(1400);
 await swipe(70,210,70,80);assert.equal(await page.evaluate(()=>!!document.fullscreenElement),false);
 assert.equal(await page.evaluate(()=>document.querySelector('video').hasAttribute('data-flowview-gesture')),false);
 console.log('PASS Shorts leaves native swipe navigation intact');
 assert.deepEqual(errors,[]);console.log('PASS no JavaScript errors');
 if(process.env.FLOWVIEW_SCREENSHOT)await page.screenshot({path:path.resolve(process.env.FLOWVIEW_SCREENSHOT)});
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
