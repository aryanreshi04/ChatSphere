import {geminiReply} from './gemini.mjs';
import {Store} from './store.mjs';
import {ApiError} from './core.mjs';
if(!process.env.GEMINI_API_KEY||process.env.GEMINI_FREE_TIER_CONFIRMED!=='true'){
 console.log('SKIPPED: configure the existing unbilled Gemini key and confirmation in ignored backend/.env. No provider request was sent.');
}else{
 const store=new Store(process.env.STATE_DATABASE||'./runtime/state.sqlite3');
 try{
  const answer=await geminiReply([{role:'user',content:'For a connectivity test, what is 2 + 2? Reply briefly.'}],{beforeAttempt:()=>{
   if(!store.takeProviderQuota(Number(process.env.GEMINI_DAILY_REQUEST_LIMIT||40)))throw new ApiError(429,'Configured daily attempt cap reached',{code:'AI_DAILY_LIMIT'});
  }});
  if(!answer.trim())throw new ApiError(503,'Empty provider answer',{code:'AI_NO_ANSWER'});
  console.log('REAL_GEMINI_VERIFIED nonempty=true characters='+answer.length+' (provider attempts counted against the existing configured cap)');
 }catch(error){console.error('REAL_GEMINI_FAILED code='+(error instanceof ApiError?error.code:'UNAVAILABLE'));process.exitCode=1;}
 finally{store.close();}
}
