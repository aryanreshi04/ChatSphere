import { createHash } from 'node:crypto';

export class ApiError extends Error {
  constructor(status,message,{code,retryable=false,retryAfterSeconds,providerAttempts}={}) {
    super(message);this.status=status;this.code=code||(status===401?'AUTHENTICATION_REQUIRED':status===429?'RATE_LIMIT':'BACKEND_ERROR');
    this.retryable=retryable;this.retryAfterSeconds=retryAfterSeconds;this.providerAttempts=providerAttempts;
  }
}
export function validateChat(body) {
  if (!body || typeof body !== 'object' || !/^[A-Za-z0-9-]{1,80}$/.test(body.requestId ?? '') ||
      Object.keys(body).some(key => !['requestId','messages'].includes(key))) throw new ApiError(400, 'Invalid chat request');
  if (!Array.isArray(body.messages) || body.messages.length < 1 || body.messages.length > 20) throw new ApiError(400, 'Use 1 to 20 messages');
  let total = 0;
  const messages = body.messages.map((message, index) => {
    if (!message || !['user','assistant'].includes(message.role) || typeof message.content !== 'string' ||
        !message.content.trim() || message.content.length > 1000 || Object.keys(message).some(k => !['role','content'].includes(k)) ||
        (index && message.role === body.messages[index-1].role)) throw new ApiError(400, 'Invalid message history');
    total += message.content.length;
    return { role: message.role, content: message.content };
  });
  if (total > 20_000 || messages.at(-1).role !== 'user') throw new ApiError(400, 'End with a user message');
  return { requestId: body.requestId, messages, hash: createHash('sha256').update(JSON.stringify(messages)).digest('hex') };
}
export class RateLimiter {
  constructor({perMinute=5,perDay=60}={}) { this.entries = new Map();this.perMinute=perMinute;this.perDay=perDay; }
  take(uid, now = Date.now()) {
    // Expire inactive users so the local server has a bounded memory footprint.
    for (const [key, entry] of this.entries) if (entry.day <= now - 86_400_000) this.entries.delete(key);
    const entry = this.entries.get(uid) ?? { day: now, count: 0, minute: now, recent: 0 };
    if (entry.minute <= now - 60_000) { entry.minute = now; entry.recent = 0; }
    if (entry.count >= this.perDay || entry.recent >= this.perMinute || (!this.entries.has(uid) && this.entries.size >= 1000)) throw new ApiError(429, 'Request limit reached');
    entry.count++; entry.recent++; this.entries.set(uid, entry);
  }
}
export function recipients(thread, message) {
  if (!thread || !Array.isArray(thread.participants) || thread.participants.length !== 2 ||
      thread.participants.some(uid => typeof uid !== 'string' || !/^[A-Za-z0-9_-]{1,128}$/.test(uid)) ||
      thread.participants[0] === thread.participants[1] || !thread.participants.includes(message?.senderId)) return [];
  return thread.participants.filter(uid => uid !== message.senderId);
}
