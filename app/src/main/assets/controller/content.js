(() => {
  'use strict';
  if (window.top !== window) return;
  const G = globalThis.FlowGestures;
  const overlay = 'ytm-engagement-panel,ytm-engagement-panel-section-list-renderer,ytm-comments-engagement-panel,ytm-comment-section-renderer,ytm-comment-thread-renderer,ytm-comment-renderer,[role="dialog"],tp-yt-paper-dialog';
  const control = 'button,a,input,textarea,select,[role="button"],[role="slider"],[contenteditable="true"]';
  let port, touch, hintTimer, videoStateTimer, gestures = true, fakeFull = null;
  let lastTap = null, currentHref = location.href, lastReport = 0, reconnectTimer, active = false;
  let selectedVideo=null, requestedMode='inline', presentationRevision=-1, pendingRequest=null, nativeExitPending=false;
  let nativeFullscreenSwipe=false,dragTarget=null,dragTimer;
  function video() {
    if(requestedMode!=='inline'&&selectedVideo?.isConnected)return selectedVideo;
    const all=[...document.querySelectorAll('video')];
    const visible=all.filter(v=>{const r=v.getBoundingClientRect();return r.width>80&&r.height>50;})
      .sort((a,b)=>Number(a.paused)-Number(b.paused)||b.clientWidth*b.clientHeight-a.clientWidth*a.clientHeight);
    // Surface resize/background rendering can temporarily give a live video a zero rectangle.
    selectedVideo=visible[0]||(selectedVideo?.isConnected?selectedVideo:all.find(v=>!v.paused)||all[0])||null;
    return selectedVideo;
  }
  const isShort = () => location.pathname.startsWith('/shorts/');
  const full = () => !!fakeFull || (!!document.fullscreenElement && !nativeExitPending);
  function post(message) { try { port?.postMessage(message); } catch (_) {} }
  const viewportPlayback=globalThis.FlowViewportPlayback.create({video,location:()=>location.href,
    onResult:ok=>post({type:'viewportRecovery',ok})});
  // Capture before the site's resize handlers can pause the current video.
  const resized=()=>{viewportPlayback.resize();fitCompactPlayer();};
  window.addEventListener('resize',resized,true);
  window.visualViewport?.addEventListener('resize',resized);
  for(const type of ['pointerdown','touchstart','keydown','click'])document.addEventListener(type,e=>{
    if(e.isTrusted)viewportPlayback.cancel();
  },true);
  function clearDrag(){
    clearTimeout(dragTimer);
    if(dragTarget){dragTarget.removeAttribute('data-flowview-drag');dragTarget.removeAttribute('data-flowview-settle');}
    dragTarget=null;
    for(const name of ['--flowview-drag-y','--flowview-drag-scale','--flowview-drag-radius'])document.documentElement.style.removeProperty(name);
  }
  function previewDrag(message){
    if(!full()||requestedMode==='pip'||message.revision!==presentationRevision)return;
    const el=fakeFull||document.fullscreenElement;
    if(!el)return;
    if(dragTarget!==el){clearDrag();dragTarget=el;}
    clearTimeout(dragTimer);
    const progress=Math.max(0,Math.min(1,Number(message.progress)||0));
    el.setAttribute('data-flowview-drag','');
    el.toggleAttribute('data-flowview-settle',!!message.settle);
    const style=document.documentElement.style;
    style.setProperty('--flowview-drag-y',Math.min(innerHeight*.24,96)*progress+'px');
    style.setProperty('--flowview-drag-scale',String(1-.16*progress));
    style.setProperty('--flowview-drag-radius',24*progress+'px');
    if(message.settle)dragTimer=setTimeout(clearDrag,220);
  }
  function hint(message) {
    if (!document.body) return;
    let el = document.getElementById('flowview-hint');
    if (!el) { el = document.createElement('div'); el.id='flowview-hint'; el.setAttribute('role','status'); (fakeFull || document.fullscreenElement || document.body).append(el); }
    el.textContent = message; clearTimeout(hintTimer); hintTimer=setTimeout(()=>el.remove(),1400);
  }
  function player(v) { return v.closest('.html5-video-player') || v.closest('#player-container-id') || v; }
  function fitCompactPlayer(){
    const previous=[...document.querySelectorAll('[data-flowview-compact-player]')];
    const height=window.visualViewport?.height||innerHeight,v=video();
    let targets=[],size=0;
    if(location.hostname==='m.youtube.com'&&location.pathname==='/watch'&&requestedMode==='inline'&&height<420&&v){
      const p=player(v),container=v.closest('#player-container-id')||p,r=container.getBoundingClientRect();
      const width=r.width||v.getBoundingClientRect().width,top=Math.max(0,r.top);
      if(width>0&&top<height){
        const ratio=v.videoWidth>0?v.videoHeight/v.videoWidth:9/16;
        size=Math.max(1,Math.min(width*ratio,height-top-8));targets=[...new Set([container,p])];
      }
    }
    for(const el of previous)if(!targets.includes(el))el.removeAttribute('data-flowview-compact-player');
    for(const el of targets)el.setAttribute('data-flowview-compact-player','');
    if(targets.length)document.documentElement.style.setProperty('--flowview-compact-height',size+'px');
    else document.documentElement.style.removeProperty('--flowview-compact-height');
  }
  // Observations are diagnostics, never requests. Only explicit input changes native mode.
  function reportFull() { post({type:'fullscreen', value:full(), revision:presentationRevision}); }
  function requestPresentation(mode) {
    if(presentationRevision<0 || pendingRequest===mode)return;
    pendingRequest=mode;touch=null;lastTap=null;
    post({type:'presentationRequest',mode,revision:presentationRevision});
  }
  function clearPresentation() {
    clearDrag();
    if(fakeFull){fakeFull.removeAttribute('data-flowview-full');fakeFull=null;}
    document.querySelectorAll('[data-flowview-ancestor]').forEach(el=>el.removeAttribute('data-flowview-ancestor'));
    document.documentElement.removeAttribute('data-flowview-presentation');
  }
  function syncAncestors(el) {
    const ancestors=new Set();
    for(let p=el.parentElement;p&&p!==document.documentElement;p=p.parentElement)ancestors.add(p);
    for(const old of document.querySelectorAll('[data-flowview-ancestor]'))if(!ancestors.has(old))old.removeAttribute('data-flowview-ancestor');
    for(const p of ancestors)if(!p.hasAttribute('data-flowview-ancestor'))p.setAttribute('data-flowview-ancestor','');
  }
  function exitNative() {
    if(!document.fullscreenElement||nativeExitPending)return;
    nativeExitPending=true;
    try { Promise.resolve(document.exitFullscreen()).catch(()=>{}).finally(()=>{nativeExitPending=false;}); }
    catch(_){nativeExitPending=false;}
  }
  function enter(mode='full') {
    requestedMode=mode;
    const v=video();if(!v){clearPresentation();return false;}
    if(document.fullscreenElement&&mode==='full'&&!fakeFull&&!nativeExitPending)return true;
    const el=player(v);
    if(fakeFull!==el){clearPresentation();fakeFull=el;}
    // CSS owns only our attributes. Never overwrite YouTube's live inline style.
    el.setAttribute('data-flowview-full','');syncAncestors(el);
    document.documentElement.setAttribute('data-flowview-presentation',mode);
    if(mode==='pip')exitNative();
    return true;
  }
  function exit() {
    requestedMode='inline';clearPresentation();exitNative();
  }
  function reportMedia() {
    if(!active)return;
    const v=video();post({type:'media',playing:!!v&&!v.paused&&!v.ended});
  }
  function playVideo() {
    const v=video();
    if(!v){post({type:'playResult',ok:false,error:'NoVideo'});return;}
    Promise.resolve(v.play()).then(()=>{reportMedia();post({type:'playResult',ok:!v.paused,error:v.paused?'Paused':''});})
      .catch(error=>{reportMedia();post({type:'playResult',ok:false,error:error?.name||'PlayError'});});
  }
  function seek(delta) {
    const v=video(); if (!v || !Number.isFinite(v.currentTime)) return;
    try { v.currentTime=G.clampSeek(v.currentTime,delta,v.duration); hint(delta>0?'10秒進む':'10秒戻る'); } catch (_) { hint('この動画では移動できません'); }
  }
  function handle(message) {
    const v=video();
    if (!message || typeof message !== 'object') return;
    switch(message.command) {
      case 'settings': gestures=message.gestures !== false;nativeFullscreenSwipe=message.nativeFullscreenSwipe===true;touch=null;lastTap=null;clearDrag();markPlayers();break;
      case 'fullscreenDrag':previewDrag(message);break;
      case 'fullscreen':requestPresentation(full()?'inline':'full');break;
      case 'presentation':
        if(!['inline','full','pip'].includes(message.mode)||!Number.isSafeInteger(message.revision)||message.revision<presentationRevision)break;
        presentationRevision=message.revision;pendingRequest=null;touch=null;lastTap=null;clearDrag();
        {const ok=message.mode==='inline'?(exit(),true):enter(message.mode);
        fitCompactPlayer();
        post({type:'presentationApplied',mode:message.mode,revision:presentationRevision,ok,error:ok?'':'NoVideo'});}break;
      case 'state':reportFull();reportMedia();break;
      case 'cancelGesture':touch=null;lastTap=null;clearDrag();break;
      case 'music':window.postMessage({channel:'flowview-music-command',command:message.data},location.origin);break;
      case 'play':viewportPlayback.cancel();playVideo();break;
      case 'pause':viewportPlayback.cancel();if(v)v.pause();reportMedia();break;
      case 'exit':requestPresentation('inline');break;
      case 'playpause': viewportPlayback.cancel();post({type:'interaction'});if(v){if(v.paused)playVideo();else {v.pause();reportMedia();}}break;
      case 'seek': if(message.delta===10 || message.delta===-10) seek(message.delta); break;
      case 'speed': if(v && [0.5,0.75,1,1.25,1.5,1.75,2].includes(message.value)) { v.playbackRate=message.value; hint(message.value+'倍速'); } break;
      case 'top': document.querySelectorAll(overlay).forEach(el=>{ if(el.scrollHeight>el.clientHeight) el.scrollTo({top:0,behavior:'smooth'}); }); window.scrollTo({top:0,behavior:'smooth'}); break;
    }
  }
  function connect() {
    try {
      port=browser.runtime.connectNative('flowview');
      port.onMessage.addListener(handle);
      port.onDisconnect.addListener(()=>{port=null;clearTimeout(reconnectTimer);if(active)reconnectTimer=setTimeout(connect,2000);});
      post({type:'ready'});
    } catch (_) { clearTimeout(reconnectTimer);if(active)reconnectTimer=setTimeout(connect,2000); }
  }
  function allowedTarget(target,v) {
    if (!(target instanceof Element) || target.closest(overlay) || target.closest(control)) return false;
    const p=player(v);
    return p===target || p.contains(target) || target===v;
  }
  document.addEventListener('contextmenu',e=>{
    if(!e.isTrusted||!port)return;
    const url=globalThis.FlowLinks?.find(e.target,location.href);if(!url)return;
    touch=null;lastTap=null;e.preventDefault();e.stopImmediatePropagation();
    post({type:'copyLink',url});
  },{capture:true,passive:false});
  document.addEventListener('touchstart', e=>{
    if(e.isTrusted)post({type:'interaction'});
    touch=null;
    if(!gestures || e.touches.length!==1 || (isShort() && !full())) return;
    const v=video(); if(!v || !allowedTarget(e.target,v)) return;
    const t=e.touches[0],r=v.getBoundingClientRect();
    if(full()&&t.clientY<=36)return;
    if(!full() && (t.clientX<r.left || t.clientX>r.right || t.clientY<r.top || t.clientY>r.bottom)) return;
    touch={id:t.identifier,x:t.clientX,y:t.clientY,time:performance.now(),fullscreen:full(),v,valid:true};
  },{capture:true,passive:true});
  document.addEventListener('touchmove',e=>{
    if(!touch) return;
    if(e.touches.length!==1) { touch=null; return; }
    const t=e.touches[0],dx=t.clientX-touch.x,dy=t.clientY-touch.y;
    if(Math.abs(dy)>12 && Math.abs(dy)>Math.abs(dx)*1.6 && (touch.fullscreen?(!nativeFullscreenSwipe&&touch.y>36&&dy>0):dy<0)) {
      if(e.cancelable)e.preventDefault();
      e.stopImmediatePropagation();
    }
  },{capture:true,passive:false});
  document.addEventListener('touchend',e=>{
    const start=touch; touch=null;
    if(!start || e.touches.length) return;
    const t=[...e.changedTouches].find(x=>x.identifier===start.id); if(!t)return;
    const dx=t.clientX-start.x,dy=t.clientY-start.y,dt=performance.now()-start.time;
    const action=start.fullscreen&&(nativeFullscreenSwipe||start.y<=36)?null:G.classify(dx,dy,dt,start.fullscreen);
    if(action) {
      if(e.cancelable)e.preventDefault();e.stopImmediatePropagation();lastTap=null;
      requestPresentation(action==='enter'?'full':'inline');return;
    }
    if(Math.abs(dx)<16 && Math.abs(dy)<16 && dt<260) {
      const r=start.v.getBoundingClientRect(), side=(t.clientX-r.left)/r.width;
      const region=side<0.33?'left':side>0.67?'right':null, now=performance.now();
      if(region && lastTap && lastTap.region===region && now-lastTap.time<330) {
        if(e.cancelable)e.preventDefault(); e.stopImmediatePropagation(); seek(region==='left'?-10:10); lastTap=null;
      } else lastTap=region?{region,time:now}:null;
    } else lastTap=null;
  },{capture:true,passive:false});
  document.addEventListener('touchcancel',()=>{touch=null;lastTap=null;},{capture:true,passive:true});
  document.addEventListener('fullscreenchange',()=>{
    touch=null;lastTap=null;
    if(nativeExitPending||fakeFull||requestedMode==='pip')return;
    requestPresentation(document.fullscreenElement?'full':'inline');
  },true);
  for(const type of ['blur','resize','orientationchange'])window.addEventListener(type,()=>{touch=null;lastTap=null;clearDrag();});
  for(const type of ['play','pause','ended'])document.addEventListener(type,reportMedia,true);
  function markPlayers() {
    for(const el of document.querySelectorAll('[data-flowview-gesture]')) if(!gestures || isShort())el.removeAttribute('data-flowview-gesture');
    if(requestedMode!=='inline')enter(requestedMode);
    if(gestures && !isShort()) for(const v of document.querySelectorAll('video')) {v.setAttribute('data-flowview-gesture','');player(v).setAttribute('data-flowview-gesture','');}
    fitCompactPlayer();
  }
  let scheduled=false, frameId=null;
  const observer=new MutationObserver(()=>{
    if(scheduled||!active)return;scheduled=true;frameId=requestAnimationFrame(()=>{scheduled=false;frameId=null;if(active)markPlayers();});
  });
  window.addEventListener('message',e=>{if(active&&e.source===window&&e.origin===location.origin&&e.data?.channel==='flowview-music-state'&&e.data.state&&typeof e.data.state==='object')post({type:'music',state:e.data.state});});
  function init() {
    if(active)return;active=true;
    markPlayers(); observer.observe(document.documentElement,{subtree:true,childList:true});connect();
    videoStateTimer=setInterval(()=>{
      if(currentHref!==location.href) { viewportPlayback.cancel();currentHref=location.href; touch=null;lastTap=null;selectedVideo=null;if(location.pathname!=='/watch'&&!isShort()){exit();requestPresentation('inline');}markPlayers(); }
      const v=video();
      if(v) {
        const now=Date.now();
        reportMedia();
        if(now-lastReport>5000) {lastReport=now;post({type:'position',url:location.href,time:Number.isFinite(v.currentTime)?v.currentTime:0});}
      } else post({type:'media',playing:false});
    },1200);
  }
  if(document.documentElement)init(); else document.addEventListener('DOMContentLoaded',init,{once:true});
  window.addEventListener('pagehide',()=>{viewportPlayback.cancel();clearDrag();active=false;clearInterval(videoStateTimer);clearTimeout(reconnectTimer);if(frameId!==null)cancelAnimationFrame(frameId);frameId=null;scheduled=false;observer.disconnect();try{port?.disconnect();}catch(_){};port=null;});
  window.addEventListener('pageshow',e=>{if(e.persisted)init();});
})();
