/* Local monophonic pitch estimate. No audio leaves the device. */
(function(root){
 'use strict';
 const names=['C','C♯','D','D♯','E','F','F♯','G','G♯','A','A♯','B'];
 function detect(input,rate){
  const n=input.length;if(n<256||!Number.isFinite(rate)||rate<=0)return null;
  let mean=0;for(let i=0;i<n;i++)mean+=input[i];mean/=n;
  let energy=0;for(let i=0;i<n;i++)energy+=(input[i]-mean)**2;
  if(Math.sqrt(energy/n)<0.008)return null;
  const min=Math.max(2,Math.floor(rate/1800)),max=Math.min(Math.floor(rate/65),n>>1),size=n-max,diff=new Float64Array(max+1);
  let running=0;
  for(let lag=1;lag<=max;lag++){
   let sum=0;for(let i=0;i<size;i++){const d=input[i]-input[i+lag];sum+=d*d;}
   running+=sum;diff[lag]=running?sum*lag/running:1;
  }
  for(let lag=min;lag<max;lag++)if(diff[lag]<0.12){
   while(lag+1<=max&&diff[lag+1]<diff[lag])lag++;
   const prev=diff[lag-1],next=diff[lag+1]??diff[lag],den=2*(2*diff[lag]-next-prev);
   const refined=lag+(Math.abs(den)>1e-9?(next-prev)/den:0),frequency=rate/refined;
   const exact=69+12*Math.log2(frequency/440),midi=Math.round(exact);
   if(midi<36||midi>93)return null;
   return {frequency,midi,note:names[(midi%12+12)%12]+(Math.floor(midi/12)-1),cents:Math.round((exact-midi)*100),confidence:1-diff[lag]};
  }
  return null;
 }
 class Peak {
  constructor(){this.reset();}
  reset(){this.midi=null;this.note='—';this.candidate=null;this.count=0;}
  push(p){
   if(!p||p.confidence<0.9){this.candidate=null;this.count=0;return;}
   if(p.midi===this.candidate)this.count++;else{this.candidate=p.midi;this.count=1;}
   if(this.count>=3&&(this.midi===null||p.midi>this.midi)){this.midi=p.midi;this.note=p.note;}
  }
 }
 const api={detect,Peak};if(typeof module==='object')module.exports=api;else root.FlowMusicAnalysis=api;
})(globalThis);
