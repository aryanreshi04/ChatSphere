import { createServer } from 'node:http';
import { pathToFileURL } from 'node:url';
import { ApiError, RateLimiter, validateChat } from './core.mjs';
import { Store } from './store.mjs';
import {
  createPushService,
  firestorePushRepository
} from './push.mjs';
import { geminiReply } from './gemini.mjs';
import {
  createMediaService,
  supabaseStorage,
  firestoreMediaRepository,
  mediaLimit
} from './media.mjs';

export function createAssistantServer({
  verifyToken,
  generate,
  media,
  push,
  health = {},
  store = new Store(':memory:')
}) {
  const limiter = new RateLimiter();
  const inflight = new Map();
  let active = 0;

  return createServer(
    { maxHeaderSize: 8192, requestTimeout: 600_000 },
    async (request, response) => {
      response.setHeader('Content-Type', 'application/json');
      response.setHeader('Cache-Control', 'no-store');
      response.setHeader('X-Content-Type-Options', 'nosniff');

      const reply = (status, body) => {
        response.writeHead(status);
        response.end(JSON.stringify(body));
      };

      try {
        if (request.method === 'GET' && request.url === '/health') {
          return reply(200, { status: 'ok', ...health });
        }

        if (
          request.url.startsWith('/v1/media/') ||
          request.url.startsWith('/v1/push/')
        ) {
          if (request.headers.origin) {
            throw new ApiError(400, 'App requests only');
          }

          const token = /^Bearer ([A-Za-z0-9_.-]{20,8192})$/.exec(
            request.headers.authorization ?? ''
          );

          let identity;
          try {
            identity = token && await verifyToken(token[1]);
          } catch {
            // Keep authentication errors generic.
          }

          if (!identity?.uid) {
            throw new ApiError(401, 'Sign in required');
          }

          if (request.url.startsWith('/v1/push/')) {
            if (!push) {
              throw new ApiError(
                503,
                'FCM test sender is not configured.'
              );
            }

            return reply(200, await push.handle(request, identity));
          }

          if (!media) {
            throw new ApiError(
              503,
              'Supabase media backend is not configured. Follow docs/SETUP.md.'
            );
          }

          return reply(200, await media.handle(request, identity, response));
        }

        if (request.method !== 'POST' || request.url !== '/v1/chat') {
          throw new ApiError(404, 'Not found');
        }

        if (
          request.headers.origin ||
          !request.headers['content-type']?.startsWith('application/json')
        ) {
          throw new ApiError(400, 'JSON app requests only');
        }

        const match = /^Bearer ([A-Za-z0-9_.-]{20,8192})$/.exec(
          request.headers.authorization ?? ''
        );

        if (!match) {
          throw new ApiError(401, 'Sign in required');
        }

        let identity;
        try {
          identity = await verifyToken(match[1]);
        } catch {
          throw new ApiError(401, 'Sign in required');
        }

        if (!identity?.uid) {
          throw new ApiError(401, 'Sign in required');
        }

        const chunks = [];
        let length = 0;

        for await (const chunk of request) {
          length += chunk.length;

          if (length > 98_304) {
            throw new ApiError(413, 'Request is too large');
          }

          chunks.push(chunk);
        }

        let body;
        try {
          body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
        } catch {
          throw new ApiError(400, 'Invalid JSON');
        }

        const chat = validateChat(body);
        const cached = store.cached(identity.uid, chat.requestId);

        if (cached) {
          if (cached.hash !== chat.hash) {
            throw new ApiError(
              409,
              'Request ID was already used for different content'
            );
          }

          return reply(200, { reply: cached.reply });
        }

        const key = identity.uid + ':' + chat.requestId;
        const existing = inflight.get(key);

        if (existing) {
          if (existing.hash !== chat.hash) {
            throw new ApiError(409, 'Request ID conflict');
          }

          return reply(200, { reply: await existing.promise });
        }

        limiter.take(identity.uid);

        if (active >= 2) {
          throw new ApiError(
            503,
            'AI is busy; retry shortly',
            { code: 'AI_BUSY', retryable: true, retryAfterSeconds: 2 }
          );
        }

        active++;

        const promise = (async () => {
          const text = await generate(chat.messages);

          if (
            typeof text !== 'string' ||
            !text.trim() ||
            text.length > 8000
          ) {
            throw new ApiError(503, 'AI returned an invalid reply');
          }

          store.save(identity.uid, chat.requestId, chat.hash, text);
          return text;
        })();

        inflight.set(key, { hash: chat.hash, promise });

        try {
          return reply(200, { reply: await promise });
        } finally {
          active--;
          inflight.delete(key);
        }
      } catch (error) {
        // Never return exception details, prompts, tokens,
        // credentials, or upstream responses.
        if (!response.headersSent) {
          const known = error instanceof ApiError;

          if (known && Number.isFinite(error.retryAfterSeconds)) {
            response.setHeader(
              'Retry-After',
              String(Math.ceil(error.retryAfterSeconds))
            );
          }

          reply(known ? error.status : 503, {
            error: known
              ? error.message
              : 'Backend unavailable. Check server configuration and retry.',
            code: known ? error.code : 'BACKEND_UNAVAILABLE',
            retryable: known ? error.retryable : true,
            ...(known && Number.isFinite(error.retryAfterSeconds)
              ? { retryAfterSeconds: error.retryAfterSeconds }
              : {}),
            ...(known && error.providerAttempts
              ? { providerAttempts: error.providerAttempts }
              : {})
          });
        }
      }
    }
  );
}

export async function ollamaReply(messages) {
  const base = new URL(
    process.env.OLLAMA_URL || 'http://127.0.0.1:11434'
  );

  if (
    !['127.0.0.1', 'localhost', '[::1]'].includes(base.hostname) ||
    base.protocol !== 'http:' ||
    base.username ||
    base.password
  ) {
    throw new ApiError(503, 'Ollama must run on this computer');
  }

  const system = (
    process.env.AI_SYSTEM_PROMPT ||
    'You are ChatSphere student assistant. Explain study concepts clearly, help organize coursework, and be concise. State uncertainty. Do not claim access to campus records or private chats.'
  ).slice(0, 4000);

  const result = await fetch(new URL('/api/chat', base), {
    method: 'POST',
    redirect: 'error',
    signal: AbortSignal.timeout(60_000),
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      model: process.env.OLLAMA_MODEL || 'qwen2.5:0.5b',
      stream: false,
      messages: [{ role: 'system', content: system }, ...messages],
      options: { num_predict: 512, num_ctx: 4096 },
      keep_alive: '5m'
    })
  });

  if (!result.ok) {
    throw new ApiError(
      503,
      'Local model unavailable. Install the configured Ollama model.'
    );
  }

  const body = await result.json();
  return body.message?.content;
}

if (
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href
) {
  const {
    auth,
    firestore,
    messaging,
    emulatorMode
  } = await import('./firebase.mjs');

  const {
    FieldValue,
    Timestamp
  } = await import('firebase-admin/firestore');

  // 0.0.0.0 allows devices on the local network to connect.
  // Use a trusted private Wi-Fi network and a restricted firewall rule.
  const host = process.env.HOST || '0.0.0.0';

  if (!['127.0.0.1', 'localhost', '::1', '0.0.0.0'].includes(host)) {
    throw new Error(
      'HOST must be loopback or 0.0.0.0 for local development.'
    );
  }

  const port = Number(process.env.PORT || 8787);

  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('Invalid PORT setting.');
  }

  const store = new Store(
    process.env.STATE_DATABASE || './runtime/state.sqlite3'
  );

  const provider = process.env.AI_PROVIDER || 'gemini';

  if (!['gemini', 'ollama'].includes(provider)) {
    throw new Error('AI_PROVIDER must be gemini or ollama');
  }

  const daily = Number(process.env.GEMINI_DAILY_REQUEST_LIMIT || 40);

  if (!Number.isInteger(daily) || daily < 1 || daily > 1000) {
    throw new Error('Invalid Gemini daily limit');
  }

  const generate = provider === 'ollama'
    ? ollamaReply
    : messages => geminiReply(messages, {
        beforeAttempt: () => {
          if (!store.takeProviderQuota(daily)) {
            throw new ApiError(
              429,
              'Backend daily AI request limit reached. Retry tomorrow.',
              { code: 'AI_DAILY_LIMIT' }
            );
          }
        }
      });

  const media =
    process.env.SUPABASE_URL &&
    process.env.SUPABASE_SERVICE_ROLE_KEY
      ? createMediaService({
          storage: supabaseStorage(),
          repository: firestoreMediaRepository(
            firestore,
            () => FieldValue.serverTimestamp()
          ),
          maxBytes: mediaLimit(process.env.MEDIA_MAX_BYTES)
        })
      : undefined;

  const push = emulatorMode
    ? undefined
    : createPushService({
        repository: firestorePushRepository(
          firestore,
          FieldValue,
          Timestamp
        ),
        messaging,
        store
      });

  const server = createAssistantServer({
    push,
    verifyToken: token => auth.verifyIdToken(token, true),
    generate,
    media,
    store,
    health: {
      aiProvider: provider,
      aiConfigured: provider === 'gemini'
        ? (
            !!process.env.GEMINI_API_KEY &&
            process.env.GEMINI_FREE_TIER_CONFIRMED === 'true'
          )
        : !!process.env.OLLAMA_MODEL,
      mediaConfigured: !!media,
      pushConfigured: !!push
    }
  });

  server.listen(port, host, () => {
    console.log(
      `ChatSphere backend listening on ${host}:${port}. ` +
      'Use only on a trusted private network; keep secrets server-side.'
    );
  });
}
