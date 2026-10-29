(function(root){
  'use strict';
  const cards='ytm-video-with-context-renderer,ytm-rich-item-renderer,ytm-compact-video-renderer,ytm-reel-item-renderer,ytm-shorts-lockup-view-model,ytd-rich-item-renderer,ytd-video-renderer,ytd-compact-video-renderer,yt-shorts-lockup-view-model';
  function safeUrl(value,base){
    try{const u=new URL(value,base);return ['http:','https:'].includes(u.protocol)&&!u.username&&!u.password?u.href:null;}catch(_){return null;}
  }
  function find(target,base){
    if(!target||typeof target.closest!=='function')return null;
    const direct=target.closest('a[href]');
    if(direct)return safeUrl(direct.getAttribute('href'),base);
    const card=target.closest(cards);
    if(card){
      for(const a of card.querySelectorAll('a[href]')){
        const url=safeUrl(a.getAttribute('href'),base);if(!url)continue;
        const u=new URL(url);if(['youtube.com','www.youtube.com','m.youtube.com','youtu.be'].includes(u.hostname)&&(u.pathname==='/watch'||u.pathname.startsWith('/shorts/')||u.hostname==='youtu.be'))return url;
      }
    }
    if(target.closest('video,.html5-video-player,#player-container-id')){
      const url=safeUrl(base,base);if(!url)return null;
      const u=new URL(url);if(['youtube.com','www.youtube.com','m.youtube.com'].includes(u.hostname)&&(u.pathname==='/watch'||u.pathname.startsWith('/shorts/')))return url;
    }
    return null;
  }
  root.FlowLinks={find,safeUrl};if(typeof module!=='undefined')module.exports=root.FlowLinks;
})(globalThis);
