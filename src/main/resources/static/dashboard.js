const state = {
  summary: null,
  stockCandidates: [],
  stockSearchTimer: null,
  tradeHistoryFilter: "ALL",
};

const $ = (id) => document.getElementById(id);

document.addEventListener("DOMContentLoaded", () => {
  $("refreshButton").addEventListener("click", refreshDashboard);
  $("initPaperButton").addEventListener("click", initPaperBalance);
  $("runAiButton").addEventListener("click", runAiDecision);
  $("riskButton").addEventListener("click", runRiskCheck);
  $("orderButton").addEventListener("click", createOrder);
  $("stockSearchInput").addEventListener("input", handleStockSearchInput);
  document.querySelectorAll("[data-history-filter]").forEach((button) => {
    button.addEventListener("click", () => setTradeHistoryFilter(button.dataset.historyFilter));
  });
  loadSummary();
  loadTradeHistory();
  window.setInterval(loadSummary, 20 * 60 * 1000);
  window.setInterval(loadTradeHistory, 20 * 60 * 1000);
});

async function loadSummary() {
  try {
    const response = await fetch("/api/dashboard/summary");
    const payload = await response.json();
    if (!payload.success) throw new Error(payload.message || "Dashboard load failed");
    state.summary = payload.data;
    render(payload.data);
    setActionStatus("Ready");
  } catch (error) {
    setActionStatus(error.message, true);
  }
}

async function refreshDashboard() {
  try {
    setActionStatus("Revaluing account...");
    const response = await fetch("/api/account/revalue", { method: "POST" });
    const payload = await response.json();
    if (!payload.success) throw new Error(payload.message || "Account revalue failed");
    await loadSummary();
    await loadTradeHistory();
    setActionStatus("Revalued");
  } catch (error) {
    setActionStatus(error.message, true);
  }
}

function render(summary) {
  renderStatus(summary);
  renderStockUniverse(summary);
  renderPositions(summary.paperPositions || []);
  renderDecisions(summary);
  renderFlow(summary);
  renderSchedulers(summary.recentSchedulers || []);
  renderFailures(summary);
}

async function loadTradeHistory() {
  try {
    const filter = encodeURIComponent(state.tradeHistoryFilter || "ALL");
    const response = await fetch(`/api/dashboard/ai-trade-history?filter=${filter}&limit=50`);
    const payload = await response.json();
    if (!payload.success) throw new Error(payload.message || "Trade history load failed");
    renderTradeHistory(payload.data || []);
  } catch (error) {
    $("tradeHistoryBody").innerHTML = `<tr><td colspan="6" class="empty loss">${escapeHtml(error.message)}</td></tr>`;
  }
}

function renderStatus(summary) {
  const system = summary.system || {};
  const balance = summary.paperBalance;
  $("lastUpdated").textContent = formatDateTime(summary.generatedAt);
  $("tradingMode").textContent = system.tradingMode || "-";
  $("tradingEnabled").textContent = system.tradingEnabled ? "Trading enabled" : "Trading disabled";
  $("paperTotal").textContent = money(balance?.totalAssetAmount);
  $("paperReturn").textContent = signedPercent(balance?.totalProfitLossRate);
  $("totalProfitLoss").textContent = signedMoney(balance?.totalProfitLossAmount);
  $("totalProfitLoss").className = gainClass(balance?.totalProfitLossAmount);
  $("totalProfitLossRate").textContent = signedPercent(balance?.totalProfitLossRate);
  $("totalProfitLossRate").className = gainClass(balance?.totalProfitLossAmount);
  $("availableCash").textContent = money(balance?.availableCash);
  $("accountNo").textContent = system.accountNoMasked || "-";
  $("activeStockCount").textContent = number(system.activeStockCount);
  $("positionCount").textContent = `${(summary.paperPositions || []).length} positions`;
}

function renderStockUniverse(summary) {
  const activeStocks = summary.activeStocks || [];
  const stocks = summary.stockMasterList || activeStocks;
  $("activeStockBadge").textContent = number(activeStocks.length);
  renderStockMasterList(stocks);
}

function renderStockMasterList(stocks) {
  const list = $("stockMasterList");
  if (!stocks.length) {
    list.innerHTML = `<div class="empty">등록된 stock_master 종목이 없습니다</div>`;
    return;
  }
  list.innerHTML = stocks.map((item) => {
    const active = Boolean(item.active || item.isActive);
    return `
      <div class="stock-chip">
        <div>
          <strong>${escapeHtml(stockLabel(item))}</strong>
          <span>${escapeHtml(item.stockCode || "")}${item.marketType ? ` · ${escapeHtml(item.marketType)}` : ""}</span>
        </div>
        <label class="toggle">
          <input type="checkbox" data-stock-code="${escapeHtml(item.stockCode || "")}" ${active ? "checked" : ""}>
          <span>${active ? "ON" : "OFF"}</span>
        </label>
      </div>
    `;
  }).join("");
  list.querySelectorAll("input[type='checkbox']").forEach((input) => {
    input.addEventListener("change", () => toggleStockActive(input.dataset.stockCode, input.checked));
  });
}

function renderPositions(positions) {
  const body = $("positionsBody");
  if (!positions.length) {
    body.innerHTML = `<tr><td colspan="6" class="empty">No PAPER positions</td></tr>`;
    $("portfolioBadge").textContent = "0";
    return;
  }
  $("portfolioBadge").textContent = `${positions.length}`;
  body.innerHTML = positions.map((item) => `
    <tr>
      <td><strong>${escapeHtml(stockLabel(item))}</strong><br><span class="muted">${escapeHtml(item.stockCode || "")}</span></td>
      <td class="num">${number(item.quantity)}</td>
      <td class="num">${money(item.averageBuyPrice)}</td>
      <td class="num">${money(item.currentPrice)}</td>
      <td class="num">${money(item.valuationAmount)}</td>
      <td class="num ${gainClass(item.profitLossAmount)}">${money(item.profitLossAmount)}<br><span>${signedPercent(item.profitLossRate)}</span></td>
    </tr>
  `).join("");
}

function renderDecisions(summary) {
  const counts = summary.decisions || {};
  $("buyCount").textContent = `BUY ${counts.buyCount || 0}`;
  $("holdCount").textContent = `HOLD ${counts.holdCount || 0}`;
  $("sellCount").textContent = `SELL ${counts.sellCount || 0}`;

  const list = $("decisionsList");
  const decisions = summary.recentDecisions || [];
  if (!decisions.length) {
    list.innerHTML = `<div class="empty">No AI decisions</div>`;
    return;
  }
  list.innerHTML = decisions.map((item) => `
    <div class="item">
      <div class="item-main">
        <div>
          <div class="item-title">#${item.id} ${escapeHtml(stockLabel(item))}</div>
          <div class="item-meta">${formatDateTime(item.createdAt)} · ${escapeHtml(item.decisionStatus || "-")}</div>
        </div>
        <span class="pill ${decisionClass(item.decision)}">${escapeHtml(item.decision || "-")} ${percent(item.confidence)}</span>
      </div>
      <div class="item-reason">${escapeHtml(item.reason || "No reason")}</div>
    </div>
  `).join("");
}

function renderFlow(summary) {
  const risks = summary.recentRiskChecks || [];
  const orders = summary.recentOrders || [];
  $("riskFailCount").textContent = `${summary.decisions?.failedRiskCount || 0} fail`;

  const rows = [
    ...risks.map((item) => ({
      at: item.checkedAt,
      html: `
        <div class="item">
          <div class="item-main">
            <div>
              <div class="item-title">Risk #${item.id} · AI #${item.aiDecisionId}</div>
              <div class="item-meta">${formatDateTime(item.checkedAt)} · ${escapeHtml(stockLabel(item))}</div>
            </div>
            <span class="pill ${item.passed ? "buy" : "sell"}">${item.passed ? "PASS" : "FAIL"}</span>
          </div>
          <div class="item-reason">${escapeHtml(item.failReason || "Risk passed")}</div>
        </div>
      `,
    })),
    ...orders.map((item) => ({
      at: item.requestedAt,
      html: `
        <div class="item">
          <div class="item-main">
            <div>
              <div class="item-title">Order #${item.id} · AI #${item.aiDecisionId}</div>
              <div class="item-meta">${formatDateTime(item.requestedAt)} · ${escapeHtml(stockLabel(item))}</div>
            </div>
            <span class="pill ${item.orderSide === "BUY" ? "buy" : "sell"}">${escapeHtml(item.orderSide || "-")} ${escapeHtml(item.orderStatus || "-")}</span>
          </div>
          <div class="item-reason">${money(item.orderAmount)} · qty ${number(item.orderQuantity)}</div>
        </div>
      `,
    })),
  ].sort((a, b) => new Date(b.at || 0) - new Date(a.at || 0)).slice(0, 12);

  $("flowList").innerHTML = rows.length ? rows.map((row) => row.html).join("") : `<div class="empty">No risk or order records</div>`;
}

function renderTradeHistory(items) {
  const body = $("tradeHistoryBody");
  if (!items.length) {
    body.innerHTML = `<tr><td colspan="6" class="empty">No BUY/SELL history</td></tr>`;
    return;
  }
  body.innerHTML = items.map((item) => {
    const detailId = `history-detail-${item.aiDecisionId}`;
    const riskText = item.riskCheckId ? (item.riskPassed ? "PASS" : "FAIL") : "NONE";
    const riskClass = item.riskCheckId ? (item.riskPassed ? "buy" : "sell") : "hold";
    const orderText = item.orderRequestId
      ? `${item.orderSide || "-"} ${item.orderStatus || "-"}`
      : "NONE";
    const orderClass = item.orderSide === "SELL" ? "sell" : item.orderSide === "BUY" ? "buy" : "hold";
    return `
      <tr class="history-row" data-detail-id="${detailId}" tabindex="0">
        <td>${formatDateTime(item.decisionCreatedAt)}</td>
        <td><strong>${escapeHtml(stockLabel(item))}</strong><br><span class="muted">${escapeHtml(item.stockCode || "")} #${item.aiDecisionId}</span></td>
        <td><span class="pill ${decisionClass(item.decision)}">${escapeHtml(item.decision || "-")}</span></td>
        <td class="num">${percent(item.confidence)}</td>
        <td><span class="pill ${riskClass}">${riskText}</span></td>
        <td><span class="pill ${orderClass}">${escapeHtml(orderText)}</span></td>
      </tr>
      <tr id="${detailId}" class="history-detail" hidden>
        <td colspan="6">
          <div class="history-detail-grid">
            <div>
              <strong>Decision</strong>
              <p>${escapeHtml(item.reason || "No reason")}</p>
            </div>
            <div>
              <strong>Risk</strong>
              <p>${escapeHtml(item.riskFailReason || (item.riskPassed ? "Risk passed" : "No risk check"))}</p>
            </div>
            <div>
              <strong>Order</strong>
              <p>${item.orderRequestId ? `${escapeHtml(item.brokerType || "-")} / qty ${number(item.orderQuantity)} / ${money(item.orderAmount)}` : "No order request"}</p>
            </div>
          </div>
        </td>
      </tr>
    `;
  }).join("");
  body.querySelectorAll(".history-row").forEach((row) => {
    row.addEventListener("click", () => toggleHistoryDetail(row.dataset.detailId));
    row.addEventListener("keydown", (event) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        toggleHistoryDetail(row.dataset.detailId);
      }
    });
  });
}

function toggleHistoryDetail(detailId) {
  const detail = $(detailId);
  if (!detail) return;
  detail.hidden = !detail.hidden;
}

function setTradeHistoryFilter(filter) {
  state.tradeHistoryFilter = filter || "ALL";
  document.querySelectorAll("[data-history-filter]").forEach((button) => {
    button.classList.toggle("active", button.dataset.historyFilter === state.tradeHistoryFilter);
  });
  loadTradeHistory();
}

function renderSchedulers(schedulers) {
  const list = $("schedulerList");
  if (!schedulers.length) {
    list.innerHTML = `<div class="empty">No scheduler logs</div>`;
    return;
  }
  list.innerHTML = schedulers.map((item) => `
    <div class="item">
      <div class="item-main">
        <div>
          <div class="item-title">${escapeHtml(item.schedulerName || "-")}</div>
          <div class="item-meta">${formatDateTime(item.startedAt)}</div>
        </div>
        <span class="pill ${item.status === "SUCCESS" ? "buy" : item.status === "FAILED" ? "sell" : "warn"}">${escapeHtml(item.status || "-")}</span>
      </div>
      <div class="item-reason">${escapeHtml(item.executionMessage || "")}</div>
    </div>
  `).join("");
}

function renderFailures(summary) {
  const failures = [
    ...(summary.recentExternalApiFailures || []).map((item) => ({
      title: `${item.provider || "API"} · ${item.apiName || "-"}`,
      at: item.calledAt,
      message: item.errorMessage,
    })),
    ...(summary.recentBrokerApiFailures || []).map((item) => ({
      title: `${item.brokerType || "BROKER"} · ${item.apiName || "-"}`,
      at: item.calledAt,
      message: item.errorMessage,
    })),
  ].slice(0, 12);
  const list = $("failureList");
  if (!failures.length) {
    list.innerHTML = `<div class="empty">No recent API failures</div>`;
    return;
  }
  list.innerHTML = failures.map((item) => `
    <div class="item">
      <div class="item-main">
        <div class="item-title">${escapeHtml(item.title)}</div>
        <span class="pill sell">FAIL</span>
      </div>
      <div class="item-meta">${formatDateTime(item.at)}</div>
      <div class="item-reason">${escapeHtml(item.message || "")}</div>
    </div>
  `).join("");
}

async function initPaperBalance() {
  const seedCash = $("seedCashInput").value.trim();
  await runAction(`/api/account/paper/balance/init?seedCash=${encodeURIComponent(seedCash)}`, "POST", "PAPER initialized");
}

function handleStockSearchInput() {
  window.clearTimeout(state.stockSearchTimer);
  state.stockSearchTimer = window.setTimeout(searchStockCandidates, 250);
}

async function searchStockCandidates() {
  const query = $("stockSearchInput").value.trim();
  const list = $("stockCandidateList");
  if (query.length < 2) {
    state.stockCandidates = [];
    list.innerHTML = `<div class="empty">두 글자 이상 입력하면 DART 후보가 표시됩니다</div>`;
    return;
  }
  try {
    const response = await fetch(`/api/stocks/candidates?query=${encodeURIComponent(query)}&limit=10`);
    const payload = await response.json();
    if (!payload.success) throw new Error(payload.message || "Candidate search failed");
    state.stockCandidates = payload.data || [];
    renderStockCandidates();
  } catch (error) {
    list.innerHTML = `<div class="empty loss">${escapeHtml(error.message)}</div>`;
  }
}

function renderStockCandidates() {
  const list = $("stockCandidateList");
  const candidates = state.stockCandidates || [];
  if (!candidates.length) {
    list.innerHTML = `<div class="empty">추가 가능한 후보가 없습니다</div>`;
    return;
  }
  list.innerHTML = candidates.map((item) => `
    <button class="candidate-item" type="button" data-stock-code="${escapeHtml(item.stockCode || "")}">
      <strong>${escapeHtml(item.stockName || "-")}</strong>
      <span>${escapeHtml(item.stockCode || "")} · ${escapeHtml(item.corpCode || "")}</span>
    </button>
  `).join("");
  list.querySelectorAll(".candidate-item").forEach((button) => {
    button.addEventListener("click", () => addActiveStock(button.dataset.stockCode));
  });
}

async function addActiveStock(stockCode) {
  if (!stockCode) return setActionStatus("Stock candidate required", true);
  await runJsonAction("/api/stocks/active", "POST", {
    stockCode,
    newsDisplay: 30,
    newsAnalyzeLimit: 20,
  }, "분석 대상 추가 및 bootstrap 완료");
  $("stockSearchInput").value = "";
  state.stockCandidates = [];
  $("stockCandidateList").innerHTML = "";
}

async function toggleStockActive(stockCode, active) {
  if (!stockCode) return setActionStatus("Stock code required", true);
  await runJsonAction(`/api/stocks/${encodeURIComponent(stockCode)}/active`, "PATCH", { active }, "분석 대상 상태 변경 완료");
}

async function runAiDecision() {
  const stockCode = $("stockCodeInput").value.trim();
  if (!stockCode) return setActionStatus("Stock code required", true);
  await runAction(`/api/ai/decisions/${encodeURIComponent(stockCode)}`, "POST", "AI decision requested");
}

async function runRiskCheck() {
  const decisionId = $("decisionIdInput").value.trim();
  if (!decisionId) return setActionStatus("Decision ID required", true);
  await runAction(`/api/risk/checks/${encodeURIComponent(decisionId)}`, "POST", "Risk check requested");
}

async function createOrder() {
  const decisionId = $("decisionIdInput").value.trim();
  if (!decisionId) return setActionStatus("Decision ID required", true);
  await runAction(`/api/orders/requests/${encodeURIComponent(decisionId)}`, "POST", "Order requested");
}

async function runJsonAction(url, method, body, okMessage) {
  try {
    setActionStatus("Running...");
    const response = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    const payload = await response.json();
    if (!payload.success) throw new Error(payload.message || "Action failed");
    setActionStatus(okMessage);
    await loadSummary();
    await loadTradeHistory();
  } catch (error) {
    setActionStatus(error.message, true);
  }
}

async function runAction(url, method, okMessage) {
  try {
    setActionStatus("Running...");
    const response = await fetch(url, { method });
    const payload = await response.json();
    if (!payload.success) throw new Error(payload.message || "Action failed");
    setActionStatus(okMessage);
    await loadSummary();
    await loadTradeHistory();
  } catch (error) {
    setActionStatus(error.message, true);
  }
}

function setActionStatus(message, failed = false) {
  const target = $("actionStatus");
  target.textContent = message;
  target.className = `action-status ${failed ? "loss" : ""}`;
}

function money(value) {
  if (value === null || value === undefined || value === "") return "-";
  return `${Number(value).toLocaleString("ko-KR", { maximumFractionDigits: 0 })}`;
}

function signedMoney(value) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  const sign = numeric > 0 ? "+" : "";
  return `${sign}${numeric.toLocaleString("ko-KR", { maximumFractionDigits: 0 })}`;
}

function number(value) {
  if (value === null || value === undefined || value === "") return "0";
  return Number(value).toLocaleString("ko-KR");
}

function percent(value) {
  if (value === null || value === undefined || value === "") return "";
  return `${(Number(value) * 100).toFixed(0)}%`;
}

function signedPercent(value) {
  if (value === null || value === undefined || value === "") return "-";
  const numeric = Number(value);
  const sign = numeric > 0 ? "+" : "";
  return `${sign}${numeric.toFixed(2)}%`;
}

function formatDateTime(value) {
  if (!value) return "-";
  return String(value).replace("T", " ").slice(0, 19);
}

function gainClass(value) {
  const numeric = Number(value || 0);
  if (numeric > 0) return "gain";
  if (numeric < 0) return "loss";
  return "";
}

function decisionClass(decision) {
  if (decision === "BUY") return "buy";
  if (decision === "SELL") return "sell";
  return "hold";
}

function stockLabel(item) {
  return item.stockName || item.stockCode || "-";
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}
