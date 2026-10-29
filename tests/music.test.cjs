const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),vm=require('node:vm'),path=require('node:path');
const A=require('./helpers/test-pitch.cjs');
function tone(f,n=2048,rate=12000,harmonic=false){return Float32Array.from({length:n},(_,i)=>.35*Math.sin(2*Math.PI*f*i/rate)+(harmonic?.15*Math.sin(4*Math.PI*f*i/rate):0));}
function processTone(semitones,tempo){
 let Processor;const ctx={AudioWorkletProcessor:class{constructor(){this.port={postMessage(){}};}},sampleRate:48000,console,registerProcessor:(n,c)=>Processor=c,Float32Array,Float64Array,Math};
 vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../third_party/soundtouch/soundtouch-processor-2.1.1.js'),'utf8'),ctx);
 const node=new Processor({});const output=[];let sample=0;
 for(let b=0;b<750;b++){
  const input=Float32Array.from({length:128},()=>.3*Math.sin(2*Math.PI*440*tempo*sample++/48000));const left=new Float32Array(128),right=new Float32Array(128);
  node.process([[input,input]],[[left,right]],{pitch:new Float32Array([1]),pitchSemitones:new Float32Array([semitones]),playbackRate:new Float32Array([tempo])});
  if(b>400)output.push(...left);
 }
 const buffer=Float32Array.from({length:2048},(_,i)=>output[i*4]);const note=A.detect(buffer,12000);
 assert.ok(output.every(Number.isFinite));return note;
}
test('bundled audio processor shifts up and down independently of tempo',()=>{
 assert.equal(processTone(3,1)?.note,'C5');assert.equal(processTone(-5,1)?.note,'E4');assert.equal(processTone(3,1.5)?.note,'C5');assert.equal(processTone(-5,.75)?.note,'E4');
});

test('packaged compatibility processor shifts pitch without module loading or note analysis',()=>{
 for(const [semi,tempo,note] of [[3,1,'C5'],[-5,1,'E4'],[3,1.5,'C5'],[-5,.75,'E4']]){
  const scope={window:{},Float32Array,Float64Array,Math,Map,console};vm.runInNewContext(fs.readFileSync(path.resolve(__dirname,'../app/src/main/assets/controller/music-compat.js'),'utf8'),scope);
  const node=scope.window.FlowViewCreateCompatNode({sampleRate:48000,createScriptProcessor:()=>({})});node.parameters.get('pitchSemitones').value=semi;node.parameters.get('playbackRate').value=tempo;
  let sample=0,last;
  for(let b=0;b<48;b++){const input=Float32Array.from({length:2048},()=>.3*Math.sin(2*Math.PI*440*tempo*sample++/48000));const out=[new Float32Array(2048),new Float32Array(2048)];node.onaudioprocess({inputBuffer:{numberOfChannels:2,getChannelData:()=>input},outputBuffer:{getChannelData:i=>out[i]}});last=out[0];assert.ok(last.every(Number.isFinite));}
  assert.equal(A.detect(last,48000)?.note,note);
 }
});
