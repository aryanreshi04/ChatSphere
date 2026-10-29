import {ApiError} from './core.mjs';
import {setTimeout as pause} from 'node:timers/promises';
export const DEFAULT_GEMINI_MODEL='gemini-3.1-flash-lite';
export function retryAfterMs(value,now=Date.now()) {
 if(value===null||value===undefined||value==='')return undefined;
 const seconds=Number(value);
 if(Number.isFinite(seconds)&&seconds>=0)return Math.ceil(seconds*1000);
 const date=Date.parse(value);return Number.isFinite(date)?Math.max(0,date-now):undefined;
}
async function boundedJson(response){
 let size=0;const chunks=[];
 for await(const chunk of response.body||[]){size+=chunk.length;if(size>100000)throw new ApiError(503,'Gemini returned an oversized response.',{code:'AI_BAD_RESPONSE'});chunks.push(chunk);}
 try{return JSON.parse(Buffer.concat(chunks).toString('utf8'));}catch(error){if(error instanceof ApiError)throw error;return undefined;}
}
export async function geminiReply(messages,{env=process.env,fetcher=fetch,sleep=pause,random=Math.random,now=Date.now,beforeAttempt,budgetMs=65000,attemptTimeoutMs=25000}={}) {
 if(!env.GEMINI_API_KEY?.trim())throw new ApiError(503,'Gemini key is missing on the backend. Follow docs/SETUP.md.',{code:'AI_CONFIGURATION'});
 if(env.GEMINI_FREE_TIER_CONFIRMED!=='true')throw new ApiError(503,'Confirm the unbilled Gemini project and data-use terms in backend configuration.',{code:'AI_CONFIGURATION'});
 const model=env.GEMINI_MODEL||DEFAULT_GEMINI_MODEL;
 if(!/^[A-Za-z0-9._-]{1,120}$/.test(model))throw new ApiError(503,'Invalid backend Gemini model configuration.',{code:'AI_CONFIGURATION'});
 const requestBody=JSON.stringify({systemInstruction:{parts:[{text:(env.AI_SYSTEM_PROMPT||'You are a student study assistant. Explain concepts clearly, state uncertainty, and never claim access to private chats, accounts or campus records.').slice(0,4000)}]},
  contents:messages.map(m=>({role:m.role==='assistant'?'model':'user',parts:[{text:m.content}]})),generationConfig:{maxOutputTokens:1024}});
 const deadline=now()+budgetMs;let last;
 for(let attempt=0;attempt<3;attempt++){
  const remaining=deadline-now();if(remaining<=0)break;
  last=undefined;
  await beforeAttempt?.(); // Reserve every real attempt against the existing persistent daily cap.
  let response,data;
  try{
   response=await fetcher('https://generativelanguage.googleapis.com/v1beta/models/'+model+':generateContent',{
    method:'POST',redirect:'error',signal:AbortSignal.timeout(Math.max(1,Math.min(attemptTimeoutMs,remaining))),
    headers:{'content-type':'application/json','x-goog-api-key':env.GEMINI_API_KEY},body:requestBody});
   data=await boundedJson(response);
  }catch(error){
   if(error instanceof ApiError)throw error;
   const timeout=['TimeoutError','AbortError'].includes(error?.name);
   last=new ApiError(timeout?504:503,timeout?'Gemini request timed out. Retry later.':'Gemini connection failed or timed out. Check internet and retry.',{code:timeout?'AI_TIMEOUT':'AI_NETWORK',retryable:true});
  }
  if(response){
   const headerWait=retryAfterMs(response.headers.get('retry-after'),now());
   if(response.status===401||response.status===403)throw new ApiError(503,'Gemini rejected the backend key or account eligibility. Check AI Studio.',{code:'AI_CONFIGURATION'});
   if(response.status===404)throw new ApiError(503,'Configured Gemini model is unavailable. Check GEMINI_MODEL and AI Studio.',{code:'AI_MODEL_UNAVAILABLE'});
   if(response.status===400)throw new ApiError(400,'Gemini rejected the request or model configuration. Check backend settings.',{code:'AI_CONFIGURATION'});
   if(response.status===429){
    const detail=JSON.stringify(data?.error||{});const daily=/per.?day|daily|requestsperday|limit\s*:\s*0/i.test(detail);
    const info=data?.error?.details?.find(d=>d['@type']?.endsWith('RetryInfo'));
    const infoWait=typeof info?.retryDelay==='string'&&/^[0-9.]+s$/.test(info.retryDelay)?Number(info.retryDelay.slice(0,-1))*1000:undefined;
    const wait=headerWait??infoWait;
    const transient=!daily&&(wait!==undefined||/RATE_LIMIT_EXCEEDED/.test(detail));
    last=new ApiError(429,daily?'Gemini daily/free-tier quota is exhausted. Check AI Studio limits before retrying.':'Gemini free-tier quota or rate limit reached. Wait and check AI Studio limits.',
     {code:daily?'AI_QUOTA_EXHAUSTED':'AI_RATE_LIMIT',retryable:transient,retryAfterSeconds:wait===undefined?undefined:Math.ceil(wait/1000)});
   }else if([500,502,503,504].includes(response.status))last=new ApiError(503,'Gemini is temporarily unavailable. Retry later.',{code:'AI_PROVIDER_UNAVAILABLE',retryable:true,retryAfterSeconds:headerWait===undefined?undefined:Math.ceil(headerWait/1000)});
   else if(!response.ok)throw new ApiError(503,'Gemini rejected this request. Check the backend configuration.',{code:'AI_CONFIGURATION'});
   else if(!last){
    if(data===undefined)throw new ApiError(503,'Gemini returned an invalid response.',{code:'AI_BAD_RESPONSE'});
    const answer=data.candidates?.[0]?.content?.parts?.filter(p=>!p.thought).map(p=>p.text||'').join('').trim();
    if(!answer)throw new ApiError(422,'Gemini returned no answer, possibly because of a safety restriction. Rephrase your study question.',{code:'AI_NO_ANSWER'});
    return answer.slice(0,8000);
   }
  }
  if(!last)last=new ApiError(503,'Gemini returned an invalid response.',{code:'AI_BAD_RESPONSE'});
  last.providerAttempts=attempt+1;
  if(!last.retryable||attempt===2)throw last;
  const delay=Math.max(700*2**attempt+Math.floor(random()*250),(last.retryAfterSeconds||0)*1000);
  // A long Retry-After is returned to the caller; never shorten it and retry early.
  if(delay>=deadline-now())throw last;
  await sleep(delay);
 }
 throw last||new ApiError(504,'Gemini response deadline reached. Retry later.',{code:'AI_TIMEOUT',retryable:true});
}
