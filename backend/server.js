const express = require('express');
const cors = require('cors');
const axios = require('axios');

const app = express();
app.use(cors());
app.use(express.json({ limit: '50mb' }));

const PORT = process.env.PORT || 3000;
const GEMINI_KEY = process.env.GEMINI_KEY || '';
const BASE = 'https://generativelanguage.googleapis.com/v1beta/models';
const TTS_MODEL = 'gemini-2.5-flash-preview-tts';
const AI_MODEL = 'gemini-2.5-flash';

const SYSTEM_PROMPT = `You are J.A.R.V.I.S, a sophisticated personal AI assistant.
Always address the user as Sir. Be formal, precise and concise.
Keep responses under 2 sentences.`;

// Simple in-memory cache — avoids repeat TTS calls
const audioCache = new Map();
const MAX_CACHE = 50;

function cacheGet(key) { return audioCache.get(key); }
function cacheSet(key, val) {
  if (audioCache.size >= MAX_CACHE) {
    // Remove oldest
    audioCache.delete(audioCache.keys().next().value);
  }
  audioCache.set(key, val);
}

// PCM L16 → WAV
function pcmToWav(pcm, rate = 24000) {
  const wav = Buffer.alloc(44 + pcm.length);
  wav.write('RIFF', 0); wav.writeUInt32LE(36 + pcm.length, 4);
  wav.write('WAVE', 8); wav.write('fmt ', 12);
  wav.writeUInt32LE(16, 16); wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(1, 22); wav.writeUInt32LE(rate, 24);
  wav.writeUInt32LE(rate * 2, 28); wav.writeUInt16LE(2, 32);
  wav.writeUInt16LE(16, 34); wav.write('data', 36);
  wav.writeUInt32LE(pcm.length, 40); pcm.copy(wav, 44);
  return wav;
}

async function doTTS(text, voice = 'Charon') {
  const cacheKey = text + voice;
  const cached = cacheGet(cacheKey);
  if (cached) {
    console.log('[TTS] Cache hit:', text.substring(0, 40));
    return cached;
  }

  const r = await axios.post(
    `${BASE}/${TTS_MODEL}:generateContent?key=${GEMINI_KEY}`,
    {
      contents: [{ role: 'user', parts: [{ text }] }],
      generationConfig: {
        responseModalities: ['AUDIO'],
        speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } }
      }
    },
    { timeout: 30000 }
  );

  const part = r.data?.candidates?.[0]?.content?.parts?.[0];
  if (!part?.inlineData?.data) throw new Error('No audio in response');

  const mime = part.inlineData.mimeType || '';
  const raw = Buffer.from(part.inlineData.data, 'base64');
  const buf = (mime.includes('L16') || mime.includes('pcm'))
    ? pcmToWav(raw, parseInt((mime.match(/rate=(\d+)/) || [0, 24000])[1]))
    : raw;
  const finalMime = (mime.includes('L16') || mime.includes('pcm')) ? 'audio/wav' : mime;

  const result = { buffer: buf, mime: finalMime };
  cacheSet(cacheKey, result);
  console.log(`[TTS] Generated ${buf.length} bytes, cached`);
  return result;
}

async function doAI(message, deviceId, sessions) {
  if (!sessions[deviceId]) sessions[deviceId] = [];
  const history = sessions[deviceId];

  const r = await axios.post(
    `${BASE}/${AI_MODEL}:generateContent?key=${GEMINI_KEY}`,
    {
      system_instruction: { parts: [{ text: SYSTEM_PROMPT }] },
      contents: [...history, { role: 'user', parts: [{ text: message }] }],
      generationConfig: { maxOutputTokens: 150, temperature: 0.7 }
    },
    { timeout: 20000 }
  );

  const text = r.data.candidates[0].content.parts[0].text;
  history.push({ role: 'user', parts: [{ text: message }] });
  history.push({ role: 'model', parts: [{ text }] });
  if (history.length > 10) history.splice(0, 2);
  return text;
}

const sessions = {};

app.get('/', (req, res) => {
  res.json({
    status: 'online',
    service: 'J.A.R.V.I.S',
    key_set: !!GEMINI_KEY,
    cache_size: audioCache.size
  });
});

app.post('/voice', async (req, res) => {
  try {
    const { text, voice = 'Charon' } = req.body;
    if (!text) return res.status(400).json({ error: 'text required' });
    if (!GEMINI_KEY) return res.status(500).json({ error: 'No API key', audio: '' });

    console.log(`[TTS] "${text.substring(0, 60)}"`);
    const { buffer, mime } = await doTTS(text, voice);
    res.json({ audio: buffer.toString('base64'), mimeType: mime, bytes: buffer.length });

  } catch (e) {
    const msg = e.response?.data?.error?.message || e.message;
    const status = e.response?.status || 500;
    console.error('[TTS] Error:', msg);

    // Tell client how long to wait if rate limited
    let retryAfter = 0;
    if (status === 429) {
      const match = msg.match(/retry in (\d+)/i);
      retryAfter = match ? parseInt(match[1]) : 60;
    }

    res.status(status).json({ error: msg, audio: '', retryAfter });
  }
});

app.post('/ai', async (req, res) => {
  try {
    const { message, deviceId = 'default' } = req.body;
    if (!message) return res.json({ text: 'Message required Sir.' });
    if (!GEMINI_KEY) return res.json({ text: 'API key not configured Sir.' });

    console.log(`[AI] "${message.substring(0, 80)}"`);
    const text = await doAI(message, deviceId, sessions);
    console.log(`[AI] -> "${text.substring(0, 80)}"`);
    res.json({ text });

  } catch (e) {
    console.error('[AI]', e.response?.data?.error?.message || e.message);
    res.json({ text: 'I encountered a difficulty Sir. Please try again.' });
  }
});

app.post('/speak', async (req, res) => {
  try {
    const { message, deviceId = 'default', voice = 'Charon' } = req.body;
    if (!message) return res.json({ text: 'Message required.', audio: '' });
    if (!GEMINI_KEY) return res.json({ text: 'API key not configured Sir.', audio: '' });

    console.log(`[SPEAK] "${message.substring(0, 80)}"`);

    // Run AI first
    const aiText = await doAI(message, deviceId, sessions);
    console.log(`[SPEAK] AI -> "${aiText.substring(0, 80)}"`);

    // Then TTS
    try {
      const { buffer, mime } = await doTTS(aiText, voice);
      res.json({ text: aiText, audio: buffer.toString('base64'), mimeType: mime });
    } catch (ttsErr) {
      const msg = ttsErr.response?.data?.error?.message || ttsErr.message;
      console.error('[SPEAK] TTS failed:', msg);
      // Return text only — client handles gracefully
      res.json({ text: aiText, audio: '', ttsError: msg });
    }

  } catch (e) {
    console.error('[SPEAK]', e.message);
    res.json({ text: 'I encountered a difficulty Sir.', audio: '' });
  }
});

app.post('/debug-tts', async (req, res) => {
  const { text = 'Hello Sir', voice = 'Charon' } = req.body;
  const models = ['gemini-2.5-flash-preview-tts', 'gemini-2.5-pro-preview-tts'];
  const results = {};

  for (const model of models) {
    try {
      const r = await axios.post(
        `${BASE}/${model}:generateContent?key=${GEMINI_KEY}`,
        {
          contents: [{ role: 'user', parts: [{ text }] }],
          generationConfig: {
            responseModalities: ['AUDIO'],
            speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } }
          }
        },
        { timeout: 20000 }
      );
      const part = r.data?.candidates?.[0]?.content?.parts?.[0];
      results[model] = {
        success: !!part?.inlineData?.data,
        bytes: part?.inlineData?.data
          ? Buffer.from(part.inlineData.data, 'base64').length : 0,
        mime: part?.inlineData?.mimeType || 'none'
      };
    } catch (e) {
      results[model] = {
        success: false,
        error: e.response?.data?.error?.message || e.message,
        status: e.response?.status
      };
    }
  }
  res.json(results);
});

app.delete('/session/:id', (req, res) => {
  delete sessions[req.params.id];
  res.json({ message: 'Session cleared Sir.' });
});

// Keep Render awake
const https = require('https');
setInterval(() => {
  const url = process.env.RENDER_EXTERNAL_URL || 'https://vertextjarvis.onrender.com';
  https.get(url, r => console.log('[PING] awake status=' + r.statusCode))
       .on('error', e => console.log('[PING] error:', e.message));
}, 14 * 60 * 1000);

app.listen(PORT, () => {
  console.log(`J.A.R.V.I.S Backend port=${PORT} key=${GEMINI_KEY ? 'SET' : 'MISSING'}`);
});
