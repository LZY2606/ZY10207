const state = { stackups: [], current: null, currentSpec: null, report: null, tab: 'formula' };
const TRACE_TYPES = ['MICROSTRIP_SE','MICROSTRIP_DIFF','STRIPLINE_SE','STRIPLINE_DIFF'];
const TYPE_LABEL = {
  MICROSTRIP_SE:'表层微带线·单端', MICROSTRIP_DIFF:'表层微带线·差分',
  STRIPLINE_SE:'带状线·单端', STRIPLINE_DIFF:'带状线·差分'
};
const DIST_LABEL = { UNIFORM:'均匀分布', NORMAL:'正态分布(±3σ)', EXTREME:'仅极值' };
const GEO_FIELDS = [
  { key:'traceWidth', label:'线宽 w', diff:true, strip:true, milHint:'mil' },
  { key:'copperThickness', label:'铜厚 t', diff:true, strip:true },
  { key:'dielectricHeight', label:'介质高度 h', diff:true, strip:true },
  { key:'pairSpacing', label:'差分对间距 s（边沿到边沿）', onlyDiff:true },
  { key:'maskThickness', label:'阻焊层厚度（0=裸铜）', onlyMicro:true }
];
const TOL_KEYS = [
  ['widthTolerance','线宽 w'], ['copperThicknessTolerance','铜厚 t'],
  ['dielectricHeightTolerance','介质高度 h'], ['pairSpacingTolerance','差分间距 s'],
  ['dielectricConstantTolerance','介电常数 Dk'],
  ['solderMaskDkTolerance','阻焊 Dk'], ['maskThicknessTolerance','阻焊厚度'],
  ['meanderTolerance','蛇形边缘波动（单侧减宽）']
];

async function api(path, opts) {
  const res = await fetch(path, opts);
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) { throw new Error(data && data.error ? data.error : ('HTTP ' + res.status)); }
  return data;
}
function el(id) { return document.getElementById(id); }
function esc(s) {
  return String(s ?? '').replace(/[&<>]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;'}[c]));
}
function fmtOhm(v) { return v == null ? '—（越界，无数值）' : Number(v).toFixed(2) + ' Ω'; }
function fmtLen(len) { return len ? len.value + ' ' + len.unit : '—'; }

async function loadStackups(selectId) {
  state.stackups = await api('/api/stackups');
  const box = el('stackList');
  if (!state.stackups.length) {
    box.innerHTML = '<div class="muted">数据库为空。点击“新建叠层”或导入运行记录包。</div>';
    return;
  }
  const groups = {};
  state.stackups.forEach(r => (groups[r.name] ||= []).push(r));
  box.innerHTML = Object.entries(groups).map(([name, rows]) => {
    const latest = rows[rows.length - 1];
    return '<div class="stack-row ' + (state.current === latest.id ? 'active' : '') +
      '" onclick="selectStack(\'' + latest.id + '\')">' +
      '<div class="nm">' + esc(name) +
      '<span class="badge v">v' + latest.version + '</span></div>' +
      '<div class="meta">材料 ' + esc(latest.materialVersion) + '</div>' +
      '<div class="meta">' + rows.map(r =>
        '<span class="badge" onclick="event.stopPropagation();selectStack(\'' + r.id +
        '\')" style="cursor:pointer">v' + r.version + '</span>').join('') + '</div></div>';
  }).join('');
  if (selectId) { await selectStack(selectId); }
  else if (!state.current || !state.stackups.some(r => r.id === state.current)) {
    const firstLatest = Object.values(groups).map(rows => rows[rows.length - 1])[0];
    if (firstLatest) { await selectStack(firstLatest.id); }
  }
}

async function selectStack(id) {
  state.current = id;
  state.report = null;
  state.currentSpec = await api('/api/stackups/' + id);
  const s = state.currentSpec;
  el('currentTitle').textContent = '横截面 — ' + s.name + ' v' + s.version;
  el('section').innerHTML =
    '<img alt="横截面" src="/api/stackups/' + id + '/section.svg?t=' + Date.now() + '"/>';
  el('stackList').querySelectorAll('.stack-row').forEach(n => n.classList.remove('active'));
  await loadStackups(id).catch(() => {});
  renderGroups();
  renderFormula();
  renderEditor();
  renderNominal();
  renderHistory();
  el('summary').innerHTML = '<div class="muted">尚未运行分析。</div>';
  el('points').innerHTML = '';
  el('targetZ').value = s.traceType.endsWith('DIFF') ? 100 : 50;
}

function renderGroups() {
  const s = state.currentSpec;
  const tols = TOL_KEYS.map(([k, label]) => s[k]).filter(Boolean);
  const byGroup = {};
  TOL_KEYS.forEach(([k, label]) => {
    const t = s[k];
    if (t) { (byGroup[t.group] ||= { lot: t.sourceLot, members: [] }).members.push(label); }
  });
  el('groupList').innerHTML = Object.entries(byGroup).map(([g, info]) => {
    const correlated = info.members.length > 1;
    return '<div style="margin:6px 0"><span class="pill ' +
      (correlated ? 'correlated' : 'independent') + '">' +
      (correlated ? '关联组' : '独立组') + '</span><b>' + esc(g) + '</b>' +
      '<div class="muted">批次：' + esc(info.lot) + '</div>' +
      '<div class="muted">成员：' + info.members.map(esc).join('、') + '</div></div>';
  }).join('') || '<span class="muted">该版本未配置容差。</span>';
}

function renderFormula() {
  const s = state.currentSpec;
  const box = el('formulaBox');
  api('/api/formulas/' + s.traceType).then(info => {
    const nominal = s.__nominal;
    box.innerHTML =
      '<div><b>' + esc(info.name) + '</b> <span class="badge">' + esc(info.id) + '</span></div>' +
      info.formulaLines.map(l => '<div class="formula">' + esc(l) + '</div>').join('') +
      '<div class="muted" style="margin-top:8px">适用结构：' + esc(info.applicability) + '</div>' +
      '<div style="margin-top:6px"><b>适用范围（越界只报诊断、不报阻抗数字）</b>' +
      info.domainChecks.map(c => '<div class="diag">• ' + esc(c) + '</div>').join('') + '</div>' +
      info.notes.map(n => '<div class="legend">注：' + esc(n) + '</div>').join('');
  });
}

async function renderNominal() {
  const s = state.currentSpec;
  const data = await api('/api/stackups/' + s.id + '/nominal');
  const box = el('nominalBox');
  const ratioInfo = 'SI 口径：w=' + data.siInputs.w_m.toExponential(3) + ' m，t=' +
    data.siInputs.t_m.toExponential(3) + ' m，h=' + data.siInputs.h_m.toExponential(3) + ' m';
  if (data.inDomain) {
    box.innerHTML = '<div style="margin-top:8px">标称阻抗：<b class="num">' +
      data.impedanceOhm.toFixed(2) + ' Ω</b> <span class="muted">（有效 Dk ' +
      data.effectiveDk.toFixed(3) + '）</span></div><div class="legend">' + ratioInfo +
      '</div>';
  } else {
    box.innerHTML = '<div class="diag" style="margin-top:8px"><b>当前几何离开公式适用域，' +
      '不提供阻抗数字。</b><br>' + data.violations.map(v => '• ' + esc(v.rendered)).join('<br>') +
      '</div><div class="legend">' + ratioInfo + '</div>';
  }
}

function lenInput(key, label, val) {
  return '<label class="f"><span>' + label + '</span><div class="row3">' +
    '<input data-k="' + key + '.value" type="number" step="any" value="' + (val ? val.value : 0) + '"/>' +
    '<select data-k="' + key + '.unit">' +
    ['mil','um','mm','m'].map(u => '<option ' + (val && val.unit === u ? 'selected' : '') +
      '>' + u + '</option>').join('') + '</select></div></label>';
}

function tolEditor(key, label, t, scalar) {
  t = t || {};
  const dist = t.distribution || 'UNIFORM';
  const rel = t.relativePercent != null ? t.relativePercent : '';
  const abs = t.absolute || { value: '', unit: 'um' };
  const oneSided = t.oneSided ? 'checked' : '';
  return '<details class="tol" ' + (t.group ? 'open' : '') + '><summary>' + esc(label) +
    (t.group ? '：' + esc(t.group) : '（未配置）') + '</summary>' +
    '<div class="row2">' +
    '<label class="f"><span>关联组 id</span><input data-k="' + key + '.group" value="' +
      esc(t.group || '') + '" placeholder="如 PRESS-LAM-01"/></label>' +
    '<label class="f"><span>材料/工艺批次</span><input data-k="' + key + '.sourceLot" value="' +
      esc(t.sourceLot || '') + '" placeholder="如 LOT-PRESS-..."/></label></div>' +
    '<div class="row2"><label class="f"><span>分布</span><select data-k="' + key +
      '.distribution">' +
      ['UNIFORM','NORMAL','EXTREME'].map(d => '<option ' + (dist === d ? 'selected' : '') +
        '>' + d + '</option>').join('') + '</select></label>' +
    '<label class="f"><span>' + (scalar ? '相对容差 %' : '相对容差 %（与绝对量二选一）') +
      '</span><input data-k="' + key + '.relativePercent" type="number" step="any" value="' +
      rel + '"/></label></div>' +
    (scalar ? '' : '<div class="row3"><label class="f"><span>绝对容差值</span>' +
      '<input data-k="' + key + '.absolute.value" type="number" step="any" value="' + abs.value +
      '"/></label><label class="f"><span>单位</span><select data-k="' + key +
      '.absolute.unit">' + ['mil','um','mm','m'].map(u => '<option ' +
      (abs.unit === u ? 'selected' : '') + '>' + u + '</option>').join('') +
      '</select></label></div>') +
    '<label class="f" style="color:inherit"><input type="checkbox" data-k="' + key +
      '.oneSided" style="width:auto" ' + oneSided + '/> 单侧（只减小目标量，用于蛇形扇贝）</label>' +
    '</details>';
}

function renderEditor() {
  const s = state.currentSpec;
  const diff = s.traceType.endsWith('DIFF');
  const micro = s.traceType.startsWith('MICROSTRIP');
  let geo = '';
  GEO_FIELDS.forEach(f => {
    if (f.onlyDiff && !diff) { return; }
    if (f.onlyMicro && !micro) { return; }
    geo += lenInput(f.key, f.label, s[f.key]);
  });
  let tol = '';
  TOL_KEYS.forEach(([key, label]) => {
    if (key === 'pairSpacingTolerance' && !diff) { return; }
    if ((key === 'solderMaskDkTolerance' || key === 'maskThicknessTolerance') && !micro) { return; }
    tol += tolEditor(key, label, s[key], key === 'dielectricConstantTolerance' ||
      key === 'solderMaskDkTolerance');
  });
  el('editorBox').innerHTML =
    '<div class="row2"><label class="f"><span>叠层名称</span><input data-k="name" value="' +
      esc(s.name) + '"/></label>' +
    '<label class="f"><span>材料版本</span><input data-k="materialVersion" value="' +
      esc(s.materialVersion) + '"/></label></div>' +
    '<label class="f"><span>走线类型</span><select data-k="traceType">' +
      TRACE_TYPES.map(t => '<option value="' + t + '" ' + (s.traceType === t ? 'selected' : '') +
      '>' + TYPE_LABEL[t] + '</option>').join('') + '</select></label>' +
    geo +
    '<div class="row2">' +
    '<label class="f"><span>介电常数 Dk</span><input data-k="dielectricConstant" type="number" step="any" value="' + s.dielectricConstant + '"/></label>' +
    '<label class="f"><span>阻焊层 Dk（仅微带）</span><input data-k="solderMaskDk" type="number" step="any" value="' + s.solderMaskDk + '"/></label>' +
    '</div>' +
    '<h2 style="margin-top:12px">容差（制板厂口径）</h2>' + tol;
}

function collectDraft() {
  const draft = {};
  document.querySelectorAll('[data-k]').forEach(node => {
    const path = node.getAttribute('data-k').split('.');
    let cur = draft;
    for (let i = 0; i < path.length - 1; i++) {
      cur[path[i]] = cur[path[i]] || {};
      cur = cur[path[i]];
    }
    const leaf = path[path.length - 1];
    if (node.type === 'checkbox') { cur[leaf] = node.checked; }
    else if (node.tagName === 'SELECT' || node.type !== 'number') { cur[leaf] = node.value; }
    else { cur[leaf] = node.value === '' ? null : Number(node.value); }
  });
  // 清洗：空容差视为 null；relative/absolute 二选一
  TOL_KEYS.forEach(([key]) => {
    const t = draft[key];
    if (!t || !t.group) { delete draft[key]; return; }
    if (t.relativePercent == null || Number.isNaN(t.relativePercent)) { delete t.relativePercent; }
    if (t.absolute && (t.absolute.value === '' || t.absolute.value == null)) { delete t.absolute; }
    if (!t.relativePercent && !t.absolute) {
      throw new Error('容差「' + key + '」必须给相对百分比或绝对量');
    }
  });
  return draft;
}

async function saveRevision() {
  try {
    const draft = collectDraft();
    const created = await api('/api/stackups/' + state.current + '/revision', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(draft)
    });
    await loadStackups(created.id);
    alert('已创建新版本 ' + created.id + '；旧版本结论保持不变、不自动重算。');
  } catch (e) { alert('保存失败：' + e.message); }
}

async function newBlank() {
  const draft = {
    name: '新叠层-' + Date.now().toString().slice(-5),
    materialVersion: 'MATERIAL/r1', traceType: 'MICROSTRIP_SE',
    traceWidth: { value: 7, unit: 'mil' },
    copperThickness: { value: 35, unit: 'um' },
    dielectricHeight: { value: 0.12, unit: 'mm' },
    dielectricConstant: 4.2,
    solderMaskDk: 3.3, maskThickness: { value: 15, unit: 'um' }
  };
  try {
    const created = await api('/api/stackups', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(draft)
    });
    await loadStackups(created.id);
  } catch (e) { alert('创建失败：' + e.message); }
}

async function runAnalysis() {
  const body = {
    mode: el('mode').value,
    targetOhm: Number(el('targetZ').value),
    tolerancePercent: Number(el('targetTol').value),
    seed: Number(el('seed').value),
    sampleCount: Number(el('samples').value),
    independentExtremes: el('independent').checked
  };
  el('runStatus').textContent = '分析中…';
  try {
    const row = await api('/api/stackups/' + state.current + '/analyze', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    });
    state.report = row.report;
    renderReport();
    renderHistory();
    el('runStatus').textContent = '完成：运行 ' + row.id;
  } catch (e) {
    el('runStatus').textContent = '失败：' + e.message;
  }
}

function renderReport() {
  const r = state.report;
  const sum = el('summary');
  if (r.rejected) {
    sum.innerHTML = '<div class="reject"><b>分析被拒绝</b><br>' + esc(r.rejectionReason) + '</div>';
    el('points').innerHTML = '';
    return;
  }
  const rate = r.pointCount ? Math.round(r.passCount * 1000 / r.pointCount) / 10 : 0;
  sum.innerHTML =
    '<table><tr><th>模式</th><td>' + esc(r.mode) + (r.independentExtremesRequested ?
      '（独立极值）' : '（关联角落）') + '</td></tr>' +
    '<tr><th>点/样本数</th><td class="num">' + r.pointCount + '</td></tr>' +
    '<tr><th>通过 / 失败</th><td class="num"><span class="pass">' + r.passCount +
      '</span> / <span class="fail">' + r.failCount + '</span>（通过率 ' + rate + '%）</td></tr>' +
    '<tr><th>失败构成</th><td class="num">超阻抗带 ' + r.specFailureCount +
      '；公式越界 ' + r.domainFailureCount + '</td></tr>' +
    '<tr><th>阻抗范围</th><td class="num">' + fmtOhm(r.minOhm) + ' ~ ' + fmtOhm(r.maxOhm) +
      '（目标 ' + r.targetOhm + ' Ω ±' + r.tolerancePercent + '%）</td></tr>' +
    (r.seed != null ? '<tr><th>种子/样本</th><td class="num">' + r.seed + ' / ' +
      r.sampleCount + '</td></tr>' : '') + '</table>';
  renderPoints();
}

function traceTable(p) {
  return '<div class="trace-grid">' + Object.entries(p.traces).map(([k, tr]) => {
    const val = p.values[k];
    let valText;
    if (k === 'dk' || k === 'maskDk') { valText = Number(val).toFixed(4); }
    else { valText = (val * 1e6).toFixed(2) + ' µm'; }
    const dir = tr.direction != null ? (tr.direction > 0 ? '极值 +' :
      (tr.direction < 0 ? '极值 −' : '标称')) : ('r=' + Number(tr.r).toFixed(3));
    return '<div class="trace-cell"><b>' + esc(tr.label) + '</b>' +
      '取值 ' + valText + '<br><span class="muted">' + dir + '</span><br>' +
      '<span class="muted">组 ' + esc(tr.group) + '</span><br>' +
      '<span class="muted">批次 ' + esc(tr.sourceLot) + '</span></div>';
  }).join('') + '</div>';
}

function renderPoints() {
  const r = state.report;
  const rows = r.points.map((p, i) => {
    const z = p.inDomain
      ? Number(p.impedanceOhm).toFixed(2) + ' Ω（偏差 ' +
        (p.deviationPercent >= 0 ? '+' : '') + Number(p.deviationPercent).toFixed(1) + '%）'
      : '— 适用域失效 —';
    const diag = p.inDomain ? '' :
      '<div class="diag">' + p.violations.map(v => esc(v.rendered)).join('<br>') + '</div>';
    return '<details><summary><span class="' + (p.pass ? 'pass' : 'fail') + '">' +
      (p.pass ? '✓' : '✗') + '</span> ' + esc(p.label) +
      ' <span class="num muted">' + z + '</span> ' +
      (p.effectiveDk != null ? '<span class="muted">εe=' +
        Number(p.effectiveDk).toFixed(3) + '</span>' : '') + '</summary>' +
      diag + traceTable(p) + '</details>';
  }).join('');
  el('points').innerHTML = '<div class="legend" style="margin-bottom:6px">展开任意失败样本可回查' +
    '材料批次、关联组方向 / 抽样 r 值与该点各几何量实际取值。</div>' + rows;
}

async function renderHistory() {
  const rows = await api('/api/stackups/' + state.current + '/runs');
  el('history').innerHTML = rows.length ? rows.map(row => {
    const r = row.report;
    return '<div class="stack-row" style="cursor:default"><div class="meta num">' +
      esc(row.id) + '</div><div>' + esc(r.mode) + (r.seed != null ? ' seed=' + r.seed : '') +
      ' · <span class="' + (r.rejected || r.failCount > 0 ? 'fail' : 'pass') + '">' +
      (r.rejected ? '已拒绝' : (r.passCount + '/' + r.pointCount + ' 通过')) +
      '</span></div><div class="meta">目标 ' + r.targetOhm + ' Ω ±' +
      r.tolerancePercent + '% · ' + esc(row.createdAt) + '</div></div>';
  }).join('') : '<div class="muted">该版本尚无运行。</div>';
}

function showTab(tab) {
  state.tab = tab;
  el('tabFormula').classList.toggle('on', tab === 'formula');
  el('tabEditor').classList.toggle('on', tab === 'editor');
  el('paneFormula').style.display = tab === 'formula' ? '' : 'none';
  el('paneEditor').style.display = tab === 'editor' ? '' : 'none';
}

async function exportData() {
  const bundle = await api('/api/export');
  const blob = new Blob([JSON.stringify(bundle, null, 2)], { type: 'application/json' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'lamiprover-export-' + new Date().toISOString().slice(0, 10) + '.json';
  a.click();
  URL.revokeObjectURL(a.href);
}

async function importData(event) {
  const file = event.target.files[0];
  if (!file) { return; }
  const text = await file.text();
  try {
    const bundle = JSON.parse(text);
    const res = await api('/api/import', {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(bundle)
    });
    alert('导入完成：叠层 ' + res.importedStackups + ' 个、运行 ' + res.importedRuns + ' 条。');
    state.current = null;
    await loadStackups();
  } catch (e) { alert('导入失败：' + e.message); }
  event.target.value = '';
}

async function resetAll() {
  if (!confirm('将清空全部叠层版本与运行记录（建议先导出）。确定？')) { return; }
  await api('/api/reset', { method: 'POST' });
  state.current = null;
  await loadStackups();
  el('section').innerHTML = ''; el('formulaBox').innerHTML = '';
  el('editorBox').innerHTML = ''; el('nominalBox').innerHTML = '';
  el('groupList').innerHTML = '<span class="muted">数据库已清空。</span>';
  el('history').innerHTML = ''; el('summary').innerHTML = ''; el('points').innerHTML = '';
  alert('数据库已清空。可通过“导入复核”恢复导出包。');
}

loadStackups().catch(e => { document.body.insertAdjacentHTML('afterbegin',
  '<div class="reject">初始化失败：' + esc(e.message) + '</div>'); });
