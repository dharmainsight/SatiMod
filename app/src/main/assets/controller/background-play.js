/* Runs in the page world. No extension APIs, messaging, credentials, or native handles. */
(() => {
  'use strict';
  if(window.top!==window)return;
  const viewing=()=>location.pathname==='/watch'||location.pathname.startsWith('/shorts/');
  // Let Android/Gecko mark the tab inactive normally, while avoiding the site's
  // visibility-triggered pause. Explicit pause/play and fullscreen events remain intact.
  for(const [name,value] of [['hidden',false],['visibilityState','visible']]) {
    const original=Object.getOwnPropertyDescriptor(Document.prototype,name);
    if(!original?.get)continue;
    try{Object.defineProperty(document,name,{
      configurable:true,
      get:()=>viewing()?value:original.get.call(document)
    });}catch(_){}
  }
  window.addEventListener('visibilitychange',event=>{
    if(viewing())event.stopImmediatePropagation();
  },true);
})();
