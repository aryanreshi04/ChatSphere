import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

export class Store {
  constructor(path = './runtime/state.sqlite3') {
    if (path !== ':memory:') mkdirSync(dirname(path), {recursive:true, mode:0o700});
    this.db = new DatabaseSync(path);
    this.db.exec('PRAGMA journal_mode=WAL; CREATE TABLE IF NOT EXISTS ai_cache (owner TEXT, id TEXT, hash TEXT, reply TEXT, created INTEGER, PRIMARY KEY(owner,id)); CREATE TABLE IF NOT EXISTS push_receipts (key TEXT PRIMARY KEY, created INTEGER);');
    this.db.exec('CREATE TABLE IF NOT EXISTS provider_usage (day TEXT PRIMARY KEY, count INTEGER NOT NULL);');
    this.prune();
  }
  prune(now = Date.now()) {
    this.db.prepare('DELETE FROM ai_cache WHERE created < ?').run(now - 86_400_000);
    this.db.prepare('DELETE FROM push_receipts WHERE created < ?').run(now - 7 * 86_400_000);
  }
  cached(owner, id) { this.prune(); return this.db.prepare('SELECT hash,reply FROM ai_cache WHERE owner=? AND id=?').get(owner,id); }
  save(owner, id, hash, reply) { this.db.prepare('INSERT OR REPLACE INTO ai_cache VALUES (?,?,?,?,?)').run(owner,id,hash,reply,Date.now()); }
  notified(key) { return !!this.db.prepare('SELECT key FROM push_receipts WHERE key=?').get(key); }
  markNotified(key) { this.prune(); this.db.prepare('INSERT OR IGNORE INTO push_receipts VALUES (?,?)').run(key,Date.now()); }
  takeProviderQuota(limit,now=new Date()) {
    const day=now.toISOString().slice(0,10);
    return this.db.prepare('INSERT INTO provider_usage VALUES (?,1) ON CONFLICT(day) DO UPDATE SET count=count+1 WHERE count < ?').run(day,limit).changes===1;
  }
  close() { this.db.close(); }
}
