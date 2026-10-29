const test=require('node:test'),assert=require('node:assert/strict');
const {create}=require('../app/src/main/assets/controller/viewport-playback.js');
function fixture(){
  let time=0,href='https://m.youtube.com/watch?v=test',selected,plays=0,reject=false;
  const timers=new Map();let id=0;
  const v={paused:false,ended:false,error:null,isConnected:true,currentTime:53,play(){plays++;if(reject)return Promise.reject(Error('blocked'));this.paused=false;return Promise.resolve();}};
  selected=v;
  const recovery=create({video:()=>selected,location:()=>href,now:()=>time,schedule:(fn,delay)=>{timers.set(++id,{fn,at:time+delay});return id;},unschedule:i=>timers.delete(i)});
  return {v,recovery,plays:()=>plays,reject:()=>reject=true,url:u=>href=u,replace:()=>selected={...v},tick(ms){const end=time+ms;while(true){const next=[...timers].filter(([,t])=>t.at<=end).sort((a,b)=>a[1].at-b[1].at)[0];if(!next)break;time=next[1].at;timers.delete(next[0]);next[1].fn();}time=end;}};
}
test('shrinking below half resumes a resize-paused video without seeking or replacing it',()=>{
 const f=fixture();f.recovery.resize();f.v.paused=true;f.tick(120);
 assert.equal(f.plays(),1);assert.equal(f.v.paused,false);assert.equal(f.v.currentTime,53);
 f.tick(2000);assert.equal(f.plays(),1);
});
test('already-paused, ended and failed media do not acquire play intent',()=>{
 for(const key of ['paused','ended','error']){const f=fixture();f.v[key]=true;f.recovery.resize();f.tick(2000);assert.equal(f.plays(),0);}
});
test('explicit controls and audio-focus pause cancel all queued recovery',()=>{
 const f=fixture();f.recovery.resize();f.v.paused=true;f.recovery.cancel();f.tick(2000);assert.equal(f.plays(),0);
});
test('continuous divider movement cannot extend retries indefinitely',async()=>{
 const f=fixture();f.reject();f.recovery.resize();f.v.paused=true;
 for(let i=0;i<20;i++){f.tick(100);f.recovery.resize();}
 await Promise.resolve();assert.equal(f.plays(),2);
});
test('navigation, video replacement, end and teardown invalidate the resize snapshot',()=>{
 for(const change of [f=>f.url('https://m.youtube.com/watch?v=next'),f=>f.replace(),f=>f.v.ended=true,f=>f.v.isConnected=false,f=>f.recovery.cancel()]){
  const f=fixture();f.recovery.resize();f.v.paused=true;change(f);f.tick(2000);assert.equal(f.plays(),0);
 }
});
test('home and Shorts keep their own autoplay behavior',()=>{
 for(const url of ['https://m.youtube.com/','https://m.youtube.com/shorts/abc']){const f=fixture();f.url(url);f.recovery.resize();f.v.paused=true;f.tick(2000);assert.equal(f.plays(),0);}
});
