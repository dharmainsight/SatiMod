(function(root){
  'use strict';
  // A resize or fullscreen/PiP transition may pause an already-playing video.
  // Keep one bounded snapshot of that media; explicit controls always cancel it.
  function create({video,location,now=()=>performance.now(),schedule=setTimeout,unschedule=clearTimeout,onResult=()=>{}}){
    let generation=0,timers=[],until=0,target=null,href='',attempts=0,reason='',source='';
    function cancel(){generation++;timers.forEach(unschedule);timers=[];target=null;until=0;}
    function begin(kind){
      const v=video(),url=location();
      if(!v||!(url.includes('/watch?')||url.includes('/shorts/'))){cancel();return;}
      if(kind==='resize'&&url.includes('/shorts/'))return;
      const continuing=target===v&&href===url&&(v.currentSrc||'')===source&&now()<until;
      if(continuing&&(kind==='resize'||reason==='presentation'))return;
      const previousAttempts=continuing?attempts:0;
      cancel();
      if((!continuing&&v.paused)||v.ended||v.error)return;
      reason=kind;const duration=kind==='presentation'?2200:1200;
      target=v;href=url;source=v.currentSrc||'';until=now()+duration;attempts=previousAttempts;
      const token=generation;
      for(const delay of (kind==='presentation'?[120,450,1000,1800]:[120,450,1000]))timers.push(schedule(()=>{
        if(token!==generation||now()>until)return;
        if(!v.isConnected||video()!==v||location()!==href||(v.currentSrc||'')!==source||v.ended||v.error){cancel();return;}
        if(!v.paused||attempts>=2)return;
        attempts++;
        try{Promise.resolve(v.play()).then(()=>onResult(!v.paused,kind),()=>onResult(false,kind));}
        catch(_){onResult(false,kind);}
      },delay));
      timers.push(schedule(()=>{if(token===generation)cancel();},duration));
    }
    return {resize:()=>begin('resize'),transition:()=>begin('presentation'),cancel};
  }
  root.FlowViewportPlayback={create};
  if(typeof module!=='undefined')module.exports={create};
})(globalThis);
