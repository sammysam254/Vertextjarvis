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
Keep responses under 3 sentences unless more detail is truly needed.`;

const sessions = {};
const getSession = (id) => { if (!sessions[id]) sessions[id] = []; return sessions[id]; };
const addSession = (id, role, text) => {
  const s = getSession(id);
  s.push({ role, parts: [{ text }] });
  if (s.length > 20) s.splice(0, 2);
};

// Convert raw PCM L16 to WAV buffer
function pcmToWav(pcmBuffer, sampleRate = 24000, channels = 1, bitDepth = 16) {
  const dataSize = pcmBuffer.length;
  const headerSize = 44;
  const wav = Buffer.alloc(headerSize + dataSize);

  // RIFF header
  wav.write('RIFF', 0);
  wav.writeUInt32LE(36 + dataSize, 4);
  wav.write('WAVE', 8);

  // fmt chunk
  wav.write('fmt ', 12);
  wav.writeUInt32LE(16, 16);           // chunk size
  wav.writeUInt16LE(1, 20);            // PCM format
  wav.writeUInt16LE(channels, 22);     // channels
  wav.writeUInt32LE(sampleRate, 24);   // sample rate
  wav.writeUInt32LE(sampleRate * channels * bitDepth / 8, 28); // byte rate
  wav.writeUInt16LE(channels * bitDepth / 8, 32); // block align
  wav.writeUInt16LE(bitDepth, 34);     // bits per sample

  // data chunk
  wav.write('data', 36);
  wav.writeUInt32LE(dataSize, 40);
  pcmBuffer.copy(wav, 44);

  return wav;
}

app.get('/', (req, res) => {
  res.json({ status: 'online', service: 'J.A.R.V.I.S', key_set: !!GEMINI_KEY });
});

// TTS endpoint — returns WAV audio as base64
app.post('/voice', async (req, res) => {
  try {
    const { text, voice = 'Charon' } = req.body;
    if (!text) return res.status(400).json({ error: 'text required' });
    if (!GEMINI_KEY) return res.status(500).json({ error: 'No API key' });

    console.log(`[TTS] "${text.substring(0, 60)}"`);

    const r = await axios.post(
      `${BASE}/${TTS_MODEL}:generateContent?key=${GEMINI_KEY}`,
      {
        contents: [{ role: 'user', parts: [{ text }] }],
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: {
            voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } }
          }
        }
      },
      { timeout: 30000 }
    );

    const part = r.data?.candidates?.[0]?.content?.parts?.[0];
    if (!part?.inlineData?.data) {
      return res.status(500).json({ error: 'No audio returned' });
    }

    const mime = part.inlineData.mimeType || '';
    const rawBuffer = Buffer.from(part.inlineData.data, 'base64');
    console.log(`[TTS] Got ${rawBuffer.length} bytes mime=${mime}`);

    let finalBuffer;
    let finalMime;

    // If PCM — convert to WAV so Android MediaPlayer can play it
    if (mime.includes('L16') || mime.includes('pcm')) {
      // Parse sample rate from mime e.g. "audio/L16;codec=pcm;rate=24000"
      const rateMatch = mime.match(/rate=(\d+)/);
      const sampleRate = rateMatch ? parseInt(rateMatch[1]) : 24000;
      finalBuffer = pcmToWav(rawBuffer, sampleRate);
      finalMime = 'audio/wav';
      console.log(`[TTS] Converted PCM→WAV rate=${sampleRate} finalBytes=${finalBuffer.length}`);
    } else {
      finalBuffer = rawBuffer;
      finalMime = mime || 'audio/wav';
    }

    res.json({
      audio: finalBuffer.toString('base64'),
      mimeType: finalMime,
      model: TTS_MODEL,
      bytes: finalBuffer.length
    });

  } catch (e) {
    const err = e.response?.data?.error?.message || e.message;
    console.error('[TTS] Error:', err);
    res.status(500).json({ error: err, audio: '' });
  }
});

// AI endpoint
app.post('/ai', async (req, res) => {
  try {
    const { message, deviceId = 'default' } = req.body;
    if (!message) return res.json({ text: 'Message required Sir.' });
    if (!GEMINI_KEY) return res.json({ text: 'API key not configured Sir.' });
    console.log('[AI]', message.substring(0, 80));

    const history = getSession(deviceId);
    const r = await axios.post(
      `${BASE}/${AI_MODEL}:generateContent?key=${GEMINI_KEY}`,
      {
        system_instruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents: [...history, { role: 'user', parts: [{ text: message }] }],
        generationConfig: { maxOutputTokens: 300, temperature: 0.7 }
      },
      { timeout: 30000 }
    );

    const text = r.data.candidates[0].content.parts[0].text;
    addSession(deviceId, 'user', message);
    addSession(deviceId, 'model', text);
    console.log('[AI] ->', text.substring(0, 80));
    res.json({ text });

  } catch (e) {
    const err = e.response?.data?.error?.message || e.message;
    console.error('[AI] Error:', err);
    res.json({ text: 'I encountered a difficulty Sir. Please try again.' });
  }
});

// Combined AI + TTS — single call
app.post('/speak', async (req, res) => {
  try {
    const { message, deviceId = 'default', voice = 'Charon' } = req.body;
    if (!message) return res.json({ text: 'Message required Sir.', audio: '' });
    if (!GEMINI_KEY) return res.json({ text: 'API key not configured Sir.', audio: '' });
    console.log('[SPEAK]', message.substring(0, 80));

    // Step 1: AI
    const history = getSession(deviceId);
    const aiResp = await axios.post(
      `${BASE}/${AI_MODEL}:generateContent?key=${GEMINI_KEY}`,
      {
        system_instruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents: [...history, { role: 'user', parts: [{ text: message }] }],
        generationConfig: { maxOutputTokens: 300, temperature: 0.7 }
      },
      { timeout: 30000 }
    );

    const aiText = aiResp.data.candidates[0].content.parts[0].text;
    addSession(deviceId, 'user', message);
    addSession(deviceId, 'model', aiText);
    console.log('[SPEAK] AI ->', aiText.substring(0, 80));

    // Step 2: TTS
    const ttsResp = await axios.post(
      `${BASE}/${TTS_MODEL}:generateContent?key=${GEMINI_KEY}`,
      {
        contents: [{ role: 'user', parts: [{ text: aiText }] }],
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: {
            voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } }
          }
        }
      },
      { timeout: 30000 }
    );

    const part = ttsResp.data?.candidates?.[0]?.content?.parts?.[0];
    if (!part?.inlineData?.data) {
      return res.json({ text: aiText, audio: '' });
    }

    const mime = part.inlineData.mimeType || '';
    const rawBuffer = Buffer.from(part.inlineData.data, 'base64');

    let finalBuffer;
    let finalMime;

    if (mime.includes('L16') || mime.includes('pcm')) {
      const rateMatch = mime.match(/rate=(\d+)/);
      const sampleRate = rateMatch ? parseInt(rateMatch[1]) : 24000;
      finalBuffer = pcmToWav(rawBuffer, sampleRate);
      finalMime = 'audio/wav';
      console.log(`[SPEAK] PCM→WAV rate=${sampleRate} bytes=${finalBuffer.length}`);
    } else {
      finalBuffer = rawBuffer;
      finalMime = mime || 'audio/wav';
    }

    res.json({
      text: aiText,
      audio: finalBuffer.toString('base64'),
      mimeType: finalMime
    });

  } catch (e) {
    const err = e.response?.data?.error?.message || e.message;
    console.error('[SPEAK] Error:', err);
    res.json({ text: 'I encountered a difficulty Sir.', audio: '', error: err });
  }
});

app.delete('/session/:id', (req, res) => {
  delete sessions[req.params.id];
  res.json({ message: 'Session cleared Sir.' });
});

app.listen(PORT, () => {
  console.log(`J.A.R.V.I.S Backend on port ${PORT} | key=${GEMINI_KEY ? 'SET' : 'NOT SET'}`);
});
