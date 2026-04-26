require('dotenv').config();
const express = require('express');
const cors = require('cors');
const axios = require('axios');

const app = express();
app.use(cors());
app.use(express.json({ limit: '10mb' }));

const GEMINI_KEY = process.env.GEMINI_KEY || 'AIzaSyA4yzazTjmnOdOz2RITHqrCxBzKZDlR7B8';
const PORT = process.env.PORT || 3000;
const GEMINI_BASE = 'https://generativelanguage.googleapis.com/v1beta/models';

const SYSTEM_PROMPT = `You are J.A.R.V.I.S — a sophisticated personal AI assistant.
Speak with the formal dignity of White House serving staff.
Always address the user as Sir.
Be formal, precise, concise and warmly deferential. Never casual.
Keep responses under 3 sentences unless more detail is truly needed.
Examples: Yes Sir right away. At your service Sir. Consider it done Sir.
You run on Android and assist with any question or task asked.`;

const sessions = {};

function getSession(id) {
  if (!sessions[id]) sessions[id] = [];
  return sessions[id];
}

function addToSession(id, role, text) {
  const s = getSession(id);
  s.push({ role, parts: [{ text }] });
  if (s.length > 20) s.splice(0, 2);
}

// Health check
app.get('/', (req, res) => {
  res.json({ status: 'online', service: 'J.A.R.V.I.S Backend', message: 'At your service Sir.' });
});

// AI only
app.post('/ai', async (req, res) => {
  try {
    const { message, deviceId = 'default' } = req.body;
    if (!message) return res.status(400).json({ error: 'message required' });
    console.log(`[AI] ${deviceId}: ${message}`);

    const history = getSession(deviceId);
    const response = await axios.post(
      `${GEMINI_BASE}/gemini-2.5-flash:generateContent?key=${GEMINI_KEY}`,
      {
        system_instruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents: [...history, { role: 'user', parts: [{ text: message }] }],
        generationConfig: { maxOutputTokens: 300, temperature: 0.7 }
      },
      { headers: { 'Content-Type': 'application/json' }, timeout: 30000 }
    );

    const text = response.data.candidates[0].content.parts[0].text;
    addToSession(deviceId, 'user', message);
    addToSession(deviceId, 'model', text);
    console.log(`[AI] Response: ${text.substring(0, 80)}`);
    res.json({ text });

  } catch (err) {
    console.error('[AI] Error:', err.response?.data || err.message);
    res.status(500).json({ text: 'I encountered a difficulty Sir. Please try again.' });
  }
});

// TTS only - returns base64 audio
app.post('/voice', async (req, res) => {
  try {
    const { text, voice = 'Charon' } = req.body;
    if (!text) return res.status(400).json({ error: 'text required' });
    console.log(`[TTS] ${voice}: ${text.substring(0, 60)}`);

    const response = await axios.post(
      `${GEMINI_BASE}/gemini-2.5-flash-preview-tts:generateContent?key=${GEMINI_KEY}`,
      {
        contents: [{ role: 'user', parts: [{ text }] }],
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } }
        }
      },
      { headers: { 'Content-Type': 'application/json' }, timeout: 30000 }
    );

    const inlineData = response.data.candidates[0].content.parts[0].inlineData;
    console.log(`[TTS] Audio bytes: ${Buffer.from(inlineData.data, 'base64').length}`);
    res.json({ audio: inlineData.data, mimeType: inlineData.mimeType || 'audio/wav' });

  } catch (err) {
    console.error('[TTS] Error:', err.response?.data || err.message);
    res.status(500).json({ error: err.response?.data?.error?.message || err.message });
  }
});

// Combined AI + TTS — single call returns text + audio
app.post('/speak', async (req, res) => {
  try {
    const { message, deviceId = 'default', voice = 'Charon' } = req.body;
    if (!message) return res.status(400).json({ error: 'message required' });
    console.log(`[SPEAK] ${deviceId}: ${message}`);

    // Step 1: AI
    const history = getSession(deviceId);
    const aiResp = await axios.post(
      `${GEMINI_BASE}/gemini-2.5-flash:generateContent?key=${GEMINI_KEY}`,
      {
        system_instruction: { parts: [{ text: SYSTEM_PROMPT }] },
        contents: [...history, { role: 'user', parts: [{ text: message }] }],
        generationConfig: { maxOutputTokens: 300, temperature: 0.7 }
      },
      { headers: { 'Content-Type': 'application/json' }, timeout: 30000 }
    );

    const aiText = aiResp.data.candidates[0].content.parts[0].text;
    addToSession(deviceId, 'user', message);
    addToSession(deviceId, 'model', aiText);
    console.log(`[SPEAK] AI: ${aiText.substring(0, 80)}`);

    // Step 2: TTS
    const ttsResp = await axios.post(
      `${GEMINI_BASE}/gemini-2.5-flash-preview-tts:generateContent?key=${GEMINI_KEY}`,
      {
        contents: [{ role: 'user', parts: [{ text: aiText }] }],
        generationConfig: {
          responseModalities: ['AUDIO'],
          speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName: voice } } }
        }
      },
      { headers: { 'Content-Type': 'application/json' }, timeout: 30000 }
    );

    const inlineData = ttsResp.data.candidates[0].content.parts[0].inlineData;
    res.json({ text: aiText, audio: inlineData.data, mimeType: inlineData.mimeType || 'audio/wav' });

  } catch (err) {
    console.error('[SPEAK] Error:', err.response?.data || err.message);
    res.status(500).json({
      text: 'I encountered a difficulty Sir. My apologies.',
      error: err.response?.data?.error?.message || err.message
    });
  }
});

// Delete session
app.delete('/session/:id', (req, res) => {
  delete sessions[req.params.id];
  res.json({ message: 'Session cleared Sir.' });
});

app.listen(PORT, () => {
  console.log(`J.A.R.V.I.S Backend running on port ${PORT}`);
});
