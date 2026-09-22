'use strict';

const api = async (path, opts = {}) => {
  const res = await fetch(path, {
    headers: opts.body ? { 'Content-Type': 'application/json' } : {},
    ...opts,
    body: opts.body ? JSON.stringify(opts.body) : undefined,
  });
  const ct = res.headers.get('content-type') || '';
  const data = ct.includes('application/json') ? await res.json() : await res.text();
  if (!res.ok) {
    const msg = data && (data.message || (data.messages && data.messages.join('；')))
      || (typeof data === 'string' ? data : res.statusText);
    throw new Error(`[${res.status}] ${msg}`);
  }
  return data;
};

const toast = (msg, ms = 4200) => {
  const box = document.getElementById('toast');
  const el = document.createElement('div');
  el.className = 'toast-item';
  el.textContent = msg;
  box.appendChild(el);
  setTimeout(() => el.remove(), ms);
};

const esc = (s) => String(s ?? '').replace(/[&<>"]/g,
  (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));

const fmtZ = (z) => (z === null || z === undefined || Number.isNaN(z)) ? '—（越界，无数字）' : Number(z).toFixed(2) + ' Ω';
const fmtSi = (m) => m === null || m === undefined ? '—' : `${(m * 1e6).toFixed(2)} µm`;

const state = {
  stackups: [],
  selectedCode: null,
  selectedVersion: null,
  previewData: null,
  editor: null,
};

document.querySelectorAll('nav button').forEach((btn) => {
  btn.onclick = () => {
    document.querySelectorAll('nav button').forEach((b) => b.classList.toggle('active', b === btn));
    document.querySelectorAll('main > section').forEach((s) => {
      s.hidden = s.id !== 'tab-' + btn.dataset.tab;
    });
    renderTab(btn.dataset.tab);
  };
});

async function renderTab(tab) {
  if (tab === 'analyze') await renderAnalyze();
  if (tab === 'editor') await renderEditor();
  if (tab === 'formulas') await renderFormulas();
  if (tab === 'runs') await renderRuns();
  if (tab === 'admin') await renderAdmin();
}

async function loadStackups() {
  state.stackups = await api('/api/stackups');
  if (!state.selectedCode && state.stackups[0]) {
    state.selectedCode = state.stackups[0].boardCode;
    state.selectedVersion = state.stackups[0].version;
  }
  return state.stackups;
}

function versionSelector(versions) {
  return `<select id="verSel">${versions.map((v) =>
    `<option value="${v.version}" ${v.version === state.selectedVersion ? 'selected' : ''}>v${v.version} · ${esc(v.materialRevision || '')}</option>`
  ).join('')}</select>`;
}

async function renderAnalyze() {
  const root = document.getElementById('tab-analyze');
  const list = await loadStackups();
  if (!list.length) {
    root.innerHTML = '<div class="card">没有叠层。请先到“叠层编辑”加载 fixture 或手工新建。</div>';
    return;
  }
  const versions = await api(`/api/stackups/${encodeURIComponent(state.selectedCode)}`);
  if (!versions.some((v) => v.version === state.selectedVersion)) {
    state.selectedVersion = versions[versions.length - 1].version;
  }
  root.innerHTML = `
    <div class="card">
      <div class="row">
        <strong>选择叠层：</strong>
        <select id="boardSel">${list.map((s) =>
          `<option value="${esc(s.boardCode)}" ${s.boardCode === state.selectedCode ? 'selected' : ''}>${esc(s.name)}（${esc(s.boardCode)}）</option>`).join('')}</select>
        版本：${versionSelector(versions)}
        <span class="muted">叠层编辑只产生新版本；旧运行结论不会自动重算。</span>
      </div>
      <div class="row" style="margin-top:10px">
        <label>种子 <input id="seed" type="number" value="20260922" class="sm"></label>
        <label>抽样数 <input id="samples" type="number" value="200" class="sm" min="1" max="100000"></label>
        <button class="btn" id="btnPreview">预览（不落库）</button>
        <button class="btn ghost" id="btnPersist">分析并保存运行记录</button>
        <button class="btn danger" id="btnIndep">尝试“独立取极值”（应被拒绝）</button>
      </div>
    </div>
    <div class="grid2">
      <div class="card" id="svgCard"><h2>横截面</h2><div id="svgBox" class="muted">选择走线条目高亮…</div></div>
      <div class="card"><h2>批次（关联压合过程）</h2><div id="lotBox" class="muted">预览后显示。</div></div>
    </div>
    <div id="resultBox"></div>`;

  document.getElementById('boardSel').onchange = async (e) => {
    state.selectedCode = e.target.value;
    const vs = await api(`/api/stackups/${encodeURIComponent(state.selectedCode)}`);
    state.selectedVersion = vs[vs.length - 1].version;
    renderTab('analyze');
  };
  document.getElementById('verSel').onchange = (e) => { state.selectedVersion = Number(e.target.value); };
  document.getElementById('btnPreview').onclick = () => runAnalysis(false);
  document.getElementById('btnPersist').onclick = () => runAnalysis(true);
  document.getElementById('btnIndep').onclick = tryIndependentExtrema;
  refreshSvg();
}

function codePath() {
  return `/api/stackups/${encodeURIComponent(state.selectedCode)}/versions/${state.selectedVersion}`;
}

function refreshSvg(trace) {
  const qs = trace ? `?trace=${encodeURIComponent(trace)}` : '';
  api(codePath() + '/svg' + qs).then((svgText) => {
    document.getElementById('svgBox').innerHTML = svgText;
  }).catch((e) => toast('SVG 渲染失败：' + e.message));
}

async function runAnalysis(persist) {
  const seed = Number(document.getElementById('seed').value);
  const samples = Number(document.getElementById('samples').value);
  const btnText = persist ? '分析已保存' : '预览完成';
  try {
    let data;
    if (persist) {
      data = await api(`${codePath()}/analyze?seed=${seed}&samples=${samples}`);
    } else {
      const version = await api(codePath());
      data = await api('/api/preview', {
        method: 'POST',
        body: { stackup: version.stackup, seed, samples },
      });
    }
    state.previewData = data;
    renderResults(data);
    toast(btnText + (data.passed ? '：全部通过' : '：存在失败/越界点'));
  } catch (e) {
    toast(e.message);
  }
}

async function tryIndependentExtrema() {
  const signs = {};
  // 请求把同一压合批次的介质厚度取上界、铜厚取下界——物理不可能组合
  const version = await api(codePath());
  for (const layer of version.stackup.layers) {
    if (layer.kind === 'DIELECTRIC' && layer.thicknessTol?.lotId) {
      signs[`layer:${layer.name}:thickness`] = 1;
    }
    if (layer.kind === 'COPPER' && layer.thicknessTol?.lotId) {
      signs[`layer:${layer.name}:thickness`] = -1;
    }
  }
  try {
    const verdict = await api(codePath() + '/independent-extrema-check', {
      method: 'POST', body: signs,
    });
    const reasons = Object.entries(verdict).filter(([k]) => k !== 'rejected' && k !== 'requiredPolicy')
      .filter(([, v]) => v && v.rejected)
      .map(([k, v]) => `${esc(k)}：${esc(v.reason)}`).join('<br>');
    document.getElementById('resultBox').innerHTML =
      `<div class="card"><h2>独立取极值策略：<span class="tag fail">已拒绝</span></h2>
       <div>${reasons || '该叠层无关联方向冲突（但仍不允许逐变量独立极值）。'}</div>
       <div class="muted" style="margin-top:6px">允许策略：<span class="lot">${esc(verdict.requiredPolicy)}</span></div></div>`;
  } catch (e) {
    toast(e.message);
  }
}

function renderResults(result) {
  const box = document.getElementById('resultBox');
  const traces = result.traces || [];
  document.getElementById('lotBox').innerHTML = renderLots(traces[0]);

  box.innerHTML = traces.map((t) => {
    const overall = t.passed ? '<span class="tag pass">通过</span>' : '<span class="tag fail">失败</span>';
    const oodNote = t.allInDomain ? '' : '<span class="tag ood">含越界点（只给诊断）</span>';
    return `<div class="card">
      <div class="row" style="justify-content:space-between">
        <h2 style="margin:0">${esc(t.traceName)} ${overall} ${oodNote}</h2>
        <span class="muted">阻抗范围（域内点）：${fmtZ(t.zMin)} ~ ${fmtZ(t.zMax)} ·
          角落失败 ${t.cornerFailCount} · 抽样失败 ${t.sampleFailCount} · 越界 ${t.outOfDomainCount}</span>
      </div>
      ${pointTable('标称', [t.nominal], true)}
      <details open><summary>最坏关联角落（${(t.corners || []).length} 个）</summary>
        ${pointTable('角落', t.corners || [])}</details>
      <details><summary>固定种子抽样（失败/越界样本在前）</summary>${sampleTable(t)}</details>
    </div>`;
  }).join('');

  traces.forEach((t) => {
    const el = document.getElementById('highlight-' + cssId(t.traceName));
    if (el) el.onclick = () => refreshSvg(t.traceName);
    document.querySelectorAll(`[data-trace="${esc(t.traceName)}"]`).forEach((n) => {
      n.style.cursor = 'pointer';
      n.onclick = () => refreshSvg(t.traceName);
    });
  });
}

function cssId(s) { return s.replace(/[^a-zA-Z0-9_-]/g, '_'); }

function renderLots(t) {
  if (!t || !t.lots) return '';
  return t.lots.map((l) => `<div style="margin-bottom:6px">
      <span class="lot">${esc(l.lotId)}</span> <span class="muted">（${esc(l.process)}）</span>
      <div class="muted">${(l.variables || []).map(esc).join('；')}</div></div>`).join('');
}

function pointTable(title, points, nominal) {
  const rows = points.map((p) => {
    const status = !p.inDomain
      ? '<span class="tag ood">越界</span>'
      : p.withinBand ? '<span class="tag pass">带内</span>' : '<span class="tag fail">越带</span>';
    const dirs = Object.entries(p.lotDirections || {})
      .map(([lot, d]) => `<span class="lot">${esc(lot)}:${esc(d)}</span>`).join(' ');
    const viol = (p.violations || []).map((v) =>
      `<div class="viol">${esc(v.parameter)}：${esc(v.rule)}</div>`).join('');
    const traceRows = Object.entries(p.traceability || {})
      .map(([k, v]) => `<tr><td class="lot">${esc(k)}</td><td class="lot">${esc(v)}</td></tr>`).join('');
    return `<tr>
      <td data-trace="${esc(title === '标称' ? '' : '')}" class="lot">${esc(p.pointId)}</td>
      <td>${status}</td><td>${fmtZ(p.zOhm)}</td><td>${dirs}</td>
      <td>${viol || '<span class="muted">—</span>'}
        ${traceRows ? `<details><summary>溯源（批次/变量取值）</summary><table>${traceRows}</table></details>` : ''}
        <div class="muted">${esc(p.topologyReason || '')} ${p.formulaId ? '· ' + esc(p.formulaId) : ''}</div>
      </td></tr>`;
  }).join('');
  return `<table style="margin-top:8px"><thead><tr>
    <th>${title}</th><th>判定</th><th>阻抗</th><th>批次方向</th><th>诊断与溯源</th></tr></thead><tbody>${rows}</tbody></table>`;
}

function sampleTable(t) {
  const all = t.samples || [];
  const bad = all.filter((p) => !p.inDomain || !p.withinBand);
  const head = bad.slice(0, 50);
  const body = (head.length ? head : all.slice(0, 20)).map((p) => {
    const status = !p.inDomain ? '<span class="tag ood">越界</span>'
      : p.withinBand ? '<span class="tag pass">带内</span>' : '<span class="tag fail">越带</span>';
    const lots = Object.entries(p.lotDraws || {})
      .map(([k, v]) => `<span class="lot">${esc(k)} u=${Number(v).toFixed(4)}</span>`).join(' ');
    const traceRows = Object.entries(p.traceability || {})
      .map(([k, v]) => `<tr><td class="lot">${esc(k)}</td><td class="lot">${esc(v)}</td></tr>`).join('');
    const viol = (p.violations || []).map((v) =>
      `<div class="viol">${esc(v.parameter)}：${esc(v.rule)}</div>`).join('');
    return `<tr><td class="lot">${esc(p.pointId)}</td><td>${status}</td><td>${fmtZ(p.zOhm)}</td>
      <td>${lots}
        <details><summary>该样本全部变量取值（可回查批次）</summary><table>${traceRows}</table></details>
        ${viol}</td></tr>`;
  }).join('');
  return `<table><thead><tr><th>样本</th><th>判定</th><th>阻抗</th><th>批次抽样 u 与变量溯源</th></tr></thead>
    <tbody>${body}</tbody></table>
    <div class="muted">共 ${all.length} 个样本，失败/越界 ${bad.length} 个；上表展示前 ${head.length || Math.min(all.length, 20)} 个。</div>`;
}

async function renderEditor() {
  const root = document.getElementById('tab-editor');
  if (!state.editor) {
    const fixture = await api('/api/fixture');
    state.editor = JSON.parse(JSON.stringify(fixture));
  }
  const e = state.editor;
  root.innerHTML = `
    <div class="card">
      <h2>叠层元数据</h2>
      <div class="row">
        <label>名称 <input id="edName" type="text" value="${esc(e.name)}"></label>
        <label>板号 boardCode <input id="edCode" type="text" value="${esc(e.boardCode)}"></label>
        <label>材料版本 <input id="edMat" type="text" value="${esc(e.materialRevision || '')}"></label>
        <label><input id="edSym" type="checkbox" ${e.symmetric ? 'checked' : ''}> 对称叠层（层种类/材料关于中心镜像）</label>
      </div>
      <div class="row" style="margin-top:8px">
        <button class="btn ghost" id="edLoadFixture">重新载入固定 fixture</button>
        <button class="btn" id="edSave">校验并另存为新版本</button>
        <span class="muted">单位逐个显式选择（mil / um / mm），不会按数值猜测。</span>
      </div>
    </div>
    <div class="card"><h2>层（从上到下）</h2><div id="edLayers"></div></div>
    <div class="card"><h2>走线与蛇形容差</h2><div id="edTraces"></div>
      <div class="muted" style="margin-top:6px">蛇形（幅度/节距/长度）只影响长度与相位，不参与阻抗；保留输入但不会出现在角落分析里。</div></div>
    <div class="card" id="edValidate"></div>`;
  document.getElementById('edName').oninput = (ev) => { e.name = ev.target.value; };
  document.getElementById('edCode').oninput = (ev) => { e.boardCode = ev.target.value; };
  document.getElementById('edMat').oninput = (ev) => { e.materialRevision = ev.target.value; };
  document.getElementById('edSym').onchange = (ev) => { e.symmetric = ev.target.checked; validateEditor(); };
  document.getElementById('edLoadFixture').onclick = async () => {
    state.editor = await api('/api/fixture');
    renderTab('editor');
  };
  document.getElementById('edSave').onclick = saveEditor;
  renderLayerEditor();
  renderTraceEditor();
  validateEditor();
}

const qInput = (q, path, label) => {
  q = q || { value: 0, unit: 'MM' };
  return `<label>${label} <input type="number" step="any" class="sm" value="${q.value ?? 0}"
      data-q="${path}.value"></label>
    <select data-q="${path}.unit">
      ${['um', 'mm', 'mil', '1'].map((u) =>
        `<option value="${u}" ${String(q.unit).toLowerCase() === u ? 'selected' : ''}>${u === '1' ? '无量纲' : u}</option>`).join('')}
    </select>`;
};

function tolInputs(tol, path) {
  tol = tol || {};
  return `<div class="muted" style="display:flex;gap:6px;flex-wrap:wrap;align-items:center">
    <label>下界% <input type="number" step="any" class="sm" value="${tol.lowerPct ?? ''}" data-tol="${path}.lowerPct"></label>
    <label>上界% <input type="number" step="any" class="sm" value="${tol.upperPct ?? ''}" data-tol="${path}.upperPct"></label>
    <label>分布
      <select data-tol="${path}.distribution">
        <option value="uniform" ${(tol.distribution || 'uniform') === 'uniform' ? 'selected' : ''}>uniform</option>
        <option value="normal" ${tol.distribution === 'normal' ? 'selected' : ''}>normal(±3σ)</option>
      </select></label>
    <label>关联批次 lot <input type="text" style="width:120px" value="${esc(tol.lotId || '')}" data-tol="${path}.lotId"></label>
    <label>工艺说明 <input type="text" style="width:180px" value="${esc(tol.processHint || '')}" data-tol="${path}.processHint"></label>
  </div>`;
}

function renderLayerEditor() {
  const e = state.editor;
  const html = e.layers.map((l, i) => `
    <table style="margin-bottom:8px">
      <thead><tr><th style="width:130px">层 ${i + 1}</th><th>参数</th></tr></thead>
      <tbody>
      <tr><td>名称 / 类型</td><td>
        <input type="text" value="${esc(l.name)}" data-layer="${i}.name">
        <select data-layer="${i}.kind">
          ${['DIELECTRIC', 'COPPER', 'SOLDER_MASK'].map((k) =>
            `<option ${l.kind === k ? 'selected' : ''}>${k}</option>`).join('')}
        </select>
        ${l.kind === 'COPPER' ? `<select data-layer="${i}.copperRole">
          ${['SIGNAL', 'REFERENCE_PLANE'].map((k) =>
            `<option ${l.copperRole === k ? 'selected' : ''}>${k === 'SIGNAL' ? '信号' : '参考平面'}</option>`).join('')}
        </select>` : ''}
        ${l.kind === 'DIELECTRIC' ? `<input type="text" style="width:150px" placeholder="材料" value="${esc(l.material || '')}" data-layer="${i}.material">` : ''}
      </td></tr>
      <tr><td>厚度</td><td>${qInput(l.thickness, `layers.${i}.thickness`, '')}
        <div style="margin-top:4px">厚度容差：${tolInputs(l.thicknessTol, `layers.${i}.thicknessTol`)}</div></td></tr>
      ${(l.kind === 'DIELECTRIC' || l.kind === 'SOLDER_MASK') ? `<tr><td>Dk</td><td>
        ${qInput(l.dk, `layers.${i}.dk`, '')}
        <div style="margin-top:4px">Dk 容差：${tolInputs(l.dkTol, `layers.${i}.dkTol`)}</div></td></tr>` : ''}
      </tbody>
    </table>`).join('');
  document.getElementById('edLayers').innerHTML = html +
    `<div class="row"><button class="btn ghost" id="addLayer">添加层</button>
      <button class="btn danger" id="removeLayer">删除最后一层</button></div>`;
  bindEditorInputs();
  document.getElementById('addLayer').onclick = () => {
    e.layers.push({ name: 'NEW', kind: 'DIELECTRIC',
      thickness: { value: 0.1, unit: 'mm' },
      thicknessTol: null, dk: { value: 4.0, unit: '1' }, dkTol: null,
      material: 'NEW-MATERIAL', copperRole: null });
    renderTab('editor');
  };
  document.getElementById('removeLayer').onclick = () => { e.layers.pop(); renderTab('editor'); };
}

function renderTraceEditor() {
  const e = state.editor;
  const html = e.traces.map((t, i) => `
    <table style="margin-bottom:8px">
      <tbody>
      <tr><td style="width:130px">名称 / 类型</td><td>
        <input type="text" value="${esc(t.name)}" data-trace="${i}.name">
        <select data-trace="${i}.type">
          <option value="SINGLE_ENDED" ${t.type === 'SINGLE_ENDED' ? 'selected' : ''}>单端</option>
          <option value="DIFFERENTIAL" ${t.type === 'DIFFERENTIAL' ? 'selected' : ''}>差分</option>
        </select>
        信号层 <select data-trace="${i}.signalLayerName">
          ${e.layers.filter((l) => l.kind === 'COPPER').map((l) =>
            `<option ${l.name === t.signalLayerName ? 'selected' : ''}>${esc(l.name)}</option>`).join('')}
        </select></td></tr>
      <tr><td>线宽</td><td>${qInput(t.width, `traces.${i}.width`, '')}
        <div style="margin-top:4px">${tolInputs(t.widthTol, `traces.${i}.widthTol`)}</div></td></tr>
      ${t.type === 'DIFFERENTIAL' ? `<tr><td>线距 s</td><td>${qInput(t.spacing, `traces.${i}.spacing`, '')}
        <div style="margin-top:4px">${tolInputs(t.spacingTol, `traces.${i}.spacingTol`)}</div></td></tr>` : ''}
      <tr><td>目标带 Ω</td><td>
        <label>标称 <input type="number" step="any" class="sm" value="${t.target?.nominal ?? 50}" data-trace="${i}.target.nominal"></label>
        <label>下界 <input type="number" step="any" class="sm" value="${t.target?.lower ?? 45}" data-trace="${i}.target.lower"></label>
        <label>上界 <input type="number" step="any" class="sm" value="${t.target?.upper ?? 55}" data-trace="${i}.target.upper"></label></td></tr>
      <tr><td>蛇形容差<br><span class="muted">仅长度/相位</span></td><td>
        ${qInput(t.meander?.amplitude, `traces.${i}.meander.amplitude`, '幅度')}
        ${qInput(t.meander?.pitch, `traces.${i}.meander.pitch`, '节距')}
        ${qInput(t.meander?.length, `traces.${i}.meander.length`, '长度')}
      </td></tr>
      </tbody>
    </table>`).join('');
  document.getElementById('edTraces').innerHTML = html +
    `<div class="row"><button class="btn ghost" id="addTrace">添加走线</button>
      <button class="btn danger" id="removeTrace">删除最后一条走线</button></div>`;
  bindEditorInputs();
  document.getElementById('addTrace').onclick = () => {
    e.traces.push({ name: 'NEW-TRACE', signalLayerName: firstSignalLayer(e),
      type: 'SINGLE_ENDED', width: { value: 0.2, unit: 'mm' }, widthTol: null,
      spacing: null, spacingTol: null, copperThicknessTol: null, meander: null,
      target: { nominal: 50, lower: 45, upper: 55 } });
    renderTab('editor');
  };
  document.getElementById('removeTrace').onclick = () => { e.traces.pop(); renderTab('editor'); };
}

function firstSignalLayer(e) {
  const l = e.layers.find((x) => x.kind === 'COPPER' && x.copperRole === 'SIGNAL');
  return l ? l.name : '';
}

function setDeep(obj, path, value) {
  const parts = path.split('.');
  let cur = obj;
  for (let i = 0; i < parts.length - 1; i++) {
    if (cur[parts[i]] == null) cur[parts[i]] = {};
    cur = cur[parts[i]];
  }
  cur[parts[parts.length - 1]] = value;
}

function bindEditorInputs() {
  document.querySelectorAll('[data-layer],[data-trace],[data-q],[data-tol]').forEach((el) => {
    el.onchange = () => {
      const attr = el.dataset.layer != null ? 'layer'
        : el.dataset.trace != null ? 'trace'
        : el.dataset.q != null ? 'q' : 'tol';
      const path = el.dataset[attr];
      let value = el.value;
      if (el.type === 'number') value = value === '' ? null : Number(value);
      if (path.endsWith('.lowerPct') || path.endsWith('.upperPct')) value = value;
      const root = attr === 'layer' || path.startsWith('layers') ? state.editor : state.editor;
      if (attr === 'layer' && !path.startsWith('layers')) {
        setDeep(state.editor, 'layers.' + path, value);
      } else if (attr === 'trace' && !path.startsWith('traces')) {
        setDeep(state.editor, 'traces.' + path, value);
      } else {
        setDeep(root, path, value);
      }
      if (path.endsWith('.kind') || path.endsWith('.type') || path.endsWith('.signalLayerName')) {
        renderTab('editor');
      } else {
        validateEditor();
      }
    };
  });
}

async function validateEditor() {
  try {
    const data = await api('/api/preview', {
      method: 'POST',
      body: { stackup: state.editor, seed: 1, samples: 1 },
    });
    const failed = (data.traces || []).filter((t) => !t.passed);
    document.getElementById('edValidate').innerHTML =
      `<h2>即时校验</h2><span class="tag pass">结构/单位/SI 换算通过</span>
       <div class="muted" style="margin-top:4px">标称判定：${data.traces.map((t) =>
        `${esc(t.traceName)}:${t.nominal.inDomain ? fmtZ(t.nominal.zOhm) : '越界'}`).join('；')}</div>`;
  } catch (e) {
    document.getElementById('edValidate').innerHTML =
      `<h2>即时校验</h2><span class="tag fail">未通过</span><pre>${esc(e.message)}</pre>`;
  }
}

async function saveEditor() {
  try {
    const saved = await api('/api/stackups', { method: 'POST', body: state.editor });
    toast(`已保存为新版本 ${saved.boardCode} v${saved.version}`);
    state.selectedCode = saved.boardCode;
    state.selectedVersion = saved.version;
    await loadStackups();
  } catch (e) {
    toast('保存被拒绝：' + e.message);
  }
}

async function renderFormulas() {
  const root = document.getElementById('tab-formulas');
  const fs = await api('/api/formulas');
  root.innerHTML = `<div class="card"><h2>所用公式及适用范围</h2>
    <div class="muted" style="margin-bottom:8px">离开适用域的评估点只保留输入与越界指标（参数、规则、实际值、限值），不输出阻抗数字。</div>
    <table><thead><tr><th>ID</th><th>名称</th><th>表达式</th><th>适用域</th><th>出处</th></tr></thead><tbody>
    ${fs.map((f) => `<tr><td class="lot">${esc(f.id)}</td><td>${esc(f.name)}</td>
      <td><span class="formula">${esc(f.expression)}</span></td>
      <td>${esc(f.domain)}</td><td class="muted">${esc(f.reference)}</td></tr>`).join('')}
    </tbody></table></div>`;
}

async function renderRuns() {
  const root = document.getElementById('tab-runs');
  const runs = await api('/api/runs');
  root.innerHTML = `<div class="card"><h2>运行记录（不可变；旧版本结论不重算）</h2>
    <table><thead><tr><th>#</th><th>叠层</th><th>版本</th><th>种子</th><th>样本</th><th>策略</th><th>结论</th><th>时间</th><th></th></tr></thead><tbody>
    ${runs.map((r) => `<tr>
      <td>${r.id}</td><td>${esc(r.boardCode)}</td><td>v${r.version}</td>
      <td>${r.seed}</td><td>${r.sampleCount}</td><td class="lot">${esc(r.cornerPolicy)}</td>
      <td>${r.passed ? '<span class="tag pass">通过</span>' : '<span class="tag fail">失败</span>'}</td>
      <td class="muted">${esc(r.createdAt)}</td>
      <td><button class="btn ghost" data-run="${r.id}">查看</button></td></tr>`).join('')}
    </tbody></table>
    <div id="runDetail" style="margin-top:12px"></div></div>`;
  document.querySelectorAll('[data-run]').forEach((b) => {
    b.onclick = async () => {
      const r = await api(`/api/runs/${b.dataset.run}`);
      document.getElementById('runDetail').innerHTML =
        `<h2>运行 #${r.id}（输入哈希 <span class="lot">${esc((r.inputHash || '').slice(0, 16))}…</span>）</h2>
         <pre>${esc(r.resultJson)}</pre>`;
    };
  });
}

async function renderAdmin() {
  const root = document.getElementById('tab-admin');
  root.innerHTML = `
    <div class="card"><h2>导出</h2>
      <div class="muted">导出全部叠层版本与运行记录（含载荷 SHA-256，用于清空后复核）。</div>
      <div class="row" style="margin-top:8px">
        <button class="btn" id="btnExport">导出 bundle JSON</button>
        <a id="dlLink" hidden></a></div></div>
    <div class="card"><h2>导入 / 清空复核</h2>
      <div class="row">
        <input type="file" id="importFile" accept="application/json,.json">
        <label><input type="checkbox" id="replaceAll" checked> 导入前清空现有数据</label>
        <button class="btn" id="btnImport">导入并校验哈希</button>
      </div><div id="importReport" class="muted" style="margin-top:8px"></div></div>
    <div class="card"><h2>危险操作</h2>
      <button class="btn danger" id="btnClear">清空数据库（叠层版本 + 运行记录）</button></div>`;
  document.getElementById('btnExport').onclick = async () => {
    const data = await api('/api/export');
    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `laminate-prover-export-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };
  document.getElementById('btnImport').onclick = async () => {
    const file = document.getElementById('importFile').files[0];
    if (!file) return toast('请先选择导出的 JSON 文件');
    const text = await file.text();
    let bundle;
    try { bundle = JSON.parse(text); } catch (e) { return toast('JSON 解析失败：' + e.message); }
    const replaceAll = document.getElementById('replaceAll').checked;
    try {
      const report = await api(`/api/import?replaceAll=${replaceAll}`, { method: 'POST', body: bundle });
      document.getElementById('importReport').innerHTML =
        `导入版本 ${report.versionsImported} 个；导入运行 ${report.runsImported} 条；哈希不一致 ${report.hashMismatches} 条。` +
        (report.notes || []).map((n) => `<div class="viol">${esc(n)}</div>`).join('');
      toast('导入完成');
    } catch (e) { toast(e.message); }
  };
  document.getElementById('btnClear').onclick = async () => {
    if (!confirm('确定清空全部叠层版本与运行记录？')) return;
    await api('/api/admin/database', { method: 'DELETE' });
    toast('数据库已清空；重启服务或手工载入 fixture 可恢复演示数据');
    state.stackups = []; state.selectedCode = null;
  };
}

renderTab('analyze');
