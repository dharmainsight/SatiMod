const {JSDOM}=require(process.env.FLOWVIEW_JSDOM || 'jsdom');
const assert=require('node:assert/strict');
const test=require('node:test');
const links=require('../app/src/main/assets/controller/links.js');
const base='https://m.youtube.com/watch?v=current';
function find(html,selector,url=base){const dom=new JSDOM(html);try{return links.find(dom.window.document.querySelector(selector),url);}finally{dom.window.close();}}
test('nested thumbnails copy their destination',()=>{
 assert.equal(find('<a href="/watch?v=next"><img></a>','img'),'https://m.youtube.com/watch?v=next');
 assert.equal(find('<a href="https://example.com/page">External</a>','a'),'https://example.com/page');
});
test('card without an anchor ancestor selects video rather than channel',()=>{
 assert.equal(find('<ytm-rich-item-renderer><a href="/@channel">Channel</a><div id="target">Thumbnail</div><a href="/watch?v=next">Video</a></ytm-rich-item-renderer>','#target'),'https://m.youtube.com/watch?v=next');
});
test('Shorts card and player resolve shareable URLs',()=>{
 assert.equal(find('<ytm-reel-item-renderer><div id="target"></div><a href="/shorts/next">Short</a></ytm-reel-item-renderer>','#target'),'https://m.youtube.com/shorts/next');
 assert.equal(find('<video src="blob:https://m.youtube.com/123"></video>','video','https://m.youtube.com/shorts/current'),'https://m.youtube.com/shorts/current');
});
test('video overlay copies page URL instead of temporary media URL',()=>{
 assert.equal(find('<div class="html5-video-player"><video src="blob:temporary"></video><div id="overlay"></div></div>','#overlay'),base);
});
test('plain comments and homepage do not copy an unrelated page',()=>{
 assert.equal(find('<ytm-comment-thread-renderer><p>Comment</p></ytm-comment-thread-renderer>','p'),null);
 assert.equal(find('<video></video>','video','https://m.youtube.com/'),null);
});
test('unsafe schemes and embedded credentials are rejected',()=>{
 for(const href of ['javascript:alert(1)','data:text/html,hello','file:///tmp/file','mailto:test@example.com','https://user:secret@example.com'])
  assert.equal(find(`<a href="${href}">x</a>`,'a'),null,href);
});
