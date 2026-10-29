(function(root){
  'use strict';
  // A resize may make the site pause an already-playing video. Never infer play
  // intent from a paused video, and never retry beyond this one resize window.
  function create({video,location,now=()=>performance.now(),schedule=setTimeout,unschedule=clearTimeout,onResult=()=>{}}){
    let generation=0,timers=[],until=0,target=null,href='',attempts=0;
    function cancel(){generation++;timers.forEach(unschedule);timers=[];target=null;until=0;}
    function resize(){
      const v=video(),url=location();
      if(!v||!url.includes('/watch?')){cancel();return;}
      if(target===v&&href===url&&now()<until)return;
      cancel();
      if(v.paused||v.ended||v.error)return;
      target=v;href=url;until=now()+1200;attempts=0;
      const token=generation;
      for(const delay of [120,450,1000])timers.push(schedule(()=>{
        if(token!==generation||now()>until)return;
        if(!v.isConnected||video()!==v||location()!==href||v.ended||v.error){cancel();return;}
        if(!v.paused||attempts>=2)return;
        attempts++;
        try{Promise.resolve(v.play()).then(()=>onResult(!v.paused),()=>onResult(false));}
        catch(_){onResult(false);}
      },delay));
      timers.push(schedule(()=>{if(token===generation)cancel();},1200));
    }
    return {resize,cancel};
  }
  root.FlowViewportPlayback={create};
  if(typeof module!=='undefined')module.exports={create};
})(globalThis);
