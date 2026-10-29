const assert=require('node:assert/strict');
const test=require('node:test');
const g=require('../app/src/main/assets/controller/gestures.js');
test('vertical gestures are directional and require intent',()=>{
 assert.equal(g.classify(5,-100,250,false),'enter');
 assert.equal(g.classify(8,120,280,true),'exit');
 for(const args of [[5,120,300,false],[5,-120,300,true],[100,-80,250,false],[0,-50,250,false],[0,-120,1400,false],[0,-120,5,false]])assert.equal(g.classify(...args),null);
});
test('seek respects bounds and live durations',()=>{
 assert.equal(g.clampSeek(4,-10,100),0);assert.equal(g.clampSeek(98,10,100),100);assert.equal(g.clampSeek(100,10,Infinity),110);
});
test('URL allowlist rejects lookalikes and insecure URLs',()=>{
 for(const u of ['https://m.youtube.com/watch?v=abc','https://youtu.be/abc'])assert.equal(g.youtubeUrl(u),true);
 for(const u of ['https://youtube.com.evil.test/','http://m.youtube.com/','javascript:alert(1)','https://youtube.com@evil.test'])assert.equal(g.youtubeUrl(u),false);
});
