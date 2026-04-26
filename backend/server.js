const express = require('express');
const cors = require('cors');
const axios = require('axios');

const app = express();
app.use(cors());
app.use(express.json({ limit: '10mb' }));

const PORT = process.env.PORT || 3000;
const GEMINI_KEY = process.env.GEMINI_KEY || '';
const BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

const SYSTEM_PROMPT = 'You are J.A.R.V.I.S, a sophisticated personal AI assistant. Speak with the formal dignity of White House serving staff. Always address the user as Sir. Be formal, precise and concise. Keep responses under 3 sentences.';

const sessions = {};
const getSession = (id) => { if (!sessions[id]) sessions[id] = []; return sessions[id]; };
const addSession = (id, role, text) => {
  const s = getSession(id);
  s.push({ role, parts: [{ text }] });
  if (s.length > 20) s.splice(0, 2);
};

app.get('/', (req, res) => {
  res.json({ status: 'online', service: 'J.A.R.V.I.S', key_set: !!GEMINI_KEY });
});

app.post('/ai', async (req, res) => {
  try {
    const { message, deviceId = 'default' } = req.body;
    if (!message) return res.status(400).json({ text: 'message required' });
    if (!GEMINI_KEY) return res.status(500).json({ text: 'API key not configured Sir.' });

    console.log('[AI]', message.substring(0, 60));
    const history = getSession(deviceId);
    const r = await axios.post(
      `${BASE}/gemini-2.5-flash:generateContent?key=${GEMINI_KEY}`,
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
    console.log('[AI] ->', text.substring(0, 60));
    res.json({ text });
  } catch (e) {
    console.error('[AI] Error:', e.response?.data?.error?.message || e.message);
    res.json({ text: 'I encountered a difficulty Sir. Please try again.' });
  }
});

app.post('/voice', async (req, res) => {
  try {
    const { text, voice = 'Charon' } = req.body;
    if (!text) return res.status(400).json({ error: 'text required' });
    if (!GEMINI_KEY) return res.status(500).json({ error: 'API key not configured' });

    console.log('[TTS]', text.substring(0, 60));
    const models = ['gemini-2.5-flash-preview-tts', 'gemini-2.5-pro-preview-tts'];

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
          { timeout: 30000 }
        );
        const part = r.data?.candidates?.[0]?.content?.parts?.[0];
        if (part?.inlineData?.data) {
          const bytes = Buffer.from(part.inlineData.data, 'base64').length;
          console.log(`[TTS] OK model=${model} bytes=${bytes}`);
          return res.json({ audio: part.inlineData.data, mimeType: part.inlineData.mimeType || 'audio/wav', model });
        }
      } catch (me) {
        console.log(`[TTS] ${model} failed:`, me.response?.data?.error?.message || me.message);
      }
    }
    res.status(500).json({ error: 'All TTS models failed', audio: '' });
  } catch (e) {
    console.error('[TTS] Error:', e.message);
    res.status(500).json({ error: e.message, audio: '' });
  }
});

app.post('/speak', async (req, res) => {
  try {
    const { message, deviceId = 'default', voice = 'Charon' } = req.body;
    if (!message) return res.status(400).json({ text: 'message required', audio: '' });
    if (!GEMINI_KEY) return res.status(500).json({ text: 'API key not configured Sir.', audio: '' });

    console.log('[SPEAK]', message.substring(0, 60));

    // AI
    const history = getSession(deviceId);
    const aiResp = await axios.post(
      `${BASE}/gemini-2.5-flash:generateContent?key=${GEMINI_KEY}`,
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
    console.log('[SPEAK] AI ->', aiText.substring(0, 60));

    // TTS
    const ttsModels = ['gemini-2.5-flash-preview-tts', 'gemini-2.5-pro-preview-tts'];
    for (const model of ttsModels) {
      try {
        const ttsResp = await axios.post(
          `${BASE}/${model}:generateContent?key=${GEMINI_KEY}`,
          {
            contents: [{ role: 'user', parts: [{ text: aiText }] }],
            generationConfig: {
              responseModalities: ['AUDIO'],
              speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } }
            }
          },
          { timeout: 30000 }
        );
        const part = ttsResp.data?.candidates?.[0]?.content?.parts?.[0];
        if (part?.inlineData?.data) {
          console.log(`[SPEAK] TTS OK model=${model}`);
          return res.json({ text: aiText, audio: part.inlineData.data, mimeType: part.inlineData.mimeType || 'audio/wav' });
        }
      } catch (te) {
        console.log(`[SPEAK] TTS ${model} failed:`, te.response?.data?.error?.message || te.message);
      }
    }

    // TTS failed — text only
    res.json({ text: aiText, audio: '', mimeType: '' });

  } catch (e) {
    console.error('[SPEAK] Error:', e.response?.data?.error?.message || e.message);
    res.json({ text: 'I encountered a difficulty Sir.', audio: '', error: e.message });
  }
});

app.delete('/session/:id', (req, res) => {
  delete sessions[req.params.id];
  res.json({ message: 'Session cleared Sir.' });
});

app.listen(PORT, () => {
  console.log(`J.A.R.V.I.S Backend on port ${PORT} | key=${GEMINI_KEY ? 'SET' : 'NOT SET'}`);
});
