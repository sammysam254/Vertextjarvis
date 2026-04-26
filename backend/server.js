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
Speak with the formal dignity of White House serving staff.
Always address the user as Sir.
Be formal, precise and concise. Never casual.
Keep responses under 2 sentences. Be brief and direct.`;

const sessions = {};
const getSession = (id) => { if (!sessions[id]) sessions[id] = []; return sessions[id]; };
const addSession = (id, role, text) => {
  const s = getSession(id);
  s.push({ role, parts: [{ text }] });
  if (s.length > 10) s.splice(0, 2);
};

// PCM L16 to WAV converter
function pcmToWav(pcmBuffer, sampleRate = 24000) {
  const wav = Buffer.alloc(44 + pcmBuffer.length);
  wav.write('RIFF', 0);
  wav.writeUInt32LE(36 + pcmBuffer.length, 4);
  wav.write('WAVE', 8);
  wav.write('fmt ', 12);
  wav.writeUInt32LE(16, 16);
  wav.writeUInt16LE(1, 20);
  wav.writeUInt16LE(1, 22);
  wav.writeUInt32LE(sampleRate, 24);
  wav.writeUInt32LE(sampleRate * 2, 28);
  wav.writeUInt16LE(2, 32);
  wav.writeUInt16LE(16, 34);
  wav.write('data', 36);
  wav.writeUInt32LE(pcmBuffer.length, 40);
  pcmBuffer.copy(wav, 44);
  return wav;
}

async function geminiTTS(text, voice = 'Charon') {
  const r = await axios.post(
    `${BASE}/${TTS_MODEL}:generateContent?key=${GEMINI_KEY}`,
    {
      contents: [{ role: 'user', parts: [{ text }] }],
      generationConfig: {
        responseModalities: ['AUDIO'],
        speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } }
      }
    },
    { timeout: 25000 }
  );
  const part = r.data?.candidates?.[0]?.content?.parts?.[0];
  if (!part?.inlineData?.data) throw new Error('No audio returned');
  const mime = part.inlineData.mimeType || '';
  const raw = Buffer.from(part.inlineData.data, 'base64');
  if (mime.includes('L16') || mime.includes('pcm')) {
    const rate = (mime.match(/rate=(\d+)/) || [])[1] || 24000;
    return { buffer: pcmToWav(raw, parseInt(rate)), mime: 'audio/wav' };
  }
  return { buffer: raw, mime: mime || 'audio/wav' };
}

async function geminiAI(message, deviceId) {
  const history = getSession(deviceId);
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
  addSession(deviceId, 'user', message);
  addSession(deviceId, 'model', text);
  return text;
}


// Pre-cached responses for instant playback
const audioCache = new Map();

async function getCachedTTS(text, voice = 'Charon') {
  const key = text + voice;
  if (audioCache.has(key)) {
    console.log('[CACHE] Hit:', text.substring(0, 40));
    return audioCache.get(key);
  }
  const result = await geminiTTS(text, voice);
  audioCache.set(key, result);
  console.log('[CACHE] Stored:', text.substring(0, 40));
  return result;
}

// Pre-warm cache on startup with common responses
async function warmCache() {
  const phrases = [
    'Yes Sir.',
    'At your service Sir.',
    'Right away Sir.',
    'Consider it done Sir.',
    'One moment Sir.',
    'Of course Sir.',
    'Understood Sir.',
    'Very well Sir.',
    'Good morning Sir. Jarvis is online and at your service.',
    'Good afternoon Sir. Jarvis is online and at your service.',
    'Good evening Sir. Jarvis is online and at your service.',
    'How may I assist you Sir.',
    'I am listening Sir.'
  ];
  console.log('[CACHE] Warming up', phrases.length, 'phrases...');
  for (const phrase of phrases) {
    try {
      await getCachedTTS(phrase);
      await new Promise(r => setTimeout(r, 500)); // avoid rate limit
    } catch (e) {
      console.log('[CACHE] Failed:', phrase, e.message);
    }
  }
  console.log('[CACHE] Warm-up complete');
}

// Start warming after 2 seconds
setTimeout(warmCache, 2000);

app.get('/', (req, res) => {
  res.json({ status: 'online', service: 'J.A.R.V.I.S', key_set: !!GEMINI_KEY });
});

// Fast TTS only
app.post('/voice', async (req, res) => {
  try {
    const { text, voice = 'Charon' } = req.body;
    if (!text) return res.status(400).json({ error: 'text required' });
    console.log(`[TTS] "${text.substring(0, 50)}"`);
    const { buffer, mime } = await getCachedTTS(text, voice);
    console.log(`[TTS] OK bytes=${buffer.length}`);
    res.json({ audio: buffer.toString('base64'), mimeType: mime });
  } catch (e) {
    console.error('[TTS]', e.response?.data?.error?.message || e.message);
    res.status(500).json({ error: e.message, audio: '' });
  }
});

// Fast AI only
app.post('/ai', async (req, res) => {
  try {
    const { message, deviceId = 'default' } = req.body;
    if (!message) return res.json({ text: 'Message required Sir.' });
    console.log(`[AI] "${message.substring(0, 60)}"`);
    const text = await geminiAI(message, deviceId);
    console.log(`[AI] -> "${text.substring(0, 60)}"`);
    res.json({ text });
  } catch (e) {
    console.error('[AI]', e.message);
    res.json({ text: 'I encountered a difficulty Sir.' });
  }
});

// FAST SPEAK: AI and TTS run in PARALLEL for speed
app.post('/speak', async (req, res) => {
  try {
    const { message, deviceId = 'default', voice = 'Charon' } = req.body;
    if (!message) return res.json({ text: 'Message required.', audio: '' });
    console.log(`[SPEAK] "${message.substring(0, 60)}"`);
    const start = Date.now();

    // Step 1: Get AI text first (needed for TTS input)
    const aiText = await geminiAI(message, deviceId);
    console.log(`[SPEAK] AI done in ${Date.now()-start}ms: "${aiText.substring(0,60)}"`);

    // Step 2: TTS on AI text
    try {
      const { buffer, mime } = await geminiTTS(aiText, voice);
      console.log(`[SPEAK] Total ${Date.now()-start}ms bytes=${buffer.length}`);
      res.json({ text: aiText, audio: buffer.toString('base64'), mimeType: mime });
    } catch (ttsErr) {
      console.error('[SPEAK] TTS failed:', ttsErr.message);
      res.json({ text: aiText, audio: '' });
    }
  } catch (e) {
    console.error('[SPEAK]', e.message);
    res.json({ text: 'I encountered a difficulty Sir.', audio: '' });
  }
});

app.delete('/session/:id', (req, res) => {
  delete sessions[req.params.id];
  res.json({ message: 'Session cleared Sir.' });
});


// Keep Render awake — ping self every 14 minutes
const https = require('https');
setInterval(() => {
  const url = process.env.RENDER_EXTERNAL_URL || 'https://vertextjarvis.onrender.com';
  https.get(url, (r) => console.log('[PING] awake, status=' + r.statusCode))
       .on('error', (e) => console.log('[PING] error:', e.message));
}, 14 * 60 * 1000);

app.listen(PORT, () => {
  console.log(`J.A.R.V.I.S Backend port=${PORT} key=${GEMINI_KEY ? 'SET' : 'MISSING'}`);
});

// Keep-alive ping every 14 minutes to prevent Render sleep
setInterval(() => {
  const http = require('http');
  const https = require('https');
  const url = process.env.RENDER_EXTERNAL_URL || 'https://vertextjarvis.onrender.com';
  https.get(url + '/', (res) => {
    console.log('[PING] Keep-alive:', res.statusCode);
  }).on('error', (e) => console.log('[PING] Error:', e.message));
}, 14 * 60 * 1000);
