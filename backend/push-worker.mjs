import {createPushService,firestorePushRepository} from './push.mjs';
import { FieldValue, Timestamp } from 'firebase-admin/firestore';
import { firestore, messaging, emulatorMode } from './firebase.mjs';
import { Store } from './store.mjs';


if (emulatorMode) throw new Error('FCM has no emulator. Use foreground alert tests locally; run this sender only with your real Firebase project and credentials.');
const store = new Store(process.env.STATE_DATABASE || './runtime/state.sqlite3');
const start = Timestamp.now();
const queue = new Map();
let running = false;
const delivery=createPushService({repository:firestorePushRepository(firestore,FieldValue,Timestamp),messaging,store});
async function deliver(doc) {
  const conversation=doc.ref.parent.parent;
  if(!conversation||conversation.parent.id!=='conversations')return;
  await delivery.deliverMessage(conversation.id,doc.id);
}
async function drain() {
  if (running) return;
  running = true;
  try {
    for (const [path,job] of queue) {
      try { await deliver(job.doc); queue.delete(path); }
      catch { if (++job.attempt >= 3) { queue.delete(path); console.error('Push delivery failed after retries; inspect Firebase configuration and quotas.'); } }
    }
  } finally { running = false; }
}
let unsubscribe = ()=>{};
let reconnect;
function subscribe() {
unsubscribe = firestore.collectionGroup('messages').where('serverTimestamp','>=',start).orderBy('serverTimestamp').limitToLast(200)
  .onSnapshot(snapshot=>{
    for (const change of snapshot.docChanges()) if (change.type !== 'removed' && queue.size < 500 && !queue.has(change.doc.ref.path)) {
      queue.set(change.doc.ref.path,{doc:change.doc,attempt:0});
    }
    void drain();
  },()=>{
    console.error('Message listener unavailable. Check credentials, collection-group index, and quotas. Retrying in 30 seconds.');
    unsubscribe(); reconnect = setTimeout(subscribe,30_000);
  });
}
subscribe();
const timer = setInterval(()=>void drain(),10_000);
console.log('ChatSphere local FCM sender started. Only new server-timestamped messages after startup are observed.');
function stop() { unsubscribe(); clearTimeout(reconnect); clearInterval(timer); store.close(); process.exit(0); }
process.on('SIGINT',stop); process.on('SIGTERM',stop);
