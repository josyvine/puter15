/**
 * CryptoBot AI — Application Controller, Brokerage & UI Pipeline
 */

function exitBrowser() {
  if (window.AndroidInterface && typeof window.AndroidInterface.closeBrowserPortal === 'function') {
    window.AndroidInterface.closeBrowserPortal();
  } else {
    window.history.back();
  }
}

const ENDPOINTS = {
  gemini: {
    generateContent: (model) =>
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent`,
    streamGenerateContent: (model) =>
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:streamGenerateContent`,
    bidiGenerateContent: (model) =>
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:bidiGenerateContent`,
    listModels:
      'https://generativelanguage.googleapis.com/v1beta/models',
    embedContent: (model) =>
      `https://generativelanguage.googleapis.com/v1beta/models/${model}:embedContent`,
  }
};

const PAIRS_50 = [
  "BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT", "ADA/USDT", "DOGE/USDT", "SHIB/USDT", "DOT/USDT", "LINK/USDT",
  "MATIC/USDT", "LTC/USDT", "UNI/USDT", "AVAX/USDT", "ATOM/USDT", "ETC/USDT", "FIL/USDT", "LDO/USDT", "APT/USDT", "SUI/USDT",
  "OP/USDT", "ARB/USDT", "RNDR/USDT", "INJ/USDT", "NEAR/USDT", "FTM/USDT", "GRT/USDT", "PEPE/USDT", "WIF/USDT", "JUP/USDT",
  "PYTH/USDT", "STX/USDT", "ICP/USDT", "TIA/USDT", "IMX/USDT", "MKR/USDT", "RUNE/USDT", "GALA/USDT", "FET/USDT", "AGIX/USDT",
  "OCEAN/USDT", "JASMY/USDT", "WLD/USDT", "STRK/USDT", "ENS/USDT", "MINA/USDT", "PENDLE/USDT", "ONDO/USDT", "SEI/USDT", "BEAM/USDT"
];

function classifyGemini(id) {
  if (/live/i.test(id))   return 'live';
  if (/embed/i.test(id))  return 'embed';
  if (/vision/i.test(id)) return 'vision';
  return 'text';
}

function resolveEndpoint(modelId) {
  const t = classifyGemini(modelId);
  if (t === 'live')   return { url: ENDPOINTS.gemini.bidiGenerateContent(modelId),   proto: 'WebRTC / BidiStream' };
  if (t === 'embed')  return { url: ENDPOINTS.gemini.embedContent(modelId),           proto: 'REST' };
  return                     { url: ENDPOINTS.gemini.generateContent(modelId),        proto: 'REST' };
}

let activeModel    = null;
let activeKey      = null;
let conversationHistory = [];
let isLoading = false;

let paperBalance = parseFloat(localStorage.getItem('paper_balance')) || 10000.00;
let paperActivePositions = JSON.parse(localStorage.getItem('paper_positions')) || [];
let paperSettings = JSON.parse(localStorage.getItem('paper_settings')) || {
  masterEnabled: false,
  autoTradeMVDP: false,
  autoTradeSignals: false,
  defaultPositionSize: 1000.00,
  minConfidence: 85,
  riskPerTrade: 1.0
};
let tradeRejectionLog = JSON.parse(localStorage.getItem('trade_rejection_log')) || [];
let backtestCache = {};

function savePaperState() {
  localStorage.setItem('paper_balance', paperBalance.toFixed(8));
  localStorage.setItem('paper_positions', JSON.stringify(paperActivePositions));
  localStorage.setItem('paper_settings', JSON.stringify(paperSettings));
}

function formatPrice(val) {
  const num = parseFloat(val);
  if (isNaN(num)) return '0.00';
  if (num === 0) return '0.00';
  if (num < 0.00001) return num.toFixed(8);
  if (num < 0.001) return num.toFixed(6);
  if (num < 1.0) return num.toFixed(4);
  return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

async function binanceFetch(url) {
  let lastError = null;
  const fullUrl = url.startsWith('http') ? url : `https://api.binance.com${url}`;
  const targets = [
    fullUrl,
    `https://corsproxy.io/?` + encodeURIComponent(fullUrl),
    `https://api.allorigins.win/raw?url=` + encodeURIComponent(fullUrl)
  ];
  
  for (let target of targets) {
    try {
      const res = await fetch(target, { method: 'GET' });
      if (res.ok) {
        return await res.json();
      }
      lastError = new Error(`HTTP Error ${res.status}`);
    } catch (err) {
      lastError = err;
    }
  }
  throw lastError || new Error("Failed to fetch from Binance APIs");
}

const BinanceDataService = {
  async fetchCurrentPrice(symbol) {
    const s = symbol.replace('/', '').toUpperCase();
    return await binanceFetch(`/api/v3/ticker/price?symbol=${s}`);
  },
  async fetch24hTicker(symbol) {
    const s = symbol.replace('/', '').toUpperCase();
    return await binanceFetch(`/api/v3/ticker/24hr?symbol=${s}`);
  },
  async fetchKlines(symbol, timeframe, limit = 500) {
    const s = symbol.replace('/', '').toUpperCase();
    const intervalMap = {
      '1m': '1m', '3m': '3m', '5m': '5m', '15m': '15m', '30m': '30m',
      '1h': '1h', '2h': '2h', '4h': '4h', '6h': '6h', '8h': '8h',
      '12h': '12h', '1d': '1d', '3d': '3d', '1w': '1w'
    };
    const interval = intervalMap[timeframe] || '1h';
    return await binanceFetch(`/api/v3/klines?symbol=${s}&interval=${interval}&limit=${limit}`);
  },
  async fetchOrderBook(symbol, depth = 100) {
    const s = symbol.replace('/', '').toUpperCase();
    return await binanceFetch(`/api/v3/depth?symbol=${s}&limit=${depth}`);
  },
  async fetchRecentTrades(symbol, limit = 50) {
    const s = symbol.replace('/', '').toUpperCase();
    return await binanceFetch(`/api/v3/trades?symbol=${s}&limit=${limit}`);
  }
};

function toggleSidebar() {
  const sidebar = document.getElementById('sidebar');
  const overlay = document.getElementById('sidebarOverlay');
  sidebar.classList.toggle('open');
  overlay.classList.toggle('open');
}

function toggleKeyVisibility() {
  const el = document.getElementById('geminiKey');
  if (el.type === 'password') el.type = 'text';
  else el.type = 'password';
}

function triggerQuickAction(promptText) {
  toggleSidebar();
  sendQuick(promptText);
}

function updatePaperUIDisplays() {
  const displaySidebar = document.getElementById('paperBalanceDisplay');
  const displayGlobal = document.getElementById('paperBalanceDisplayGlobal');
  const countGlobal = document.getElementById('activePositionsCountGlobal');
  const pnlGlobal = document.getElementById('unrealizedPnlGlobal');
  
  if (displaySidebar) displaySidebar.textContent = `$${paperBalance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  if (displayGlobal) displayGlobal.textContent = `$${paperBalance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
  if (countGlobal) countGlobal.textContent = paperActivePositions.length.toString();
  
  let netUnrealized = 0;
  let totalCost = 0;
  paperActivePositions.forEach(pos => {
    netUnrealized += pos.unrealizedPnl;
    totalCost += pos.size;
  });
  
  if (pnlGlobal) {
    const pct = totalCost > 0 ? (netUnrealized / totalCost) * 100 : 0.00;
    const sign = netUnrealized >= 0 ? '+' : '';
    pnlGlobal.style.color = netUnrealized > 0 ? 'var(--green)' : (netUnrealized < 0 ? 'var(--red)' : 'var(--muted)');
    pnlGlobal.textContent = `${sign}$${netUnrealized.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })} (${sign}${pct.toFixed(2)}%)`;
  }
}

function togglePaperMaster() {
  const inputEl = document.getElementById('paperMasterToggle');
  paperSettings.masterEnabled = inputEl.checked;
  savePaperState();
  showToast(`Simulated Paper Engine ${paperSettings.masterEnabled ? 'ACTIVE' : 'DEACTIVATED'}`);
}

function togglePaperMVDP() {
  const inputEl = document.getElementById('paperMVDPToggle');
  paperSettings.autoTradeMVDP = inputEl.checked;
  savePaperState();
  showToast(`Auto-Trade MVDP Predictions ${paperSettings.autoTradeMVDP ? 'ENABLED' : 'DISABLED'}`);
}

function togglePaperSignals() {
  const inputEl = document.getElementById('paperSignalsToggle');
  paperSettings.autoTradeSignals = inputEl.checked;
  savePaperState();
  showToast(`Auto-Trade Chat Signals ${paperSettings.autoTradeSignals ? 'ENABLED' : 'DISABLED'}`);
}

function safelyParseResponse(text, isPredictive) {
  if (!text) {
    return { confidence: 50, risk: "MEDIUM", strength: "No details available.", weaknesses: "No anomalies detected.", probability: 50 };
  }

  let parsed = parseJSON(text);
  if (parsed) {
    return {
      confidence: parsed.confidence || parsed.probability || 50,
      risk: (parsed.risk || "MEDIUM").toUpperCase(),
      strength: parsed.strength || parsed.macroAnalysis || "No details provided.",
      weaknesses: parsed.weaknesses || parsed.groundingReason || "No anomalies detected.",
      probability: parsed.probability || parsed.confidence || 50
    };
  }

  let confidence = 50;
  let probability = 50;
  let risk = "MEDIUM";
  let strength = "No details provided.";
  let weaknesses = "No anomalies detected.";

  let confMatch = text.match(/(?:confidence|Confidence|CONFIDENCE)\s*[:*-]+\s*(\d+)/);
  if (confMatch) confidence = parseInt(confMatch[1]);

  let probMatch = text.match(/(?:probability|Probability|PROBABILITY)\s*[:*-]+\s*(\d+)/);
  if (probMatch) probability = parseInt(probMatch[1]);

  let riskMatch = text.match(/(?:risk|Risk|RISK)\s*[:*-]+\s*(\w+)/);
  if (riskMatch) {
    let r = riskMatch[1].toUpperCase();
    if (["LOW", "MEDIUM", "HIGH", "EXTREME"].includes(r)) risk = r;
  }

  let lines = text.split('\n');
  let currentSection = null;
  let strengthLines = [];
  let weaknessesLines = [];

  for (let line of lines) {
    let cleanLine = line.trim();
    if (/^[-*#\s]*strength/i.test(cleanLine)) {
      currentSection = 'strength';
      let content = cleanLine.replace(/^[-*#\s]*strength\s*[:*-]*/i, '').trim();
      if (content) strengthLines.push(content);
    } else if (/^[-*#\s]*weakness/i.test(cleanLine)) {
      currentSection = 'weaknesses';
      let content = cleanLine.replace(/^[-*#\s]*weakness(?:es)?\s*[:*-]*/i, '').trim();
      if (content) weaknessesLines.push(content);
    } else if (cleanLine) {
      if (currentSection === 'strength') strengthLines.push(cleanLine);
      else if (currentSection === 'weaknesses') weaknessesLines.push(cleanLine);
    }
  }

  if (strengthLines.length > 0) strength = strengthLines.join(' ');
  if (weaknessesLines.length > 0) weaknesses = weaknessesLines.join(' ');

  if (strength === "No details provided." && text.length > 10) {
    strength = text;
  }

  return { confidence, risk, strength, weaknesses, probability };
}

function updatePaperPosSize() {
  const inputEl = document.getElementById('paperPositionSize');
  let val = parseFloat(inputEl.value);
  if (isNaN(val) || val <= 0) {
    val = 1000.00;
    inputEl.value = val;
  }
  paperSettings.defaultPositionSize = val;
  savePaperState();
  showToast(`Simulated Order Sizing Set to $${val.toLocaleString()}`);
}

function updateRiskPerTrade() {
  const val = parseFloat(document.getElementById('riskPerTradeSelect').value);
  paperSettings.riskPerTrade = val;
  savePaperState();
  showToast(`Risk Per Trade set to ${val}% of wallet balance`);
}

function updateMinConfidence() {
  const val = parseInt(document.getElementById('minConfidenceSelect').value);
  paperSettings.minConfidence = val;
  savePaperState();
  showToast(`Minimum Required Confidence set to ${val}%`);
}

function resetPaperBalance() {
  paperBalance = 10000.00;
  paperActivePositions = [];
  savePaperState();
  updatePaperUIDisplays();
  renderActiveTradesTable();
  showToast('Virtual paper funds and position array have been reset.');
}

function openTradingWorkspace() {
  document.getElementById('tradingWorkspace').classList.add('open');
  updatePaperUIDisplays();
  renderActiveTradesTable();
  renderRejectionLogTable();
}

function closeTradingWorkspace() {
  document.getElementById('tradingWorkspace').classList.remove('open');
}

function renderActiveTradesTable() {
  const tbody = document.getElementById('activeTradesTableBody');
  if (!tbody) return;
  
  if (paperActivePositions.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="8" style="text-align: center; color: var(--muted); padding: 40px 0;">No active positions currently registered</td>
      </tr>
    `;
    return;
  }
  
  let html = '';
  paperActivePositions.forEach(pos => {
    const sideClass = pos.side === 'LONG' ? 'badge long' : 'badge short';
    const pnlColor = pos.unrealizedPnl > 0 ? 'var(--green)' : (pos.unrealizedPnl < 0 ? 'var(--red)' : 'var(--text)');
    const sign = pos.unrealizedPnl >= 0 ? '+' : '';
    
    html += `
      <tr id="pos-row-${pos.id}">
        <td>
          <strong style="color:var(--accent)">${esc(pos.asset)}</strong>
          <div style="font-size:9px;color:var(--muted);margin-top:2px;">Grade: ${esc(pos.grade || 'B')}</div>
        </td>
        <td><span class="${sideClass}">${esc(pos.side)}</span></td>
        <td>$${pos.size.toLocaleString('en-US', { minimumFractionDigits: 2 })}</td>
        <td>$${formatPrice(pos.entryPrice)}</td>
        <td>$${formatPrice(pos.markPrice)}</td>
        <td style="color:var(--muted)">SL: $${formatPrice(pos.stopLoss)}<br>TP: $${formatPrice(pos.takeProfit)}</td>
        <td style="color:${pnlColor};font-weight:bold">${sign}$${pos.unrealizedPnl.toLocaleString('en-US', { minimumFractionDigits: 2 })} (${sign}${pos.pnlPercent.toFixed(2)}%)</td>
        <td>
          <button class="btn btn-sm btn-accent" onclick="closePaperPosition('${pos.id}')" style="padding:4px 8px;font-size:10px">MARKET CLOSE</button>
        </td>
      </tr>
    `;
  });
  tbody.innerHTML = html;
}

function addSimulatedBrokerLog(message) {
  addMessage('assistant', `🎰 BROKERAGE SETTLEMENT MATRIX:\n${message}`);
}

function logTradeRejection(asset, reason, confidence) {
  const newLog = {
    timestamp: new Date().toLocaleTimeString(),
    asset: asset,
    reason: reason,
    confidence: confidence
  };
  tradeRejectionLog.unshift(newLog);
  if (tradeRejectionLog.length > 50) tradeRejectionLog.pop();
  localStorage.setItem('trade_rejection_log', JSON.stringify(tradeRejectionLog));
  renderRejectionLogTable();
}

function renderRejectionLogTable() {
  const tbody = document.getElementById('rejectionLogTableBody');
  if (!tbody) return;
  if (tradeRejectionLog.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="4" style="text-align: center; color: var(--muted); padding: 25px 0;">No rejected signals logged yet</td>
      </tr>
    `;
    return;
  }
  let html = '';
  tradeRejectionLog.forEach(log => {
    html += `
      <tr>
        <td><span style="color:var(--muted)">${esc(log.timestamp)}</span></td>
        <td><strong style="color:var(--accent)">${esc(log.asset)}</strong></td>
        <td><span style="color:var(--red); font-size:11px;">${esc(log.reason)}</span></td>
        <td><span class="badge neutral" style="padding: 2px 6px; font-size: 10px;">${log.confidence}%</span></td>
      </tr>
    `;
  });
  tbody.innerHTML = html;
}

function clearRejectionLog() {
  tradeRejectionLog = [];
  localStorage.setItem('trade_rejection_log', JSON.stringify(tradeRejectionLog));
  renderRejectionLogTable();
  showToast("Rejection telemetry logs cleared.");
}

function tryPaperTrade(pair, currentPrice, side, quantData) {
  if (!paperSettings.masterEnabled) return;

  let minConf = paperSettings.minConfidence || 85;
  if (quantData.confidence < minConf) {
    logTradeRejection(pair, `Confidence below threshold (Needs ${minConf}%, Got ${quantData.confidence}%)`, quantData.confidence);
    addSimulatedBrokerLog(`❌ Execution Filtered: Confidence ${quantData.confidence}% below requirement of ${minConf}%.`);
    return;
  }

  if (quantData.rejections.length > 0) {
    quantData.rejections.forEach(r => {
      logTradeRejection(pair, r, quantData.confidence);
    });
    addSimulatedBrokerLog(`❌ Execution Rejected by Safeguards:\n- ${quantData.rejections.join('\n- ')}`);
    return;
  }
  
  const exists = paperActivePositions.some(pos => pos.asset === pair);
  if (exists) {
    addSimulatedBrokerLog(`⚠ Position already open for ${pair}. Duplicate entries blocked.`);
    return;
  }
  
  let size = quantData.dynamicPosSize;

  if (paperBalance < size) {
    addSimulatedBrokerLog(`❌ Execution Failed: Insufficient simulated margin balance. Required: $${size.toFixed(2)}, Available: $${paperBalance.toFixed(2)}`);
    return;
  }
  
  const newPos = {
    id: 'pos_' + Date.now(),
    asset: pair,
    side: side,
    size: size,
    entryPrice: parseFloat(currentPrice),
    markPrice: parseFloat(currentPrice),
    stopLoss: quantData.calculatedSL,
    takeProfit: quantData.calculatedTP,
    unrealizedPnl: 0.00,
    pnlPercent: 0.00,
    grade: quantData.grade
  };
  
  paperBalance -= size;
  paperActivePositions.push(newPos);
  savePaperState();
  updatePaperUIDisplays();
  
  addSimulatedBrokerLog(`✅ [SIMULATION BROKERAGE] Market Executed [${quantData.grade} setups]: ${side} ${pair} | Entry: $${formatPrice(currentPrice)} | Dynamic Size: $${size.toFixed(2)} | SL: $${formatPrice(quantData.calculatedSL)} | TP: $${formatPrice(quantData.calculatedTP)}`);
}

function closePaperPosition(posId) {
  const index = paperActivePositions.findIndex(pos => pos.id === posId);
  if (index === -1) return;
  
  const pos = paperActivePositions[index];
  const finalPnl = pos.unrealizedPnl;
  const returnAmount = pos.size + finalPnl;
  
  paperBalance += returnAmount;
  paperActivePositions.splice(index, 1);
  
  savePaperState();
  updatePaperUIDisplays();
  renderActiveTradesTable();
  
  showToast(`Closed ${pos.asset} at $${formatPrice(pos.markPrice)}. Realized P&L: $${finalPnl.toFixed(2)}`);
  addSimulatedBrokerLog(`❌ Closed Position ${pos.side} ${pos.asset}. Final Realized P&L: $${finalPnl.toFixed(2)} (${pos.pnlPercent.toFixed(2)}%)`);
}

function updatePositionsMarkPrices(asset, currentPrice) {
  const priceNum = parseFloat(currentPrice);
  let updated = false;

  if (activePredictionParams && activePredictionParams.pair === asset) {
    updatePredictionChartPrice(priceNum);
  }
  
  paperActivePositions.forEach(pos => {
    if (pos.asset === asset) {
      pos.markPrice = priceNum;
      if (pos.side === 'LONG') {
        pos.unrealizedPnl = ((priceNum - pos.entryPrice) / pos.entryPrice) * pos.size;
      } else { 
        pos.unrealizedPnl = ((pos.entryPrice - priceNum) / pos.entryPrice) * pos.size;
      }
      pos.pnlPercent = (pos.unrealizedPnl / pos.size) * 100;
      updated = true;
      
      if (pos.side === 'LONG') {
        if (priceNum <= pos.stopLoss) {
          setTimeout(() => {
            closePaperPosition(pos.id);
            addSimulatedBrokerLog(`🚨 Stop Loss Triggered for LONG ${pos.asset} at $${formatPrice(priceNum)}`);
          }, 50);
        } else if (priceNum >= pos.takeProfit) {
          setTimeout(() => {
            closePaperPosition(pos.id);
            addSimulatedBrokerLog(`🎯 Take Profit Hit for LONG ${pos.asset} at $${formatPrice(priceNum)}`);
          }, 50);
        }
      } else { 
        if (priceNum >= pos.stopLoss) {
          setTimeout(() => {
            closePaperPosition(pos.id);
            addSimulatedBrokerLog(`🚨 Stop Loss Triggered for SHORT ${pos.asset} at $${formatPrice(priceNum)}`);
          }, 50);
        } else if (priceNum <= pos.takeProfit) {
          setTimeout(() => {
            closePaperPosition(pos.id);
            addSimulatedBrokerLog(`🎯 Take Profit Hit for SHORT ${pos.asset} at $${formatPrice(priceNum)}`);
          }, 50);
        }
      }
    }
  });
  
  if (updated) {
    savePaperState();
    updatePaperUIDisplays();
    renderActiveTradesTable();
  }
}

async function fetchGeminiModels() {
  const key = document.getElementById('geminiKey').value.trim();
  if (!key) { setFetchStatus('gemini', '⚠ Enter API key first', 'err'); return; }
  setFetchStatus('gemini', 'Querying database...', '');
  try {
    const res = await fetch(`${ENDPOINTS.gemini.listModels}?key=${key}&pageSize=100`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    const models = (data.models || []).filter(m =>
      m.supportedGenerationMethods?.some(x => /generate|embed/i.test(x))
    );
    populateSelect('geminiModelSelect', models.map(m => ({
      id: m.name.replace('models/', ''),
      label: m.displayName || m.name.replace('models/', ''),
      type: classifyGemini(m.name)
    })));
    setFetchStatus('gemini', `✅ ${models.length} models compiled`, 'ok');
  } catch(e) {
    setFetchStatus('gemini', `❌ ${e.message}`, 'err');
  }
}

function populateSelect(selectId, models) {
  const sel = document.getElementById(selectId);
  sel.innerHTML = '';
  const groups = {};
  models.forEach(m => {
    (groups[m.type] = groups[m.type] || []).push(m);
  });
  const order = ['text','live','vision','embed'];
  order.forEach(type => {
    if (!groups[type]) return;
    const og = document.createElement('optgroup');
    og.label = type.toUpperCase() + ' MODELS';
    groups[type].forEach(m => {
      const opt = document.createElement('option');
      opt.value = m.id;
      opt.textContent = m.label;
      og.appendChild(opt);
    });
    sel.appendChild(og);
  });
}

function saveAndActivate() {
  const key   = document.getElementById('geminiKey').value.trim();
  const model = document.getElementById('geminiModelSelect').value;
  if (!key)   { showToast('⚠ Enter an API key first'); return; }
  if (!model) { showToast('⚠ Fetch and select a model first'); return; }

  activeModel    = model;
  activeKey      = key;
  localStorage.setItem('key_gemini', key);
  localStorage.setItem('model_gemini', model);

  toggleSidebar();
  showToast(`✅ Operational: ${model}`);
  conversationHistory = [];
}

function setFetchStatus(provider, msg, cls) {
  const el = document.getElementById(`${provider}-fetch-status`);
  el.textContent = msg;
  el.className = 'fetch-status ' + cls;
}

function showToast(msg) {
  const t = document.createElement('div');
  t.textContent = msg;
  t.style.cssText = `position:fixed;bottom:24px;left:50%;transform:translateX(-50%);background:#0B0F19;border:1px solid #1F2937;border-radius:8px;padding:10px 20px;font-family:'Space Mono',monospace;font-size:12px;color:#F3F4F6;z-index:9999;box-shadow: 0 4px 20px rgba(0,0,0,0.5);animation:slideIn .3s ease`;
  document.body.appendChild(t);
  setTimeout(() => t.remove(), 2800);
}

function esc(s) {
  return String(s||'').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

const SYSTEM_PROMPT = `You are CryptoBot AI, acting exclusively as a trade validation layer.
All technical parameters, calculated pattern structures, and math equations have already been computed.
You do NOT detect patterns. You evaluate trade quality by comparing these figures against macroeconomic trends, ETF flows, and global market sentiment.

OPERATIONAL PARAMETERS:
- Format your response strictly as a single JSON object matching the requested schema. 
- Do not include conversational prefaces, pre-prose, or post-prose. You may wrap it in a markdown json code fence if required.

JSON SCHEMA:
{
  "confidence": 84,
  "risk": "LOW|MEDIUM|HIGH",
  "strength": "Detailed explanation of key microstructural supports and macro sentiment alignments.",
  "weaknesses": "Detailed explanation of opposing liquidity vectors or event risk.",
  "probability": 78
}`;

const CANDLE_PROMPT = `You are CryptoBot AI, acting as a validation layer for the Markovian Volatility-Delta Projection (MVDP) engine.
Your sole job is to cross-examine our computed technical indicators and transition probabilities against fundamental catalysts and announcements using Search Grounding.

OPERATIONAL PARAMETERS:
- Format your response strictly as a single JSON object matching the requested schema. 
- Do not include conversational prefaces, pre-prose, or post-prose. You may wrap it in a markdown json code fence if required.

JSON SCHEMA:
{
  "confidence": 84,
  "risk": "LOW|MEDIUM|HIGH",
  "strength": "Contextual projection validation leveraging search grounding on sentiment vectors.",
  "weaknesses": "External fundamental events corroborating or opposing the transition vectors.",
  "probability": 78
}`;

async function callGemini(pair, timeframe, quantData, isPredictive) {
  const rsi = quantData.ta.rsi;
  const adx = quantData.ta.adx;
  const atr = quantData.ta.atr;
  const nearestSupport = quantData.sr.nearestSupport;
  const nearestResistance = quantData.sr.nearestResistance;
  const currentPrice = parseFloat(quantData.currentPrice);
  const sl = quantData.calculatedSL;
  const tp = quantData.calculatedTP;
  const rrr = quantData.riskRewardRatio;

  let isDataValid = true;
  let validationReason = "";

  if (quantData.consensusDirection === "NO TRADE") {
    if (rsi < 0 || rsi > 100 || isNaN(rsi)) { isDataValid = false; validationReason = "RSI out of bounds (0-100)"; }
    else if (adx < 0 || adx > 100 || isNaN(adx)) { isDataValid = false; validationReason = "ADX out of bounds (0-100)"; }
    else if (atr <= 0 || isNaN(atr)) { isDataValid = false; validationReason = "ATR is non-positive"; }
  } else {
    if (rsi < 0 || rsi > 100 || isNaN(rsi)) { isDataValid = false; validationReason = "RSI out of bounds (0-100)"; }
    else if (adx < 0 || adx > 100 || isNaN(adx)) { isDataValid = false; validationReason = "ADX out of bounds (0-100)"; }
    else if (atr <= 0 || isNaN(atr)) { isDataValid = false; validationReason = "ATR is non-positive"; }
    else if (quantData.consensusDirection === "LONG" && nearestSupport >= currentPrice) { isDataValid = false; validationReason = "Support is placed above price on LONG"; }
    else if (quantData.consensusDirection === "SHORT" && nearestResistance <= currentPrice) { isDataValid = false; validationReason = "Resistance is placed below price on SHORT"; }
    else if (isNaN(sl) || sl <= 0 || isNaN(tp) || tp <= 0) { isDataValid = false; validationReason = "Stop Loss or Take Profit calculation is invalid"; }
    else if (isNaN(rrr) || rrr <= 0) { isDataValid = false; validationReason = "Invalid Risk Reward Ratio"; }
  }

  if (!isDataValid) {
    quantData.validationFailed = true;
    quantData.validationErrorReason = validationReason;
    throw new Error("DATA VALIDATION FAILED: " + validationReason);
  }

  const prompt = isPredictive ? CANDLE_PROMPT : SYSTEM_PROMPT;
  const userPayload = `
[QUANTITATIVE_SIGNAL_VALIDATION_INPUT]
Pair: ${pair}
Timeframe: ${timeframe}
Current Price: $${formatPrice(quantData.currentPrice)}
Calculated Confidence: ${quantData.confidence}%
Trade Entry Filter: ${quantData.tradeFilter}

INDICATORS:
- RSI: ${quantData.ta.rsi}
- MACD Hist: ${quantData.ta.macdHist}
- ADX Volatility: ${quantData.ta.adx}
- Market Structure Trend: ${quantData.marketStructure.trend}
- Volume Spike: ${quantData.volConfirmation.volumeSpike}
- Order Flow Imbalance: ${quantData.orderFlowConf.imbalance}%
- Support/Resistance Validation Score: ${quantData.srValidationScore}/15
- Breakout Confirmed: ${quantData.breakoutValidation.breakout} (Dir: ${quantData.breakoutValidation.direction})
- Multi-Timeframe score: ${quantData.mtf.mtfScore}%

MARKOVIAN_PROJECTION:
- Current State: ${quantData.markov.currentState}
- Expected Direction Bias: ${quantData.markov.expectedDirection}

PATTERNS_DETECTED:
- Candlesticks: ${quantData.patterns.candlesticks.join(', ')}
- Chart Patterns: ${quantData.patterns.chartPatterns.join(', ')}
  `;

  conversationHistory.push({ role:'user', parts:[{ text: userPayload }] });
  
  const res = await fetch(`${ENDPOINTS.gemini.generateContent(activeModel)}?key=${activeKey}`, {
    method:'POST',
    headers:{'Content-Type':'application/json'},
    body: JSON.stringify({
      system_instruction:{ parts:[{ text: prompt }] },
      contents: conversationHistory,
      tools:[{ google_search:{} }], 
      generationConfig:{ 
        temperature:0.1, 
        maxOutputTokens:4096,
        responseMimeType: "text/plain"
      }
    })
  });
  if (!res.ok) { const e=await res.json().catch(()=>({})); throw new Error(e.error?.message||`Gemini ${res.status}`); }
  const data = await res.json();
  const text = (data.candidates?.[0]?.content?.parts||[]).map(p=>p.text||'').join('');
  conversationHistory.push({ role:'model', parts:[{ text }] });
  return text;
}

function displayQuantitativeResult(quantData, geminiParsed, pair, timeframe) {
  const directionColor = quantData.consensusDirection === 'LONG' ? 'var(--green)' : (quantData.consensusDirection === 'SHORT' ? 'var(--red)' : 'var(--amber)');
  const groundColor = geminiParsed.risk==='LOW' ? 'var(--green)' : geminiParsed.risk==='HIGH' ? 'var(--red)' : 'var(--amber)';
  const groundBorder = geminiParsed.risk==='LOW' ? 'rgba(16,185,129,0.3)' : geminiParsed.risk==='HIGH' ? 'rgba(239,68,68,0.3)' : 'rgba(245,158,11,0.3)';

  let confirmationDetails = "";
  if (quantData.confirmedPatterns && quantData.confirmedPatterns.length > 0) {
    confirmationDetails = quantData.confirmedPatterns.map(p => {
      let icon = p.confirmed ? "🟢 [Confirmed]" : "⚪ [Unconfirmed]";
      return `${p.name} ${icon}`;
    }).join(", ");
  } else {
    confirmationDetails = "None detected";
  }

  let breakdownHTML = '';
  if (quantData.confidenceBreakdown && quantData.confidenceBreakdown.length > 0) {
    breakdownHTML = quantData.confidenceBreakdown.map(b => {
      let color = b.value >= 0 ? 'var(--green)' : 'var(--red)';
      let sign = b.value >= 0 ? '+' : '';
      return `<div style="display:flex; justify-content:space-between; margin-bottom: 2px;">
        <span style="color:var(--muted);">${esc(b.name)}</span>
        <span style="color:${color}; font-weight:bold;">${sign}${b.value}</span>
      </div>`;
    }).join("");
  }

  let signalBreakdownPanelHTML = `
    <div style="background: rgba(0, 255, 204, 0.02); border: 1px solid var(--border); border-radius: 8px; padding: 12px; margin-bottom: 16px;">
      <div class="section-title" style="color: var(--accent); font-size: 10px;">📋 ENGINE CONSENSUS BREAKDOWN</div>
      <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 8px; font-family: var(--mono); font-size: 11px; margin-bottom: 8px; border-bottom: 1px solid var(--border); padding-bottom: 8px;">
        <div>Market Structure: <span style="color:var(--accent); font-weight:bold;">${quantData.marketStructureScore > 0 ? '+3' : (quantData.marketStructureScore < 0 ? '-3' : '0')}</span></div>
        <div>Trend Analysis: <span style="color:var(--accent); font-weight:bold;">${quantData.trendScore > 0 ? '+2' : (quantData.trendScore < 0 ? '-2' : '0')}</span></div>
        <div>Volume Score: <span style="color:var(--accent); font-weight:bold;">${quantData.volumeScore > 0 ? '+1' : (quantData.volumeScore < 0 ? '-1' : '0')}</span></div>
        <div>Pattern Score: <span style="color:var(--accent); font-weight:bold;">${quantData.patternScoreVal > 0 ? '+2' : (quantData.patternScoreVal < 0 ? '-2' : '0')}</span></div>
        <div>Order Flow: <span style="color:var(--accent); font-weight:bold;">${quantData.orderFlowScore > 0 ? '+2' : (quantData.orderFlowScore < 0 ? '-2' : '0')}</span></div>
        <div>Markov Engine: <span style="color:var(--accent); font-weight:bold;">${quantData.markovScore > 0 ? '+2' : (quantData.markovScore < 0 ? '-2' : '0')}</span></div>
        <div>Breakouts: <span style="color:var(--accent); font-weight:bold;">${quantData.breakoutScore > 0 ? '+2' : (quantData.breakoutScore < 0 ? '-2' : '0')}</span></div>
        <div>S/R Context: <span style="color:var(--accent); font-weight:bold;">${quantData.srScore > 0 ? '+1' : (quantData.srScore < 0 ? '-1' : '0')}</span></div>
      </div>
      
      <div class="section-title" style="color: var(--accent2); font-size: 9px; margin-bottom: 4px;">📈 DYNAMIC CONFIDENCE MODIFIERS</div>
      <div style="font-family: var(--mono); font-size: 10px; margin-bottom: 8px;">
        ${breakdownHTML}
      </div>

      <div style="display:flex; justify-content:space-between; font-family: var(--mono); font-size: 11px; border-top:1px solid var(--border); padding-top:6px;">
        <span>Consensus: <strong style="color:${directionColor}">${quantData.consensusMeter.percent}% ${quantData.consensusDirection}</strong></span>
        <span>Trade Quality: <strong style="color:var(--accent);">${quantData.tradeQuality}/100</strong></span>
        <span>Rating: <strong style="color: var(--accent2)">Grade ${quantData.grade}</strong></span>
      </div>
    </div>
  `;

  let rejectionHTML = '';
  if (quantData.rejections && quantData.rejections.length > 0) {
    const rejectionItems = quantData.rejections.map(r =>
      `<div style="display:flex;align-items:flex-start;gap:6px;margin-bottom:3px;">
        <span style="color:var(--red);flex-shrink:0;">✗</span>
        <span style="color:var(--red);font-size:10px;">${esc(r)}</span>
      </div>`
    ).join('');
    rejectionHTML = `
      <div style="background:rgba(239,68,68,0.05);border:1px solid rgba(239,68,68,0.2);border-radius:8px;padding:10px;margin-bottom:10px;">
        <div class="section-title" style="color:var(--red);font-size:9px;margin-bottom:6px;">🚫 ACTIVE REJECTION FILTERS</div>
        ${rejectionItems}
      </div>`;
  }

  let explanationHTML = `
    <div style="background: rgba(139,92,246,0.04); border: 1px solid rgba(139,92,246,0.2); border-radius: 8px; padding: 12px; margin-bottom: 16px;">
      <div class="section-title" style="color: var(--accent2)">📝 SIGNAL EXPLANATION REPORT</div>
      ${rejectionHTML}
      <p style="font-size: 12px; line-height: 1.6; color: var(--text); font-family: var(--mono)">
        [Consensus decision generated because]:<br>
        1. <strong>Patterns Checklist:</strong> ${confirmationDetails}<br>
        2. <strong>Structure:</strong> Sitting near support/resistance level with a validation score of ${quantData.srValidationScore}/15.<br>
        3. <strong>Volume Validation:</strong> Strength score of ${quantData.volConfirmation.volumeStrength} (Spike: ${quantData.volConfirmation.volumeSpike}).<br>
        4. <strong>Breakout state:</strong> Verified: ${quantData.breakoutValidation.breakout} (Direction: ${quantData.breakoutValidation.direction}).<br>
        5. <strong>Timeframes:</strong> MTF Alignment score of ${quantData.mtf.mtfScore}% (${quantData.mtf.alignedTimeframes} aligned).<br><br>
        <strong>Engine Action:</strong> ${quantData.tradeFilter} (Confidence Score: ${quantData.confidence}%)
      </p>
    </div>
  `;

  let html = `
    <div style="margin-bottom:18px">
      <div class="section-title">⚡ QUANTITATIVE METRICS CORE (CONSENSUS TELEMETRY)</div>
      <p style="font-size:13px;line-height:1.6;color:var(--text);font-family:var(--mono);">
        Asset Focus: <strong style="color:var(--accent)">${esc(pair)}</strong> at $${formatPrice(quantData.currentPrice)} <span class="badge" style="background:var(--accent2);color:#fff;margin-left:6px;">GRADE ${quantData.grade}</span><br>TF: ${esc(timeframe)}
      </p>
      <div style="margin-top:10px;display:flex;align-items:center;gap:10px">
        <span style="font-family:var(--mono);font-size:9px;color:var(--muted);letter-spacing:1px">CALCULATED DIRECTION BIAS</span>
        <span style="font-weight:700;color:${directionColor};font-family:var(--mono);font-size:13px">${esc(quantData.consensusDirection)}</span>
      </div>
    </div>

    ${signalBreakdownPanelHTML}
    ${explanationHTML}
  `;

  html += `
    <div style="background:var(--bg); border-left:4px solid ${groundColor}; border-color:${groundBorder}; border-radius:8px; padding:14px; margin-bottom:20px; box-shadow: 0 2px 10px rgba(0,0,0,0.15)">
      <div class="section-title" style="color:${groundColor}; margin-bottom:6px">🔮 GEMINI SEARCH VALIDATOR GATING (PHASE 13)</div>
      <div style="font-weight:700; font-family:var(--mono); font-size:11px; margin-bottom:8px; color:${groundColor}">
        RISK EVALUATION: ${esc(geminiParsed.risk)} | STRENGTH PROBABILITY: ${esc(geminiParsed.probability)}%
      </div>
      <p style="font-size:13px; line-height:1.6; color:var(--text); margin-bottom:8px"><strong>Strengths:</strong> ${esc(geminiParsed.strength)}</p>
      <p style="font-size:13px; line-height:1.6; color:var(--muted); border-top:1px solid var(--border); padding-top:8px; margin-top:8px"><strong>Weaknesses/Anomalies:</strong> ${esc(geminiParsed.weaknesses)}</p>
    </div>
  `;

  html += `<div class="signal-card">
    <div class="signal-header">
      <div class="coin-row">
        <span style="font-size:18px">₿</span>
        <strong style="color:var(--text);font-size:15px">${esc(pair)} Matrix</strong>
      </div>
      <div style="display:flex;align-items:center;gap:8px">
        <span class="badge ${quantData.consensusDirection === 'LONG'?'long':(quantData.consensusDirection === 'SHORT'?'short':'neutral')}">${esc(quantData.consensusDirection)}</span>
        <span style="font-size:11px;color:var(--muted)">${esc(timeframe)}</span>
      </div>
    </div>
    <div style="padding:14px">
      <div style="margin-bottom:12px">
        <div class="signal-label">PROBABILITY CONFLUENCE FACTOR</div>
        <div style="display:flex;align-items:center;gap:10px;margin-top:4px">
          <div class="progress-bar" style="flex:1"><div class="progress-fill" style="width:${quantData.confidence}%;background:var(--accent)"></div></div>
          <span style="font-family:var(--mono);font-size:13px;font-weight:700;color:var(--accent);min-width:36px">${quantData.confidence}%</span>
        </div>
      </div>
      
      <div class="signal-grid">
        ${cell('RSI Momentum', esc(quantData.ta.rsi) + " (Stoch K: "+quantData.ta.stochK+")")}
        ${cell('MACD Delta Hist', esc(quantData.ta.macdHist), quantData.ta.macdHist>0?'var(--green)':'var(--red)')}
        ${cell2('Order imbalance', esc(quantData.of.imbalance.toFixed(2)) + "%", quantData.of.imbalance>0?'var(--green)':'var(--red)')}
        ${cell('ATR Value 🧭', formatPrice(quantData.ta.atr), 'var(--accent)')}
        ${cell('Dynamic SL 🛡️', "$" + formatPrice(quantData.calculatedSL), 'var(--red)')}
        ${cell('Dynamic TP 🎯', "$" + formatPrice(quantData.calculatedTP), 'var(--green)')}
        ${cell('Stop Distance 📏', "$" + formatPrice(quantData.stopDistance))}
        ${cell('Risk Reward Ratio ⚖️', "1 : " + quantData.riskRewardRatio.toFixed(2), 'var(--accent)')}
        ${cell('Expected Loss 💸', "$" + quantData.potentialLoss.toLocaleString('en-US', {minimumFractionDigits:2, maximumFractionDigits:2}), 'var(--red)')}
        ${cell('Expected Profit 💰', "$" + quantData.potentialProfit.toLocaleString('en-US', {minimumFractionDigits:2, maximumFractionDigits:2}), 'var(--green)')}
        ${cell('Dynamic Size 🎰', "$" + quantData.dynamicPosSize.toLocaleString('en-US', {minimumFractionDigits:2, maximumFractionDigits:2}), 'var(--accent2)')}
        ${cell('ADX Volatility', esc(quantData.ta.adx))}
        ${cell2('Candlestick Patterns', quantData.patterns.candlesticks.join(', ') || 'None')}
        ${cell2('Chart Patterns', quantData.patterns.chartPatterns.join(', ') || 'None')}
        ${cell2('Sweeps Detected', quantData.sweeps.bias)}
        ${cell('Markov State (MVDP)', esc(quantData.markov.currentState))}
      </div>
    </div>
  </div>`;

  html += `
    <div style="background:rgba(0,255,204,0.03); border:1px solid var(--border); border-radius:12px; padding:14px; margin-top:16px;">
      <div class="section-title">📊 BACKTEST ENGINE HISTOGRAM (UNIFIED COMPILER)</div>
      ${quantData.backtest.totalTrades < 5 
        ? `<div style="font-family:var(--mono);font-size:11px;color:var(--muted);padding:8px 0">Insufficient historical trade simulations (${quantData.backtest.totalTrades} samples). Run on longer datasets for statistically valid results.</div>`
        : `<div class="signal-grid" style="margin-top:10px">
            ${cell('Unified Win Rate', esc(quantData.backtest.winRate) + '%', 'var(--green)')}
            ${cell('Profit Factor', esc(quantData.backtest.profitFactor), 'var(--accent)')}
            ${cell('Max Drawdown', esc(quantData.backtest.drawdown) + '%', 'var(--red)')}
            ${cell('Unified Accuracy', esc(quantData.backtest.accuracy) + '%')}
            ${cell('Total Compiled Trades', esc(quantData.backtest.totalTrades))}
            ${cell('Unified Grade Capability', "Grade " + quantData.grade)}
          </div>`
      }
    </div>
  `;

  return html;
}

function displayPredictiveCandles(quantData, geminiParsed, pair, timeframe, chartId) {
  const groundColor = geminiParsed.risk==='LOW' ? 'var(--green)' : geminiParsed.risk==='HIGH' ? 'var(--red)' : 'var(--amber)';
  const groundBorder = geminiParsed.risk==='LOW' ? 'rgba(16,185,129,0.3)' : geminiParsed.risk==='HIGH' ? 'rgba(239,68,68,0.3)' : 'rgba(245,158,11,0.3)';

  let html = `
    <div style="margin-bottom:12px">
      <div class="section-title">MARKOVIAN CANDLE VECTOR PROJECTION (MVDP)</div>
      <p style="font-size:13px;color:var(--text);font-family:var(--mono);">
        Asset Index Focus: <strong style="color:var(--accent)">${esc(pair)}</strong> at $${formatPrice(quantData.currentPrice)} // TF: ${esc(timeframe)}
      </p>
    </div>
    
    <div style="background:var(--bg); border-left:4px solid ${groundColor}; border-color:${groundBorder}; border-radius:8px; padding:12px; margin-bottom:14px;">
      <span class="section-title" style="color:${groundColor}; font-size:9px">🔮 GROUNDING CROSS-VALIDATION: ${esc(geminiParsed.risk)}</span>
      <p style="font-size:12px; line-height:1.5; color:var(--text); margin-top:4px"><strong>Strength Analysis:</strong> ${esc(geminiParsed.strength)}</p>
      <p style="font-size:11px; line-height:1.5; color:var(--muted); margin-top:4px"><strong>Risk Factors:</strong> ${esc(geminiParsed.weaknesses)}</p>
    </div>

    <div id="${chartId}" style="height: 250px; background: #0B0F19; border: 1px solid var(--border); border-radius: 12px; margin: 15px 0; overflow: hidden; width: 100%;"></div>

    <div class="signal-grid" style="margin-top:14px">
      ${cell('Current Markov State', esc(quantData.markov.currentState), 'var(--accent)')}
      ${cell('Transition expected direction', esc(quantData.markov.expectedDirection))}
      ${cell('Confidence Score', esc(quantData.confidence) + '%', 'var(--green)')}
      ${cell('Order Imbalance Variance', esc(quantData.of.imbalance.toFixed(2)) + '%')}
    </div>
  `;
  return html;
}

function cell(label, val, color) {
  return `<div class="signal-cell"><div class="signal-label">${label}</div><div class="signal-value"${color?` style="color:${color}"`:''}>${val}</div></div>`;
}

function cell2(label, val) {
  return `<div class="signal-cell"><div class="signal-label">${label}</div><div class="signal-value" style="font-size:11px">${val}</div></div>`;
}

function addMessage(role, content, isRaw=false) {
  const msgs = document.getElementById('messages');
  const div = document.createElement('div');
  div.className = 'msg';
  const isBot = role==='assistant';
  div.innerHTML = `<div class="msg-avatar ${isBot?'bot':'user'}">${isBot?'🤖':'👤'}</div>
    <div class="msg-bubble ${isBot?'':'user'}">${isRaw ? content : esc(content)}</div>`;
  msgs.appendChild(div);
  msgs.scrollTop = msgs.scrollHeight;
  return div.querySelector('.msg-bubble');
}

function addMessageTop(role, content) {
  const msgs = document.getElementById('messages');
  const div = document.createElement('div');
  div.className = 'msg';
  const isBot = role==='assistant';
  div.innerHTML = `<div class="msg-avatar ${isBot?'bot':'user'}">${isBot?'🤖':'👤'}</div>
    <div class="msg-bubble ${isBot?'':'user'}">${esc(content)}</div>`;
  msgs.insertBefore(div, msgs.firstChild);
}

function addThinking() {
  const msgs = document.getElementById('messages');
  const div = document.createElement('div');
  div.className='msg'; div.id='thinking';
  div.innerHTML=`<div class="msg-avatar bot">🤖</div>
    <div class="msg-bubble"><div class="thinking"><span>Synthesizing live matrix telemetry</span><div class="thinking-dots"><span></span><span></span><span></span></div></div></div>`;
  msgs.appendChild(div);
  msgs.scrollTop = msgs.scrollHeight;
}

function removeThinking() { const t=document.getElementById('thinking'); if(t) t.remove(); }

function showLoadingProgress(msg) {
  const thinkingEl = document.getElementById('thinking');
  if (thinkingEl) {
    const textSpan = thinkingEl.querySelector('.thinking span');
    if (textSpan) {
      textSpan.textContent = msg;
    }
  }
}

function parseJSON(text) {
  try {
    const clean = text.replace(/```json\n?/g,'').replace(/```\n?/g,'').trim();
    const m = clean.match(/\{[\s\S]*\}/);
    if (m) return JSON.parse(m[0]);
  } catch(e) {}
  return null;
}

function parseUserQuery(text) {
  let detectedPair = "BTC/USDT";
  let detectedTimeframe = "1h";
  const cleanText = text.toUpperCase();
  
  for (let pair of PAIRS_50) {
    const symbol = pair.replace('/', '');
    if (cleanText.includes(pair) || cleanText.includes(symbol)) {
      detectedPair = pair;
      break;
    }
  }
  
  const timeframes = ['1m', '3m', '5m', '15m', '30m', '1h', '2h', '4h', '1d'];
  for (let tf of timeframes) {
    const regex = new RegExp(`\\b${tf}\\b`, 'i');
    if (regex.test(text)) {
      detectedTimeframe = tf;
      break;
    }
  }
  return { pair: detectedPair, timeframe: detectedTimeframe };
}

async function sendMessage() {
  if (isLoading) return;
  const input = document.getElementById('userInput');
  const text = input.value.trim();
  if (!text) return;
  if (!activeKey || !activeModel) {
    showToast('⚠ Map core engine configuration first');
    toggleSidebar();
    return;
  }
  
  input.value='';
  isLoading=true;
  document.getElementById('sendBtn').disabled=true;
  addMessage('user', text);
  addThinking();

  try {
    const parsedParams = parseUserQuery(text);
    
    showLoadingProgress(`Fetching ${parsedParams.pair} raw data...`);
    const priceData = await BinanceDataService.fetchCurrentPrice(parsedParams.pair);
    const ticker24h = await BinanceDataService.fetch24hTicker(parsedParams.pair);
    const klines = await BinanceDataService.fetchKlines(parsedParams.pair, parsedParams.timeframe, 500);
    const depth = await BinanceDataService.fetchOrderBook(parsedParams.pair, 50);

    showLoadingProgress(`Fetching ${parsedParams.pair} higher timeframe data...`);
    const htfMap = { '1m':'15m','3m':'15m','5m':'1h','15m':'4h','30m':'4h','1h':'4h','2h':'4h','4h':'1d','1d':'1d' };
    const htfInterval = htfMap[parsedParams.timeframe] || '4h';
    let htfKlines = klines;
    let htfBullish = null;
    try {
      htfKlines = await BinanceDataService.fetchKlines(parsedParams.pair, htfInterval, 200);
      const htfCloses = htfKlines.map(k => parseFloat(k[4]));
      const htfRsiArr = getRSI(htfCloses, 14);
      const htfRsi = htfRsiArr[htfRsiArr.length - 1];
      const htfMacd = getMACD(htfCloses, 12, 26, 9);
      const htfMacdHist = htfMacd.histogram[htfMacd.histogram.length - 1] || 0;
      htfBullish = htfRsi > 50 && htfMacdHist > 0;
    } catch(e) {
      console.warn("Failed to retrieve HTF matrix", e);
    }

    showLoadingProgress(`Processing technical and mathematical matrices...`);
    const quantData = runQuantitativePipeline(klines, depth, false, htfBullish);
    quantData.currentPrice = priceData.price;
    quantData.ticker24h = ticker24h;

    backtestCache[parsedParams.pair] = quantData.backtest;

    updatePositionsMarkPrices(parsedParams.pair, priceData.price);

    showLoadingProgress("Consulting Gemini Search Grounding layers... (Failsafe Checked)");
    
    let responseText = "";
    try {
      responseText = await callGemini(parsedParams.pair, parsedParams.timeframe, quantData, false);
    } catch (validationErr) {
      removeThinking();
      addMessage('assistant', `❌ Data Validation Guard Triggered:\n- ${validationErr.message}\n\nGemini transmission blocked (Failsafe activated).`);
      isLoading = false;
      document.getElementById('sendBtn').disabled = false;
      return;
    }

    removeThinking();
    const parsedGemini = safelyParseResponse(responseText, false);
    
    if (parsedGemini) {
      const bubble = addMessage('assistant','',true);
      bubble.innerHTML = displayQuantitativeResult(quantData, parsedGemini, parsedParams.pair, parsedParams.timeframe);

      if (quantData.consensusDirection !== 'NO TRADE' && paperSettings.autoTradeSignals) {
        tryPaperTrade(parsedParams.pair, priceData.price, quantData.consensusDirection, quantData);
      }
    } else {
      addMessage('assistant', responseText || 'Telemetry formatting discrepancies detected during signal interpretation.');
    }
  } catch(e) {
    removeThinking();
    addMessage('assistant', `⚠ Telemetry Fault: ${e.message}\n\nVerify connection API key configurations.`);
  }
  isLoading=false;
  document.getElementById('sendBtn').disabled=false;
}

async function predictTwoCandles() {
  if (isLoading) return;
  const select = document.getElementById('candlePairSelect');
  const selectTimeframe = document.getElementById('candleTimeframeSelect');
  const pair = select.value;
  const timeframe = selectTimeframe.value;
  if (!activeKey || !activeModel) {
    showToast('⚠ Map core engine configuration first');
    toggleSidebar();
    return;
  }
  
  isLoading = true;
  document.getElementById('sendBtn').disabled = true;
  document.getElementById('predictBtn').disabled = true;
  toggleSidebar(); 
  addMessage('user', `Predict next two candles for ${pair} using Markovian Volatility-Delta Projection on the ${timeframe} timeframe.`);
  addThinking();
  
  try {
    showLoadingProgress(`Fetching ${pair} klines...`);
    const priceData = await BinanceDataService.fetchCurrentPrice(pair);
    const ticker24h = await BinanceDataService.fetch24hTicker(pair);
    const klines = await BinanceDataService.fetchKlines(pair, timeframe, 500);
    const depth = await BinanceDataService.fetchOrderBook(pair, 50);

    showLoadingProgress(`Fetching ${pair} higher timeframe data...`);
    const htfMap = { '1m':'15m','3m':'15m','5m':'1h','15m':'4h','30m':'4h','1h':'4h','2h':'4h','4h':'1d','1d':'1d' };
    const htfInterval = htfMap[timeframe] || '4h';
    let htfKlines = klines;
    let htfBullish = null;
    try {
      htfKlines = await BinanceDataService.fetchKlines(pair, htfInterval, 200);
      const htfCloses = htfKlines.map(k => parseFloat(k[4]));
      const htfRsiArr = getRSI(htfCloses, 14);
      const htfRsi = htfRsiArr[htfRsiArr.length - 1];
      const htfMacd = getMACD(htfCloses, 12, 26, 9);
      const htfMacdHist = htfMacd.histogram[htfMacd.histogram.length - 1] || 0;
      htfBullish = htfRsi > 50 && htfMacdHist > 0;
    } catch(e) {
      console.warn("Failed to retrieve HTF matrix for prediction", e);
    }

    showLoadingProgress(`Processing technical and mathematical matrices...`);
    const quantData = runQuantitativePipeline(klines, depth, false, htfBullish);
    quantData.currentPrice = priceData.price;
    quantData.ticker24h = ticker24h;

    backtestCache[pair] = quantData.backtest;

    updatePositionsMarkPrices(pair, priceData.price);

    showLoadingProgress("Consulting Gemini Search Grounding layers... (Failsafe Checked)");
    
    let responseText = "";
    try {
      responseText = await callGemini(pair, timeframe, quantData, true);
    } catch (validationErr) {
      removeThinking();
      addMessage('assistant', `❌ Data Validation Guard Triggered:\n- ${validationErr.message}\n\nGemini transmission blocked (Failsafe activated).`);
      isLoading = false;
      document.getElementById('sendBtn').disabled = false;
      document.getElementById('predictBtn').disabled = false;
      return;
    }
    
    removeThinking();
    const parsedGemini = safelyParseResponse(responseText, true);
    if (parsedGemini) {
      const chartId = 'predictionChart_' + Date.now() + '_' + Math.floor(Math.random() * 1000); 
      const bubble = addMessage('assistant','',true);
      bubble.innerHTML = displayPredictiveCandles(quantData, parsedGemini, pair, timeframe, chartId);

      setTimeout(() => {
        renderRealtimePredictionChart(chartId, klines, quantData, pair, timeframe);
      }, 50);

      const c1Bullish = quantData.markov.nextCandleProbability["STRONG_BULL"] + quantData.markov.nextCandleProbability["BULL"] > quantData.markov.nextCandleProbability["STRONG_BEAR"] + quantData.markov.nextCandleProbability["BEAR"];
      const side = c1Bullish ? 'LONG' : 'SHORT';
      if (paperSettings.autoTradeMVDP) {
        tryPaperTrade(pair, priceData.price, side, quantData);
      }
    } else {
      addMessage('assistant', responseText || 'Telemetry formatting discrepancies detected during signal interpretation.');
    }
  } catch(e) {
    removeThinking();
    addMessage('assistant', `⚠ Projection Fault: ${e.message}\n\nVerify key authorization and selected engine model status.`);
  }
  
  isLoading = false;
  document.getElementById('sendBtn').disabled = false;
  document.getElementById('predictBtn').disabled = false;
}

async function executeBulkStrategyScan(type) {
  if (isLoading) return;
  isLoading = true;
  document.getElementById('sendBtn').disabled = true;
  toggleSidebar();
  
  addMessage('user', `Execute Live Quantitative Market Scan: Strategy Type [${type}] across active Binance asset matrix.`);
  addThinking();
  showLoadingProgress("Executing live 24h ticker calculations...");

  try {
    const tickerData = await binanceFetch('/api/v3/ticker/24hr');
    
    const pairTickers = PAIRS_50.map(pair => {
      const symbol = pair.replace('/', '');
      const tick = tickerData.find(t => t.symbol === symbol);
      return { pair, tick };
    }).filter(item => item.tick !== undefined);

    let selectedPairs = [];

    if (type === 'BULLISH') {
      selectedPairs = pairTickers
        .sort((a, b) => parseFloat(b.tick.priceChangePercent) - parseFloat(a.tick.priceChangePercent))
        .slice(0, 5)
        .map(x => x.pair);
    } else if (type === 'SHORTS') {
      selectedPairs = pairTickers
        .sort((a, b) => parseFloat(a.tick.priceChangePercent) - parseFloat(b.tick.priceChangePercent))
        .slice(0, 5)
        .map(x => x.pair);
    } else if (type === 'SCALPER') {
      selectedPairs = pairTickers
        .map(item => {
          const high = parseFloat(item.tick.highPrice);
          const low = parseFloat(item.tick.lowPrice);
          const vola = low > 0 ? (high - low) / low : 0;
          return { pair: item.pair, vola };
        })
        .sort((a, b) => b.vola - a.vola)
        .slice(0, 5)
        .map(x => x.pair);
    } else if (type === 'MATRIX') {
      selectedPairs = ["BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT"];
    } else if (type === 'DEFI') {
      const defiPairs = ["UNI/USDT", "LDO/USDT", "MKR/USDT", "PENDLE/USDT", "ONDO/USDT"];
      selectedPairs = pairTickers
        .filter(item => defiPairs.includes(item.pair))
        .sort((a, b) => parseFloat(b.tick.volume) - parseFloat(a.tick.volume))
        .slice(0, 5)
        .map(x => x.pair);
    }

    addMessage('assistant', `🤖 QUANTUM DETECTOR ACTIVE: Mathematically selected top 5 ${type} targets based on real-time volatility, performance, and volume: [${selectedPairs.join(', ')}]. Calculating indicators...`);

    const consolidatedQuantData = [];
    const bulkHtfMap = { '1h': '4h', '4h': '1d', '1d': '1d' };
    const bulkHtfInterval = bulkHtfMap['1h'] || '4h';

    for (let i = 0; i < selectedPairs.length; i++) {
      const pair = selectedPairs[i];
      showLoadingProgress(`Processing metrics for ${pair} (${i+1}/5)...`);

      const priceData = await BinanceDataService.fetchCurrentPrice(pair);
      const ticker24h = await BinanceDataService.fetch24hTicker(pair);
      const klines = await BinanceDataService.fetchKlines(pair, '1h', 300);
      const depth = await BinanceDataService.fetchOrderBook(pair, 20);

      let htfBullishBulk = null;
      try {
        const htfKlinesBulk = await BinanceDataService.fetchKlines(pair, bulkHtfInterval, 100);
        const htfClosesBulk = htfKlinesBulk.map(k => parseFloat(k[4]));
        const htfRsiArrBulk = getRSI(htfClosesBulk, 14);
        const htfRsiBulk = htfRsiArrBulk[htfRsiArrBulk.length - 1];
        const htfMacdBulk = getMACD(htfClosesBulk, 12, 26, 9);
        const htfMacdHistBulk = htfMacdBulk.histogram[htfMacdBulk.histogram.length - 1] || 0;
        htfBullishBulk = htfRsiBulk > 50 && htfMacdHistBulk > 0;
      } catch(e) {
        console.warn(`HTF fetch failed for ${pair}:`, e.message);
      }

      const quantData = runQuantitativePipeline(klines, depth, false, htfBullishBulk);
      quantData.currentPrice = priceData.price;
      quantData.ticker24h = ticker24h;

      backtestCache[pair] = quantData.backtest;

      updatePositionsMarkPrices(pair, priceData.price);
      consolidatedQuantData.push({ pair, quantData });

      let bulkParsedGemini;
      if (activeKey && activeModel) {
        try {
          const bulkResponseText = await callGemini(pair, '1h', quantData, false);
          bulkParsedGemini = safelyParseResponse(bulkResponseText, false);
        } catch(e) {
          bulkParsedGemini = null;
        }
      }

      if (!bulkParsedGemini) {
        bulkParsedGemini = {
          risk: quantData.tradeConfidence.riskLevel,
          probability: quantData.tradeConfidence.probability,
          strength: `Quantitative analysis: ${parseFloat(ticker24h.priceChangePercent).toFixed(2)}% 24h change. Structure: ${quantData.marketStructure.trend}. Markov state: ${quantData.markov.currentState}.`,
          weaknesses: `${klines.length} candles analyzed. Volume strength: ${quantData.volConfirmation.volumeStrength}/100. ADX: ${quantData.ta.adx}. Rejections: ${quantData.rejections.length > 0 ? quantData.rejections.join('; ') : 'None'}.`
        };
      }

      const bubble = addMessage('assistant', '', true);
      bubble.innerHTML = displayQuantitativeResult(quantData, bulkParsedGemini, pair, '1h');

      if (quantData.consensusDirection !== 'NO TRADE' && paperSettings.autoTradeSignals) {
        tryPaperTrade(pair, priceData.price, quantData.consensusDirection, quantData);
      }
    }

    removeThinking();
    showToast(`✅ Quantitative scan for ${type} strategy completed successfully.`);

    const bestResult = consolidatedQuantData.reduce((best, curr) =>
      curr.quantData.confidence > best.quantData.confidence ? curr : best,
      consolidatedQuantData[0]
    );

    if (activeKey && activeModel) {
      addThinking();
      showLoadingProgress("Generating general strategic market analysis...");
      try {
        const summaryText = await callGemini(bestResult.pair, '1h', bestResult.quantData, false);
        removeThinking();
        const parsed = safelyParseResponse(summaryText, false);
        if (parsed) {
          addMessage('assistant', `🔮 GENERAL STRATEGIC SUMMARY: ${parsed.strength}\n\nRisk Assessment: ${parsed.risk}\nReasoning: ${parsed.weaknesses}`);
        } else {
          addMessage('assistant', summaryText);
        }
      } catch(e) {
        removeThinking();
        const summaryLines = consolidatedQuantData.map(({ pair, quantData }) =>
          `${pair}: ${quantData.consensusDirection} | Confidence: ${quantData.confidence}% | Grade: ${quantData.grade} | Markov: ${quantData.markov.currentState}`
        ).join('\n');
        addMessage('assistant', `🔮 QUANTITATIVE SCAN SUMMARY (${type}):\n\n${summaryLines}`);
      }
    } else {
      removeThinking();
      const summaryLines = consolidatedQuantData.map(({ pair, quantData }) =>
        `${pair}: ${quantData.consensusDirection} | Confidence: ${quantData.confidence}% | Grade: ${quantData.grade}`
      ).join('\n');
      addMessage('assistant', `🔮 SCAN COMPLETE (${type}) — No Gemini key active. Local results:\n\n${summaryLines}`);
    }

  } catch(e) {
    removeThinking();
    addMessage('assistant', `⚠ Multi-Pair Scan Fault: ${e.message}`);
  }

  isLoading = false;
  document.getElementById('sendBtn').disabled = false;
}

function sendQuick(text) {
  document.getElementById('userInput').value = text;
  sendMessage();
}

document.getElementById('userInput').addEventListener('keypress', e => { if(e.key==='Enter') sendMessage(); });

setInterval(() => {
  if (activePredictionParams) {
    const matchedPosition = paperActivePositions.find(p => p.asset === activePredictionParams.pair);
    if (matchedPosition) {
      updatePredictionChartPrice(matchedPosition.markPrice);
    } else {
      const pct = (Math.random() - 0.5) * 0.0006;
      const newPrice = activePredictionParams.t0Close * (1 + pct);
      updatePredictionChartPrice(newPrice);
    }
  }

  if (paperActivePositions.length > 0) {
    paperActivePositions.forEach(pos => {
      const pct = (Math.random() - 0.5) * 0.001;
      pos.markPrice = pos.markPrice * (1 + pct);
      if (pos.side === 'LONG') {
        pos.unrealizedPnl = ((pos.markPrice - pos.entryPrice) / pos.entryPrice) * pos.size;
      } else {
        pos.unrealizedPnl = ((pos.entryPrice - pos.markPrice) / pos.entryPrice) * pos.size;
      }
      pos.pnlPercent = (pos.unrealizedPnl / pos.size) * 100;
      
      if (pos.side === 'LONG') {
        if (pos.markPrice <= pos.stopLoss) {
          closePaperPosition(pos.id);
          addSimulatedBrokerLog(`🚨 Stop Loss Triggered (Fluctuation) for LONG ${pos.asset} at $${formatPrice(pos.markPrice)}`);
        } else if (pos.markPrice >= pos.takeProfit) {
          closePaperPosition(pos.id);
          addSimulatedBrokerLog(`🎯 Take Profit Hit (Fluctuation) for LONG ${pos.asset} at $${formatPrice(pos.markPrice)}`);
        }
      } else {
        if (pos.markPrice >= pos.stopLoss) {
          closePaperPosition(pos.id);
          addSimulatedBrokerLog(`🚨 Stop Loss Triggered (Fluctuation) for SHORT ${pos.asset} at $${formatPrice(pos.markPrice)}`);
        } else if (pos.markPrice <= pos.takeProfit) {
          closePaperPosition(pos.id);
          addSimulatedBrokerLog(`🎯 Take Profit Hit (Fluctuation) for SHORT ${pos.asset} at $${formatPrice(pos.markPrice)}`);
        }
      }
    });
    savePaperState();
    updatePaperUIDisplays();
    
    const ws = document.getElementById('tradingWorkspace');
    if (ws && ws.classList.contains('open')) {
      renderActiveTradesTable();
    }
  }
}, 3000);

window.addEventListener('DOMContentLoaded', () => {
  const candleSelect = document.getElementById('candlePairSelect');
  candleSelect.innerHTML = '';
  PAIRS_50.forEach(pair => {
    const opt = document.createElement('option');
    opt.value = pair;
    opt.textContent = pair + " Projection Matrix";
    candleSelect.appendChild(opt);
  });

  const storedKey = localStorage.getItem('key_gemini');
  const storedModel = localStorage.getItem('model_gemini');
  if (storedKey) {
    document.getElementById('geminiKey').value = storedKey;
  }
  if (storedModel) {
    const opt = document.createElement('option');
    opt.value = storedModel;
    opt.textContent = storedModel;
    opt.selected = true;
    document.getElementById('geminiModelSelect').appendChild(opt);
    
    activeKey = storedKey;
    activeModel = storedModel;
  }

  document.getElementById('paperMasterToggle').checked = paperSettings.masterEnabled || false;
  document.getElementById('paperMVDPToggle').checked = paperSettings.autoTradeMVDP || false;
  document.getElementById('paperSignalsToggle').checked = paperSettings.autoTradeSignals || false;
  document.getElementById('paperPositionSize').value = paperSettings.defaultPositionSize || 1000.00;
  document.getElementById('riskPerTradeSelect').value = (paperSettings.riskPerTrade || 1.0).toFixed(1);
  document.getElementById('minConfidenceSelect').value = paperSettings.minConfidence || 85;

  updatePaperUIDisplays();
});

function getTimeframeSeconds(tf) {
  const map = {
    '1m': 60, '3m': 180, '5m': 300, '15m': 900, '30m': 1800,
    '1h': 3600, '2h': 7200, '4h': 14400, '1d': 86400
  };
  return map[tf] || 3600;
}

let activePredictionChart = null;
let activePredictionSeries = null;
let activePredictionParams = null;

function renderRealtimePredictionChart(containerId, klines, quantData, pair, timeframe) {
  const container = document.getElementById(containerId);
  if (!container) return;

  const chart = LightweightCharts.createChart(container, {
    width: container.clientWidth,
    height: 250,
    layout: {
      background: { type: 'solid', color: '#0B0F19' }, 
      textColor: '#9CA3AF', 
      fontFamily: 'Space Mono, monospace',
    },
    grid: {
      vertLines: { color: 'rgba(31, 41, 55, 0.4)' }, 
      horzLines: { color: 'rgba(31, 41, 55, 0.4)' }
    },
    rightPriceScale: {
      borderColor: '#1F2937',
    },
    timeScale: {
      borderColor: '#1F2937',
      timeVisible: true,
      secondsVisible: false,
    },
  });

  const candlestickSeries = chart.addCandlestickSeries({
    upColor: '#10B981',
    downColor: '#EF4444',
    borderUpColor: '#10B981',
    borderDownColor: '#EF4444',
    wickUpColor: '#10B981',
    wickDownColor: '#EF4444',
  });

  const slicedKlines = klines.slice(-50);
  const candleData = slicedKlines.map(k => {
    return {
      time: Math.floor(parseInt(k[0]) / 1000),
      open: parseFloat(k[1]),
      high: parseFloat(k[2]),
      low: parseFloat(k[3]),
      close: parseFloat(k[4])
    };
  });

  const t0Time = candleData[candleData.length - 1].time;
  const t0Open = candleData[candleData.length - 1].open;
  const t0High = candleData[candleData.length - 1].high;
  const t0Low = candleData[candleData.length - 1].low;
  const t0Close = parseFloat(quantData.currentPrice);

  const interval = getTimeframeSeconds(timeframe);
  const t1 = t0Time + interval;
  const t2 = t1 + interval;

  const c1Bullish = quantData.markov.nextCandleProbability["STRONG_BULL"] + quantData.markov.nextCandleProbability["BULL"] > quantData.markov.nextCandleProbability["STRONG_BEAR"] + quantData.markov.nextCandleProbability["BEAR"];
  const c2Bullish = quantData.markov.secondCandleProbability["STRONG_BULL"] + quantData.markov.secondCandleProbability["BULL"] > quantData.markov.secondCandleProbability["STRONG_BEAR"] + quantData.markov.secondCandleProbability["BEAR"];

  const dir1 = c1Bullish ? 1 : -1;
  const dir2 = c2Bullish ? 1 : -1;
  const rawStd = quantData.markov.rawStd || 0.01;

  const c1Prob = c1Bullish
    ? ((quantData.markov.nextCandleProbability["STRONG_BULL"] || 0) + (quantData.markov.nextCandleProbability["BULL"] || 0))
    : ((quantData.markov.nextCandleProbability["STRONG_BEAR"] || 0) + (quantData.markov.nextCandleProbability["BEAR"] || 0));
  const c1StrongBias = c1Bullish
    ? (quantData.markov.nextCandleProbability["STRONG_BULL"] || 0) > (quantData.markov.nextCandleProbability["BULL"] || 0)
    : (quantData.markov.nextCandleProbability["STRONG_BEAR"] || 0) > (quantData.markov.nextCandleProbability["BEAR"] || 0);
  const c1Mult = c1StrongBias ? 1.8 : 1.0;
  const c1Body = rawStd * c1Prob * c1Mult;

  const c2Prob = c2Bullish
    ? ((quantData.markov.secondCandleProbability["STRONG_BULL"] || 0) + (quantData.markov.secondCandleProbability["BULL"] || 0))
    : ((quantData.markov.secondCandleProbability["STRONG_BEAR"] || 0) + (quantData.markov.secondCandleProbability["BEAR"] || 0));
  const c2StrongBias = c2Bullish
    ? (quantData.markov.secondCandleProbability["STRONG_BULL"] || 0) > (quantData.markov.secondCandleProbability["BULL"] || 0)
    : (quantData.markov.secondCandleProbability["STRONG_BEAR"] || 0) > (quantData.markov.secondCandleProbability["BEAR"] || 0);
  const c2Mult = c2StrongBias ? 1.8 : 1.0;
  const c2Body = rawStd * c2Prob * c2Mult;

  const p1Close = t0Close * (1 + dir1 * c1Body);
  const p2Close = p1Close * (1 + dir2 * c2Body);

  const high1 = Math.max(t0Close, p1Close) + (t0Close * c1Body * 0.2);
  const low1 = Math.min(t0Close, p1Close) - (t0Close * c1Body * 0.2);

  const high2 = Math.max(p1Close, p2Close) + (p1Close * c2Body * 0.2);
  const low2 = Math.min(p1Close, p2Close) - (p1Close * c2Body * 0.2);

  const p1Color = c1Bullish ? 'rgba(16, 185, 129, 0.4)' : 'rgba(239, 68, 68, 0.4)';
  const p2Color = c2Bullish ? 'rgba(16, 185, 129, 0.4)' : 'rgba(239, 68, 68, 0.4)';

  const p1Candle = {
    time: t1,
    open: t0Close,
    high: high1,
    low: low1,
    close: p1Close,
    color: p1Color,
    borderColor: p1Color,
    wickColor: p1Color
  };

  const p2Candle = {
    time: t2,
    open: p1Close,
    high: high2,
    low: low2,
    close: p2Close,
    color: p2Color,
    borderColor: p2Color,
    wickColor: p2Color
  };

  candleData[candleData.length - 1].close = t0Close;

  const fullSeriesData = [...candleData, p1Candle, p2Candle];
  candlestickSeries.setData(fullSeriesData);

  const lastHistClose = candleData[candleData.length - 1].close;
  const livePrice = parseFloat(quantData.currentPrice);
  const gapPct = Math.abs((livePrice - lastHistClose) / lastHistClose) * 100;
  if (gapPct > 0.1) {
    candlestickSeries.createPriceLine({
      price: livePrice,
      color: '#00FFCC',
      lineWidth: 1,
      lineStyle: 2,
      axisLabelVisible: true,
      title: `Live: $${livePrice.toFixed(2)}`,
    });
  }

  chart.timeScale().fitContent();

  const resizeObserver = new ResizeObserver(entries => {
    if (entries.length === 0) return;
    const { width, height } = entries[0].contentRect;
    chart.resize(width, height);
  });
  resizeObserver.observe(container);

  activePredictionChart = chart;
  activePredictionSeries = candlestickSeries;
  activePredictionParams = {
    pair,
    timeframe,
    quantData,
    interval,
    c1Bullish,
    c2Bullish,
    t0Time,
    t0Open,
    t0High,
    t0Low,
    t0Close,
    t1Time: t1,
    t2Time: t2,
    historicalData: candleData 
  };
}

function updatePredictionChartPrice(newPrice) {
  if (!activePredictionChart || !activePredictionSeries || !activePredictionParams) return;
  const params = activePredictionParams;
  const p = parseFloat(newPrice);

  params.t0Close = p;
  if (p > params.t0High) params.t0High = p;
  if (p < params.t0Low) params.t0Low = p;

  const baseData = [...params.historicalData];
  
  if (baseData.length > 0) {
    baseData[baseData.length - 1] = {
      time: params.t0Time,
      open: params.t0Open,
      high: params.t0High,
      low: params.t0Low,
      close: params.t0Close
    };
  }

  const rawStd = params.quantData.markov.rawStd || 0.01;
  const dir1 = params.c1Bullish ? 1 : -1;
  const dir2 = params.c2Bullish ? 1 : -1;

  const c1Prob = params.c1Bullish
    ? ((params.quantData.markov.nextCandleProbability["STRONG_BULL"] || 0) + (params.quantData.markov.nextCandleProbability["BULL"] || 0))
    : ((params.quantData.markov.nextCandleProbability["STRONG_BEAR"] || 0) + (params.quantData.markov.nextCandleProbability["BEAR"] || 0));
  const c1StrongBias = params.c1Bullish
    ? (params.quantData.markov.nextCandleProbability["STRONG_BULL"] || 0) > (params.quantData.markov.nextCandleProbability["BULL"] || 0)
    : (params.quantData.markov.nextCandleProbability["STRONG_BEAR"] || 0) > (params.quantData.markov.nextCandleProbability["BEAR"] || 0);
  const c1Mult = c1StrongBias ? 1.8 : 1.0;
  const c1Body = rawStd * c1Prob * c1Mult;

  const c2Prob = params.c2Bullish
    ? ((params.quantData.markov.secondCandleProbability["STRONG_BULL"] || 0) + (params.quantData.secondCandleProbability["BULL"] || 0))
    : ((params.quantData.markov.secondCandleProbability["STRONG_BEAR"] || 0) + (params.quantData.secondCandleProbability["BEAR"] || 0));
  const c2StrongBias = params.c2Bullish
    ? (params.quantData.secondCandleProbability["STRONG_BULL"] || 0) > (params.quantData.secondCandleProbability["BULL"] || 0)
    : (params.quantData.mockNextState === "STRONG_BEAR" || (params.quantData.markov.secondCandleProbability["STRONG_BEAR"] || 0) > (params.quantData.markov.secondCandleProbability["BEAR"] || 0));
  const c2Mult = c2StrongBias ? 1.8 : 1.0;
  const c2Body = rawStd * c2Prob * c2Mult;

  const p1Close = params.t0Close * (1 + dir1 * c1Body);
  const p2Close = p1Close * (1 + dir2 * c2Body);

  const high1 = Math.max(params.t0Close, p1Close) + (params.t0Close * c1Body * 0.2);
  const low1 = Math.min(params.t0Close, p1Close) - (params.t0Close * c1Body * 0.2);

  const high2 = Math.max(p1Close, p2Close) + (p1Close * c2Body * 0.2);
  const low2 = Math.min(p1Close, p2Close) - (p1Close * c2Body * 0.2);

  const p1Color = params.c1Bullish ? 'rgba(16, 185, 129, 0.4)' : 'rgba(239, 68, 68, 0.4)';
  const p2Color = params.c2Bullish ? 'rgba(16, 185, 129, 0.4)' : 'rgba(239, 68, 68, 0.4)';

  const p1Candle = {
    time: params.t1Time,
    open: params.t0Close,
    high: high1,
    low: low1,
    close: p1Close,
    color: p1Color,
    borderColor: p1Color,
    wickColor: p1Color
  };

  const p2Candle = {
    time: params.t2Time,
    open: p1Close,
    high: high2,
    low: low2,
    close: p2Close,
    color: p2Color,
    borderColor: p2Color,
    wickColor: p2Color
  };

  activePredictionSeries.setData([...baseData, p1Candle, p2Candle]);
}