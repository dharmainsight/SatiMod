// Run with Playwright available on NODE_PATH and CHROMIUM_PATH pointing to Chromium.
// Uses the production engine and real Web Audio filters, with a synthetic media source.
const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const source=fs.readFileSync(path.join(__dirname,'../app/src/main/assets/controller/music-engine.js'),'utf8');
(async()=>{
 const browser=await chromium.launch({executablePath:process.env.CHROMIUM_PATH||undefined,headless:true,args:['--no-sandbox']});
 try{
  const page=await browser.newPage();
  await page.route('https://fixture.test/**',route=>route.fulfill({contentType:'text/html',body:'<video></video>'}));
  async function measure(eq,hz=910,amplitude=.01,finalAction=null){
   await page.goto('https://fixture.test/');
   return page.evaluate(async({source,eq,hz,amplitude,finalAction})=>{
    const ctx=new OfflineAudioContext(2,48000,48000),v=document.querySelector('video');
    const osc=ctx.createOscillator(),level=ctx.createGain();osc.frequency.value=hz;level.gain.value=amplitude;osc.connect(level);osc.start();
    Object.defineProperty(ctx,'state',{get:()=> 'running'});
    ctx.resume=async()=>{};ctx.createMediaElementSource=()=>level;
    Object.defineProperty(ctx,'audioWorklet',{value:null});
    Object.defineProperty(v,'readyState',{value:4});
    window.AudioContext=function(){return ctx;};
    window.FlowViewCreateCompatNode=()=>{const n=ctx.createGain();n.parameters=new Map(['pitchSemitones','playbackRate'].map(k=>[k,{value:0}]));return n;};
    (0,eval)(source);
    async function command(command){
     return new Promise((resolve,reject)=>{
      const timeout=setTimeout(()=>reject(Error('engine timeout')),3000);
      function receive(e){if(e.data?.channel==='flowview-music-state'&&!e.data.state.busy){clearTimeout(timeout);removeEventListener('message',receive);if(e.data.state.error)reject(Error(e.data.state.error));else resolve();}}
      addEventListener('message',receive);postMessage({channel:'flowview-music-command',command},location.origin);
     });
    }
    // Capture the source first, including for flat/disabled reference measurements.
    await command({action:'eq',value:[1,0,0,0,0]});
    await command({action:'eq',value:eq});
    if(finalAction)await command(finalAction);
    const buffer=await ctx.startRendering(),samples=buffer.getChannelData(0).slice(24000);
    let sum=0,peak=0;for(const x of samples){sum+=x*x;peak=Math.max(peak,Math.abs(x));}
    return {rms:Math.sqrt(sum/samples.length),peak};
   },{source,eq,hz,amplitude,finalAction});
  }
  for(const [band,hz] of [60,230,910,3600,14000].entries()){
   const flat=await measure([0,0,0,0,0],hz);let previous=0;
   for(const gain of [-12,-6,0,6,12]){
    const eq=[0,0,0,0,0];eq[band]=gain;const result=await measure(eq,hz);
    const db=20*Math.log10(result.rms/flat.rms);
    assert.ok(result.rms>previous,`${hz} Hz: increasing ${gain} dB must increase output`);
    assert.ok(Math.abs(db-gain)<1,`${hz} Hz: expected ${gain} dB, got ${db.toFixed(2)}`);
    previous=result.rms;
   }
   console.log(`PASS ${hz} Hz: -12 < -6 < 0 < +6 < +12 dB`);
  }
  const flat=await measure([0,0,0,0,0]);
  const boosted=await measure([12,12,12,12,12]);
  assert.ok(boosted.rms>flat.rms*3,'boosting all bands must not lower output');
  const disabled=await measure([12,12,12,12,12],910,.01,{action:'enable',value:false});
  assert.ok(Math.abs(disabled.rms-flat.rms)<1e-6,'disable restores dry audio');
  const loud=await measure([12,12,12,12,12],910,.9);
  assert.ok(Number.isFinite(loud.rms)&&loud.peak<1.1,'steady loud tone has controlled peaks');
  console.log('PASS all-band boost, disable, steady-state peak control');
  console.log(JSON.stringify({flat,boosted,loud}));
 }finally{await browser.close();}
})().catch(e=>{console.error(e);process.exitCode=1;});
