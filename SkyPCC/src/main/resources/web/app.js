(() => {
  'use strict';

  const { t, code } = window.PccI18n;
  let connectionLabel = 'Connecting';
  let eventStatusKey = 'Connecting';
  let switchResultState = { key: 'Ready' };

  const NS = 'http://www.w3.org/2000/svg';
  const state = {
    graph: null,
    bounds: null,
    edges: new Map(),
    nodes: new Map(),
    trains: new Map(),
    renderedOffsets: new Map(),
    selectedTrainId: null,
    selectedTrainSnapshot: null,
    selectedSrTrainId: null,
    followTrainId: null,
    lineFilter: '',
    clockOffset: 0,
    view: { x: 0, y: 0, w: 100, h: 100 },
    fitted: false,
    dragging: null,
    lastGraphCheck: 0,
    updateMode: 'auto',
    pollIntervalMillis: 1000,
    labelScale: loadLabelScale()
  };
  let eventSource = null;
  let pollTimer = null;
  let pollGeneration = 0;
  let sseRetryTimer = null;
  let labelLayoutFrame = null;
  let lastLabelLayoutAt = 0;
  let operations = { status: 'UNAVAILABLE', session: '', latestSequence: 0, evictedCount: 0, events: [] };
  let operationsKey = '';
  let shadowMa = null;
  let operationalMa = null;
  let switchRequest = null;
  let switchPending = false;
  let srRequest = null;
  let srPending = false;

  const ui = Object.fromEntries([
    'railMap', 'edgeLayer', 'switchLayer', 'nodeLayer', 'labelLayer', 'trainLayer', 'lineFilter',
    'fitButton', 'zoomIn', 'zoomOut', 'fontDecrease', 'fontSizeValue', 'fontIncrease',
    'connectionDot', 'connectionText', 'themeToggle', 'brandLogo',
    'graphRevision', 'trainCount', 'activeCount', 'trainList', 'emptyState',
    'inspectorBack', 'inspectorEmpty', 'inspector', 'detailName', 'detailStatus', 'detailMode',
    'detailDriver', 'detailLine', 'detailMileage', 'detailSpeed', 'detailEdge', 'detailAge',
    'detailAtp', 'detailDirection', 'detailReverser', 'detailHandle', 'detailBrakeHold',
    'cameraControls', 'followTrain', 'cancelFollow', 'followStatus',
    'eventPanel', 'eventCount', 'eventStatus', 'eventFilter', 'eventRetention', 'eventList',
    'authorityLayer', 'detailMa', 'detailEoa', 'detailMaReason', 'maStatus',
    'switchDialog', 'switchTitle', 'switchTransition', 'switchToken', 'switchResult', 'switchConfirm', 'switchClose',
    'srControls', 'srTrainSelect', 'srApprove', 'srDialog', 'srTitle', 'srTarget', 'srToken', 'srResult', 'srConfirm', 'srClose'
  ].map(id => [id, document.getElementById(id)]));

  function setTheme(theme) {
    document.documentElement.dataset.theme = theme;
    const light = theme === 'light';
    ui.brandLogo.src = light ? '/assets/day_logo.png' : '/assets/night_logo.png';
    ui.themeToggle.textContent = light ? '☾' : '☀';
    ui.themeToggle.title = light ? t('Switch to dark theme') : t('Switch to light theme');
    ui.themeToggle.setAttribute('aria-label', ui.themeToggle.title);
    try { localStorage.setItem('skypcc.theme', theme); } catch (_error) { /* Optional storage. */ }
  }

  function loadTheme() {
    try { return localStorage.getItem('skypcc.theme') === 'light' ? 'light' : 'dark'; }
    catch (_error) { return 'dark'; }
  }

  function loadLabelScale() {
    try {
      const stored = Number(localStorage.getItem('skypcc.labelScale') ?? 1.5);
      return Number.isFinite(stored) ? Math.max(0.75, Math.min(3, stored)) : 1.5;
    } catch (_error) {
      return 1.5;
    }
  }

  function setLabelScale(value) {
    state.labelScale = Math.max(0.75, Math.min(3, Math.round(value * 4) / 4));
    ui.fontSizeValue.textContent = `${Math.round(state.labelScale * 100)}%`;
    ui.fontDecrease.disabled = state.labelScale <= 0.75;
    ui.fontIncrease.disabled = state.labelScale >= 3;
    try {
      localStorage.setItem('skypcc.labelScale', String(state.labelScale));
    } catch (_error) {
      // The control still works when browser storage is unavailable.
    }
    updateScaleStyles();
  }

  function svg(tag, attrs = {}) {
    const element = document.createElementNS(NS, tag);
    Object.entries(attrs).forEach(([key, value]) => element.setAttribute(key, value));
    return element;
  }

  async function fetchJson(url) {
    const response = await fetch(url, { cache: 'no-store' });
    if (!response.ok) throw new Error(`${url}: HTTP ${response.status}`);
    return response.json();
  }

  async function loadGraph(force = false) {
    if (!force && Date.now() - state.lastGraphCheck < 2000) return;
    state.lastGraphCheck = Date.now();
    const graph = await fetchJson('/api/v5/graph');
    if (!graph || !Array.isArray(graph.edges)) return;
    const sameTopology = !force && state.graph && state.graph.revision === graph.revision;
    state.graph = graph;
    state.edges = new Map(graph.edges.map(edge => [edge.id, edge]));
    graph.nodes = (graph.nodes || []).map(node => ({ ...node, type: String(node.type || '').toLowerCase() }));
    state.nodes = new Map(graph.nodes.map(node => [node.id, node]));
    renderPendingSr();
    if (sameTopology) {
      renderSwitchDirections();
      return;
    }
    state.bounds = calculateBounds(graph);
    ui.graphRevision.textContent = graph.revision ?? '--';
    populateLines();
    renderGraph();
    fitGraph();
  }

  function lineOfEdge(edge) {
    if (state.graph && (state.graph.lineModelVersion != null || state.graph.lineAssignments != null)) {
      const assignment = state.graph.lineAssignments?.[edge.id];
      return assignment?.status === 'CONFIRMED' && typeof assignment.line === 'string'
        ? assignment.line : '';
    }
    const from = state.nodes.get(edge.from);
    const to = state.nodes.get(edge.to);
    // Legacy graphs have no edge ownership: never spread a single endpoint's line.
    return from?.line && from.line === to?.line ? from.line : '';
  }

  function lineColor(line) {
    const palette = ['#55c1d4', '#f0b45a', '#72ca8b', '#d97893', '#9e83e8', '#d5d868', '#6fa7e8'];
    let hash = 0;
    for (const char of line || 'unassigned') hash = ((hash * 31) + char.charCodeAt(0)) >>> 0;
    return palette[hash % palette.length];
  }

  function populateLines() {
    const previous = state.lineFilter;
    const lines = [...new Set((state.graph.nodes || []).map(node => node.line).filter(Boolean))]
      .sort((a, b) => a.localeCompare(b));
    ui.lineFilter.replaceChildren(new Option(t('All lines'), ''));
    lines.forEach(line => ui.lineFilter.add(new Option(line, line)));
    ui.lineFilter.value = lines.includes(previous) ? previous : '';
    state.lineFilter = ui.lineFilter.value;
  }

  function renderGraph() {
    ui.edgeLayer.replaceChildren();
    ui.switchLayer.replaceChildren();
    ui.nodeLayer.replaceChildren();
    ui.labelLayer.replaceChildren();
    const hasGraph = state.graph && state.graph.edges.length > 0;
    ui.emptyState.hidden = hasGraph;
    if (!hasGraph) return;

    state.graph.edges.forEach(edge => {
      if (!edge.path || edge.path.length < 2) return;
      const line = lineOfEdge(edge);
      const path = svg('polyline', {
        points: edge.path.map(point => `${point.x},${point.z}`).join(' '),
        class: `rail-edge${state.lineFilter && line !== state.lineFilter ? ' dimmed' : ''}`,
        stroke: lineColor(line),
        'data-edge-id': edge.id
      });
      ui.edgeLayer.append(path);
      const hit = svg('polyline', { points: path.getAttribute('points'), class: 'edge-hit' });
      inspectClick(hit, 'edge', edge.id);
      ui.edgeLayer.append(hit);
    });

    (state.graph.nodes || []).forEach(node => {
      if (!node.rail) return;
      const dimmed = state.lineFilter && node.line !== state.lineFilter;
      const marker = svg('circle', {
        cx: node.rail.x,
        cy: node.rail.z,
        r: 3.2,
        class: `rail-node ${node.type || ''}${dimmed ? ' dimmed' : ''}`,
        'data-radius': node.type === 'switch' ? '4.2' : '3.2'
      });
      const title = svg('title');
      title.textContent = `${node.name || node.id}\n${code(node.type || 'marker')}\n${node.line || t('Unassigned')}`;
      marker.append(title);
      if (node.type === 'switch') switchClick(marker, node.id);
      else inspectClick(marker, 'node', node.id);
      ui.nodeLayer.append(marker);

      const label = svg('text', {
        x: node.rail.x + 5,
        y: node.rail.z - 5,
        class: `node-label${node.type === 'switch' ? ' switch-label' : ''}${dimmed ? ' dimmed' : ''}`,
        'data-node-x': node.rail.x,
        'data-node-z': node.rail.z,
        'data-priority': node.type === 'switch' ? '0' : node.type === 'station' ? '1' : '2'
      });
      label.textContent = node.name || node.id;
      if (node.type === 'switch') {
        const group = svg('g');
        switchClick(group, node.id);
        group.append(svg('rect', { class: `switch-label-bg${dimmed ? ' dimmed' : ''}` }), label);
        ui.labelLayer.append(group);
      } else { inspectClick(label, 'node', node.id); ui.labelLayer.append(label); }
    });
    renderSwitchDirections();
    renderAuthority();
    updateScaleStyles();
  }

  function faceVector(face) {
    const value = String(face || '').toLowerCase();
    const vectors = {
      north: [0, -1], north_north_east: [0.5, -1], north_east: [1, -1],
      east_north_east: [1, -0.5], east: [1, 0], east_south_east: [1, 0.5],
      south_east: [1, 1], south_south_east: [0.5, 1], south: [0, 1],
      south_south_west: [-0.5, 1], south_west: [-1, 1], west_south_west: [-1, 0.5],
      west: [-1, 0], west_north_west: [-1, -0.5], north_west: [-1, -1],
      north_north_west: [-0.5, -1]
    };
    return vectors[value] || [0, 0];
  }

  function arrowForVector([x, z]) {
    if (Math.abs(x) < 0.25 && z < 0) return '↑';
    if (Math.abs(x) < 0.25 && z > 0) return '↓';
    if (x > 0 && Math.abs(z) < 0.25) return '→';
    if (x < 0 && Math.abs(z) < 0.25) return '←';
    if (x > 0 && z < 0) return '↗';
    if (x > 0 && z > 0) return '↘';
    if (x < 0 && z > 0) return '↙';
    if (x < 0 && z < 0) return '↖';
    return '·';
  }

  function renderSwitchDirections() {
    ui.switchLayer.replaceChildren();
    state.nodes.forEach(node => {
      if (node.type !== 'switch' || !node.rail || !node.ports) return;
      const switchState = ['straight', 'diverging'].includes(String(node.state).toLowerCase())
        ? String(node.state).toLowerCase() : 'unknown';
      const face = node.ports[switchState];
      const vector = faceVector(face);
      const direction = svg('text', {
        class: `switch-direction ${switchState}`,
        'data-node-x': node.rail.x,
        'data-node-z': node.rail.z,
        'data-vector-x': vector[0],
        'data-vector-z': vector[1]
      });
      direction.textContent = switchState === 'unknown' ? '?' : arrowForVector(vector);
      switchClick(direction, node.id);
      const title = svg('title');
      title.textContent = `${node.name || node.id}: ${code(switchState)} (${code(face || 'unknown')})`;
      direction.append(title);
      ui.switchLayer.append(direction);
    });
    updateScaleStyles();
  }

  function calculateBounds(graph) {
    const points = (graph?.edges || []).flatMap(edge => edge.path || []);
    if (!points.length) return null;
    let minX = Infinity, maxX = -Infinity, minZ = Infinity, maxZ = -Infinity;
    points.forEach(point => {
      minX = Math.min(minX, point.x);
      maxX = Math.max(maxX, point.x);
      minZ = Math.min(minZ, point.z);
      maxZ = Math.max(maxZ, point.z);
    });
    return { minX, minZ, width: Math.max(20, maxX - minX), height: Math.max(20, maxZ - minZ) };
  }

  function graphBounds() {
    return state.bounds;
  }

  function fitGraph() {
    const bounds = graphBounds();
    if (!bounds) return;
    const margin = Math.max(18, Math.max(bounds.width, bounds.height) * 0.05);
    state.view = {
      x: bounds.minX - margin,
      y: bounds.minZ - margin,
      w: bounds.width + margin * 2,
      h: bounds.height + margin * 2
    };
    state.fitted = true;
    applyView();
  }

  function applyView() {
    const { x, y, w, h } = state.view;
    ui.railMap.setAttribute('viewBox', `${x} ${y} ${w} ${h}`);
    updateScaleStyles();
  }

  function unitPerPixel() {
    const rect = ui.railMap.getBoundingClientRect();
    return Math.max(state.view.w / Math.max(1, rect.width), state.view.h / Math.max(1, rect.height));
  }

  function highlightLabel(text) {
    const background = text.previousElementSibling;
    if (!background?.classList.contains('switch-label-bg')) return;
    const box = text.getBBox(), padding = 3 * unitPerPixel();
    for (const [key, value] of Object.entries({ x: box.x - padding, y: box.y - padding,
      width: box.width + padding * 2, height: box.height + padding * 2 })) {
      background.setAttribute(key, value);
    }
  }

  function updateScaleStyles() {
    const bounds = graphBounds();
    if (!bounds) return;
    const unit = unitPerPixel();
    ui.nodeLayer.querySelectorAll('.rail-node').forEach(node => {
      node.setAttribute('r', Number(node.dataset.radius || 3.2) * unit);
    });
    ui.labelLayer.querySelectorAll('.node-label').forEach(label => {
      label.setAttribute('font-size', 11 * state.labelScale * unit);
      label.setAttribute('stroke-width', 3 * state.labelScale * unit);
    });
    ui.switchLayer.querySelectorAll('.switch-direction').forEach(direction => {
      direction.setAttribute('x', Number(direction.dataset.nodeX)
        + Number(direction.dataset.vectorX) * 14 * unit);
      direction.setAttribute('y', Number(direction.dataset.nodeZ)
        + Number(direction.dataset.vectorZ) * 14 * unit);
      direction.setAttribute('font-size', 17 * state.labelScale * unit);
      direction.setAttribute('stroke-width', 3 * unit);
      highlightLabel(direction);
    });
    scheduleLabelLayout();
  }

  function scheduleLabelLayout() {
    if (labelLayoutFrame !== null) cancelAnimationFrame(labelLayoutFrame);
    labelLayoutFrame = requestAnimationFrame(() => {
      labelLayoutFrame = null;
      layoutNodeLabels();
    });
  }

  function overlapArea(a, b) {
    const width = Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left));
    const height = Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
    return width * height;
  }

  function paddedRect(rect, padding = 3) {
    return { left: rect.left - padding, top: rect.top - padding,
      right: rect.right + padding, bottom: rect.bottom + padding };
  }

  function layoutNodeLabels() {
    lastLabelLayoutAt = performance.now();
    if (ui.labelLayer.style.display === 'none') return;
    const unit = unitPerPixel();
    const viewport = ui.railMap.getBoundingClientRect();
    const occupied = [...ui.switchLayer.querySelectorAll('.switch-direction')]
      .map(element => paddedRect(element.getBoundingClientRect(), 4));
    ui.trainLayer.querySelectorAll('.train-label').forEach(label => {
      occupied.push(paddedRect(label.getBoundingClientRect(), 4));
    });
    const candidates = [
      [8, -8, 'start'], [8, 16, 'start'], [-8, -8, 'end'], [-8, 16, 'end'],
      [0, -14, 'middle'], [0, 22, 'middle'], [8, -26, 'start'], [-8, 34, 'end']
    ];
    const labels = [...ui.labelLayer.querySelectorAll('.node-label')]
      .sort((a, b) => Number(a.dataset.priority) - Number(b.dataset.priority));
    labels.forEach(label => {
      let best = null;
      for (const [dx, dy, anchor] of candidates) {
        label.setAttribute('x', Number(label.dataset.nodeX) + dx * unit);
        label.setAttribute('y', Number(label.dataset.nodeZ) + dy * unit);
        label.setAttribute('text-anchor', anchor);
        const rect = paddedRect(label.getBoundingClientRect());
        const outside = Math.max(0, viewport.left + 4 - rect.left) + Math.max(0, rect.right - viewport.right + 4)
          + Math.max(0, viewport.top + 4 - rect.top) + Math.max(0, rect.bottom - viewport.bottom + 4);
        const score = occupied.reduce((sum, used) => sum + overlapArea(rect, used), 0) + outside * 1000;
        if (!best || score < best.score) best = { dx, dy, anchor, rect, score };
        if (score === 0) break;
      }
      label.setAttribute('x', Number(label.dataset.nodeX) + best.dx * unit);
      label.setAttribute('y', Number(label.dataset.nodeZ) + best.dy * unit);
      label.setAttribute('text-anchor', best.anchor);
      highlightLabel(label);
      const visibility = best.score === 0 ? 'visible' : 'hidden';
      label.style.visibility = visibility;
      if (label.previousElementSibling?.classList.contains('switch-label-bg')) {
        label.previousElementSibling.style.visibility = visibility;
      }
      if (best.score === 0) occupied.push(best.rect);
    });
  }

  function zoom(factor, clientX, clientY) {
    const rect = ui.railMap.getBoundingClientRect();
    const rx = (clientX - rect.left) / Math.max(1, rect.width);
    const ry = (clientY - rect.top) / Math.max(1, rect.height);
    const old = state.view;
    const nextW = Math.max(20, old.w * factor);
    const nextH = Math.max(20, old.h * factor);
    state.view = {
      x: old.x + old.w * rx - nextW * rx,
      y: old.y + old.h * ry - nextH * ry,
      w: nextW,
      h: nextH
    };
    applyView();
  }

  function updateTrainSamples(snapshot) {
    if (snapshot.schemaVersion !== 5) {
      snapshot = { schemaVersion: 5, serviceStatus: 'UNAVAILABLE', trains: [], shadowMa: null, operationalMa: null };
    }
    shadowMa = snapshot.shadowMa || null;
    operationalMa = snapshot.operationalMa || null;
    updateOperations(snapshot.operationalEvents);
    state.serviceStatus = snapshot.serviceStatus || 'AVAILABLE';
    state.lastSnapshotAt = Date.now();
    state.clockOffset = Number(snapshot.serverTimeMillis || Date.now()) - Date.now();
    renderAuthority();
    const next = new Map();
    (snapshot.trains || []).forEach(train => next.set(train.trainId, train));
    state.trains = next;
    if (state.selectedTrainId && next.has(state.selectedTrainId)) {
      state.selectedTrainSnapshot = next.get(state.selectedTrainId);
    }
    for (const id of state.renderedOffsets.keys()) {
      if (!next.has(id)) state.renderedOffsets.delete(id);
    }
    ui.trainCount.textContent = String(next.size);
    ui.activeCount.textContent = t('{n} active', { n: [...next.values()].filter(t => !t.stale).length });
    renderTrainList();
    renderPendingSr();
    updateInspector();
    if (snapshot.graphRevision !== state.graph?.revision) loadGraph(true).catch(showDisconnected);
  }

  function liveOperational() {
    const age = Date.now() + state.clockOffset - Number(operationalMa?.emittedAtMillis || 0);
    return operationalMa?.version === 6 && operationalMa.status === 'AVAILABLE'
      && operationalMa.graphRevision === state.graph?.revision
      && age >= 0 && age <= 1500 ? operationalMa : null;
  }

  function renderPendingSr() {
    const pending = liveOperational()?.pendingSr || [];
    ui.srControls.hidden = pending.length === 0;
    if (!pending.some(item => item.trainId === state.selectedSrTrainId)) state.selectedSrTrainId = null;
    ui.srTrainSelect.replaceChildren();
    const prompt = document.createElement('option');
    prompt.value = ''; prompt.textContent = t('Pending SR'); ui.srTrainSelect.append(prompt);
    for (const item of pending) {
      const option = document.createElement('option');
      option.value = item.trainId;
      option.textContent = item.trainName || item.trainId;
      ui.srTrainSelect.append(option);
    }
    ui.srTrainSelect.value = state.selectedSrTrainId || '';
  }

  function updateOperations(report) {
    if (!report || !Array.isArray(report.events)) {
      setEventStatus('Events unavailable');
      return;
    }
    const key = `${report.session}:${report.latestSequence}:${report.status}`;
    setEventStatus(report.status === 'AVAILABLE' ? 'Live' : 'Unavailable / cached');
    if (key === operationsKey) return;
    operationsKey = key;
    operations = report;
    renderOperations();
  }

  function renderOperations() {
    const unique = new Map();
    for (const event of operations.events) unique.set(`${event.session}:${event.sequence}`, event);
    const events = [...unique.values()].sort((a, b) => b.sequence - a.sequence);
    ui.eventCount.textContent = t('{n} events', { n: events.length });
    ui.eventRetention.textContent = operations.evictedCount > 0 ? t('{n} older events expired', { n: operations.evictedCount }) : '';
    const fragment = document.createDocumentFragment();
    let count = 0;
    for (const event of events) {
      const warning = event.type === 'DRIVER_UNAVAILABLE' || event.type === 'SWITCH_RUN_THROUGH_SUSPECTED'
        || event.type === 'MA_UNAVAILABLE'
        || (event.type === 'EMERGENCY_BRAKE_APPLIED' && event.reason !== 'EB_INPUT');
      if (ui.eventFilter.value === 'warning' && !warning) continue;
      count++;
      const row = document.createElement('div');
      row.className = `event-row${warning ? ' warning' : ''}`;
      const time = document.createElement('time');
      const date = new Date(event.emittedAtMillis);
      time.textContent = date.toLocaleTimeString(PccI18n.language, { hour12: false });
      time.title = date.toLocaleString(PccI18n.language);
      const severity = document.createElement('span');
      severity.className = 'event-severity';
      severity.textContent = warning ? t('Warning') : t('Info');
      const train = document.createElement('button');
      train.type = 'button'; train.textContent = event.trainName || event.details?.switchName || event.trainId || event.details?.switchId || '--';
      train.title = event.trainId || event.details?.switchId || '';
      train.addEventListener('click', () => {
        if (state.trains.has(event.trainId)) selectTrain(event.trainId);
      });
      const message = document.createElement('span');
      message.className = 'event-message';
      const driver = event.driverName || event.driverId || '--';
      const reason = code(event.reason);
      if (event.type === 'SWITCH_CHANGED') {
        const d = event.details || {};
        message.textContent = t('Switch {name}: {from} → {to} ({reason}). Conversion completed.',
          { name: d.switchName || d.switchId || '--', from: code(d.previous), to: code(d.next), reason });
      } else if (event.type === 'SWITCH_RUN_THROUGH_SUSPECTED') {
        const d = event.details || {};
        message.textContent = t('Possible switch run-through at {name}: observed {entry} entry, last confirmed position {position}. Inspect the switch.',
          { name: d.switchName || d.switchId || '--', entry: code(d.entry), position: code(d.previous) });
      } else if (event.type === 'EMERGENCY_BRAKE_APPLIED') message.textContent = t('EB state entered ({reason}).', { reason });
      else if (event.type === 'DRIVER_UNAVAILABLE') message.textContent = t('Driver unavailable: {driver} ({reason}). Control revoked; EB commanded.', { driver, reason });
      else if (event.type === 'DRIVER_ACQUIRED') message.textContent = t('{driver} acquired control via /st drive. EB retained until handle input.', { driver });
      else if (event.type === 'DRIVER_RELEASED') message.textContent = t('{driver} released control ({reason}). EB commanded.', { driver, reason });
      else if (event.type === 'MA_REQUESTED') message.textContent = t('{driver} requested {mode} MA. Result: {state} ({reason}); executable: {executable}.', {
        driver, mode: code(event.details?.mode || '--'), state: code(event.details?.state || 'UNKNOWN'),
        reason, executable: code(event.details?.executable || 'false') });
      else if (event.type === 'MA_RELEASED') message.textContent = t('{driver} released forward MA reservation. Train occupancy retained.', { driver });
      else if (event.type === 'MA_UNAVAILABLE') message.textContent = t('Executable MA unavailable for {train} ({reason}). Stop at the last confirmed EoA.', {
        train: event.trainName || event.trainId, reason });
      else if (event.type === 'SR_GRANTED') message.textContent = t('{operator} approved SR for {train} to {node}.', {
        operator: driver, train: event.trainName || event.trainId, node: event.details?.targetNodeId || '--' });
      else if (event.type === 'ATP_MODE_CHANGED') message.textContent = t('{operator} changed ATP mode: {from} → {to}.', { operator: event.details?.actorName || event.details?.actorId || '--', from: code(event.details?.previous || 'UNKNOWN'), to: code(event.details?.next || 'UNKNOWN') });
      else message.textContent = `${event.source}: ${code(event.type)} (${reason})`;
      message.title = `${event.type} / ${event.reason || '--'}`;
      row.append(time, severity, train, message);
      fragment.append(row);
    }
    if (!count) {
      const empty = document.createElement('p'); empty.className = 'event-empty';
      empty.textContent = ui.eventFilter.value === 'warning' ? t('No warnings') : t('No operational events');
      fragment.append(empty);
    }
    const scroll = ui.eventList.scrollTop;
    ui.eventList.replaceChildren(fragment);
    ui.eventList.scrollTop = scroll;
  }

  function predictedOffset(train) {
    let offset = Number(train.edgeOffsetMeters || 0);
    if (!train.stale && train.graphCurrent && train.moving) {
      const serverNow = Date.now() + state.clockOffset;
      const elapsed = Math.max(0, Math.min(7, (serverNow - Number(train.observedAtMillis || serverNow)) / 1000));
      offset += Number(train.speedMetersPerSecond || 0) * elapsed;
    }
    return Math.max(0, Math.min(Number(train.edgeLengthMeters || offset), offset));
  }

  function pointOnEdge(edge, distance) {
    const path = edge?.path;
    if (!path || !path.length) return null;
    if (path.length === 1) return { x: path[0].x, z: path[0].z, angle: 0 };
    const target = Math.max(0, Math.min(Number(edge.distanceMeters || distance), distance));
    for (let index = 1; index < path.length; index++) {
      const before = path[index - 1], after = path[index];
      if (Number(after.distanceMeters) + 0.0001 < target) continue;
      const span = Math.max(0.0001, Number(after.distanceMeters) - Number(before.distanceMeters));
      const ratio = Math.max(0, Math.min(1, (target - Number(before.distanceMeters)) / span));
      const x = before.x + (after.x - before.x) * ratio;
      const z = before.z + (after.z - before.z) * ratio;
      return { x, z, angle: Math.atan2(after.z - before.z, after.x - before.x) * 180 / Math.PI };
    }
    const before = path[path.length - 2], after = path[path.length - 1];
    return { x: after.x, z: after.z, angle: Math.atan2(after.z - before.z, after.x - before.x) * 180 / Math.PI };
  }

  function renderTrains() {
    if (state.lastSnapshotAt && Date.now() - state.lastSnapshotAt > Math.max(3000, state.pollIntervalMillis * 3)
        && state.serviceStatus !== 'TRANSPORT_STALE') {
      state.serviceStatus = 'TRANSPORT_STALE';
      setEventStatus('Disconnected / cached');
      for (const train of state.trains.values()) { train.stale = true; train.quality = 'STALE'; }
      showConnected();
      renderTrainList();
    }
    ui.trainLayer.replaceChildren();
    state.trains.forEach(train => {
      const edge = state.edges.get(train.edgeId);
      const targetOffset = predictedOffset(train);
      const remembered = state.renderedOffsets.get(train.trainId);
      const displayOffset = remembered && remembered.edgeId === train.edgeId
        ? remembered.offset + (targetOffset - remembered.offset) * 0.18
        : targetOffset;
      state.renderedOffsets.set(train.trainId, { edgeId: train.edgeId, offset: displayOffset });
      const point = pointOnEdge(edge, displayOffset);
      if (!point) return;
      if (state.followTrainId === train.trainId && train.graphCurrent && !train.stale && !state.dragging) {
        const x = point.x - state.view.w / 2, y = point.z - state.view.h / 2;
        if (Math.abs(state.view.x - x) + Math.abs(state.view.y - y) > 0.00001) {
          state.view.x = x; state.view.y = y;
          applyView();
        }
      }
      const hidden = state.lineFilter && train.line !== state.lineFilter;
      const classes = ['train-marker'];
      if (train.stale) classes.push('stale');
      if (!train.graphCurrent) classes.push('unmapped');
      if (state.selectedTrainId === train.trainId) classes.push('selected');
      const group = svg('g', {
        class: classes.join(' '),
        transform: `translate(${point.x} ${point.z})`,
        opacity: hidden ? '0.15' : '1',
        'data-train-id': train.trainId
      });
      const unit = unitPerPixel();
      const marker = svg('g', { transform: `rotate(${point.angle}) scale(${unit})` });
      marker.append(svg('circle', { class: 'halo', r: 7 }));
      marker.append(svg('path', { class: 'body', d: 'M -3.5 -4.5 L 5.5 0 L -3.5 4.5 Z' }));
      group.append(marker);
      const label = svg('text', {
        class: 'train-label',
        x: (8 + 2 * state.labelScale) * unit,
        y: -(7 + 2 * state.labelScale) * unit,
        'font-size': 12 * state.labelScale * unit,
        'stroke-width': 4 * state.labelScale * unit
      });
      const speedLabel = !train.stale && Number.isFinite(train.speedMetersPerSecond)
        ? (Math.max(0, train.speedMetersPerSecond) * 3.6).toFixed(1) : '--';
      label.textContent = `${train.trainNumber || '--'} | ${train.name || train.trainId.slice(0, 8)} | ${speedLabel} km/h`;
      group.append(label);
      group.addEventListener('click', event => {
        event.stopPropagation();
        selectTrain(train.trainId);
      });
      ui.trainLayer.append(group);
    });
    updateInspector();
    if (performance.now() - lastLabelLayoutAt > 200) scheduleLabelLayout();
    requestAnimationFrame(renderTrains);
  }

  function renderTrainList() {
    ui.trainList.replaceChildren();
    const trains = [...state.trains.values()].sort((a, b) => (a.name || '').localeCompare(b.name || ''));
    trains.forEach(train => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = `train-row${state.selectedTrainId === train.trainId ? ' selected' : ''}`;
      const swatch = document.createElement('span');
      swatch.className = 'train-swatch';
      swatch.style.background = train.stale ? '#f1c75b' : (!train.graphCurrent ? '#ed6a6a' : lineColor(train.line));
      const text = document.createElement('span');
      const name = document.createElement('strong');
      name.textContent = train.name || train.trainId.slice(0, 8);
      const detail = document.createElement('small');
      detail.textContent = train.quality === 'AWAITING_POSITION' ? t('Awaiting position')
        : `${train.line || t('Unassigned')} / ${train.mode === 'automatic' ? t('Automatic') : (train.driver || t('No driver'))}`;
      text.append(name, detail);
      const speed = document.createElement('span');
      speed.className = 'speed';
      speed.textContent = Number.isFinite(train.speedMetersPerSecond)
        ? `${(train.speedMetersPerSecond * 3.6).toFixed(0)} km/h` : '--';
      button.append(swatch, text, speed);
      button.addEventListener('click', () => selectTrain(train.trainId));
      ui.trainList.append(button);
    });
  }

  function selectTrain(id) {
    state.selectedInfrastructure = null;
    if (id !== state.selectedTrainId) state.followTrainId = null;
    state.selectedTrainId = id;
    state.selectedTrainSnapshot = id ? (state.trains.get(id) || state.selectedTrainSnapshot) : null;
    renderTrainList();
    updateInspector();
  }

  function selectInfrastructure(kind, id) {
    state.selectedTrainId = null; state.selectedTrainSnapshot = null; state.followTrainId = null;
    state.selectedInfrastructure = { kind, id };
    renderTrainList(); updateInspector();
  }
  function inspectClick(element, kind, id) {
    element.classList.add('infrastructure-hit');
    element.setAttribute('tabindex', '0'); element.setAttribute('role', 'button');
    element.setAttribute('aria-label', `${kind === 'edge' ? t('Edge') : t('Infrastructure')} ${state.nodes.get(id)?.name || id}`);
    element.dataset.inspectId = id;
    const open = event => { event.stopPropagation(); selectInfrastructure(kind, id); };
    element.addEventListener('click', open);
    element.addEventListener('keydown', event => {
      if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(event); }
    });
  }
  function renderInfrastructure(panel, control) {
    const { kind, id } = state.selectedInfrastructure;
    const item = kind === 'edge' ? state.edges.get(id) : state.nodes.get(id);
    ui.srApprove.hidden = true;
    const pending = liveOperational()?.pendingSr?.find(entry => entry.trainId === state.selectedSrTrainId);
    if (kind === 'node' && item && pending && state.controlEnabled
        && ['balise', 'switch', 'origin', 'end'].includes(item.type)) {
      ui.srApprove.hidden = false;
      ui.srApprove.disabled = srPending;
      ui.srApprove.onclick = () => {
        if (srPending || !liveOperational()) return;
        srRequest = { requestId: crypto.randomUUID(), trainId: pending.trainId,
          targetNodeId: id, graphRevision: state.graph.revision };
        ui.srTarget.textContent = `${pending.trainName || pending.trainId} → ${item.name || id}`;
        ui.srToken.value = '';
        ui.srResult.textContent = t('Ready');
        ui.srConfirm.disabled = false;
        ui.srDialog.showModal();
      };
    }
    const rows = [[kind === 'edge' ? 'Edge' : 'Infrastructure', item?.name || id]];
    if (!item) rows.push(['Status', t('Unavailable')]);
    else {
      rows.push(['Type', kind === 'edge' ? t('Edge') : code(item.type)], ['Graph revision', String(state.graph.revision)]);
      if (kind === 'edge') {
        rows.push(['Line', lineOfEdge(item) || t('Unassigned')], ['Length', `${item.distanceMeters.toFixed(1)} m`],
          ['Endpoints', `${state.nodes.get(item.from)?.name || item.from} (${item.sourcePort || '--'}) → ${state.nodes.get(item.to)?.name || item.to} (${item.targetPort || '--'})`]);
      } else {
        rows.push(['Line', item.line || t('Unassigned')]);
        if (Number.isFinite(item.mileageMeters)) {
          const km = Math.floor(item.mileageMeters / 1000);
          rows.push(['Mileage', `K${km}+${(item.mileageMeters - km * 1000).toFixed(1).padStart(5, '0')}`]);
        } else {
          const neighbors = new Map();
          for (const edge of state.edges.values()) {
            if (edge.from !== id && edge.to !== id) continue;
            const forward = edge.from === id, other = forward ? edge.to : edge.from;
            const port = forward ? edge.sourcePort : edge.targetPort;
            neighbors.set(`${port}:${other}`, `${code(port || 'UNKNOWN')} → ${state.nodes.get(other)?.name || other}: ${Number(edge.distanceMeters).toFixed(1)} m`);
          }
          for (const distance of neighbors.values()) rows.push(['Adjacent distance', distance]);
        }
        rows.push(['Coordinates', item.rail ? `${item.rail.world} ${item.rail.x}, ${item.rail.y}, ${item.rail.z}` : '--']);
        if (item.type === 'switch') {
          rows.push(['Graph snapshot', code(item.state || 'UNKNOWN')], ['Ports', Object.entries(item.ports || {}).map(([k,v]) => `${code(k)}: ${code(v)}`).join(' · ')],
            ['Transitions', (item.allowedTransitions || []).join(' · ') || '--']);
          control.hidden = false; control.textContent = t('Change switch');
          control.disabled = !state.controlEnabled || switchPending || !['straight','diverging'].includes(item.state);
          control.onclick = () => { const button = document.createElement('button'); switchClick(button, id, true); button.click(); };
        }
      }
      const live = liveAuthority();
      const sections = (live?.sections || []).filter(s => kind === 'edge' ? s.edgeId === id : (() => {
        const e = state.edges.get(s.edgeId);
        return e && ((e.from === id && (s.fromMeters == null || s.fromMeters <= 0.001))
          || (e.to === id && (s.toMeters == null || s.toMeters >= e.distanceMeters - 0.001)));
      })());
      const states = [...new Set(sections.map(s => code(s.state)))];
      rows.push(['Status', live && sections.length ? states.join(' · ') : code('UNKNOWN')]);
      const names = key => [...new Set(sections.flatMap(s => s[key] || []))].map(uuid => state.trains.get(uuid)?.name || uuid).join(', ') || '--';
      rows.push(['Occupants', live ? names('occupants') : '--'], ['Shadow reservations', live ? names('reservations') : '--']);
      if (kind === 'edge') for (const s of sections.filter(s => s.state !== 'UNALLOCATED'))
        rows.push(['Intervals', `${s.fromMeters == null ? '0' : s.fromMeters.toFixed(1)}–${s.toMeters == null ? item.distanceMeters.toFixed(1) : s.toMeters.toFixed(1)} m · ${code(s.state)}`]);
    }
    const signature = JSON.stringify([document.getElementById('languageSelect').value, rows]);
    if (panel.dataset.content === signature) return;
    panel.dataset.content = signature; panel.replaceChildren();
    for (const [key, value] of rows) {
      const row = document.createElement('div'), dt = document.createElement('dt'), dd = document.createElement('dd');
      dt.textContent = t(key); dd.textContent = value; row.append(dt, dd); panel.append(row);
    }
  }

  function updateInspector() {
    const infrastructure = document.getElementById('infrastructureInspector');
    const control = document.getElementById('infrastructureSwitch');
    infrastructure.hidden = !state.selectedInfrastructure;
    control.hidden = true;
    ui.srApprove.hidden = true;
    if (state.selectedInfrastructure) {
      ui.inspector.hidden = true; ui.inspectorEmpty.hidden = true; ui.inspectorBack.hidden = false;
      ui.cameraControls.hidden = true;
      document.querySelector('.workspace').classList.add('has-selection');
      renderInfrastructure(infrastructure, control);
      return;
    }
    const train = state.trains.get(state.selectedTrainId) || state.selectedTrainSnapshot;
    const available = Boolean(state.trains.get(state.selectedTrainId));
    ui.inspector.hidden = !train;
    ui.inspectorEmpty.hidden = Boolean(train);
    ui.inspectorBack.hidden = !train;
    ui.cameraControls.hidden = !train;
    document.querySelector('.workspace').classList.toggle('has-selection', Boolean(train));
    if (!train) return;
    const following = state.followTrainId === train.trainId;
    const positioned = available && train.graphCurrent && !train.stale && Boolean(state.edges.get(train.edgeId)?.path?.length);
    ui.followTrain.hidden = following;
    ui.followTrain.disabled = !positioned;
    ui.cancelFollow.hidden = !following;
    ui.followStatus.textContent = following ? (positioned ? t('Following') : t('Following / waiting for position')) : t('Camera free');
    ui.detailName.textContent = train.name || train.trainId;
    const status = !available ? t('Unavailable') : train.quality && train.quality !== 'VALID' ? code(train.quality) : !train.graphCurrent ? t('Graph mismatch')
      : train.stale ? t('Stale') : train.moving ? t('Running') : t('Stopped');
    ui.detailStatus.textContent = status;
    ui.detailStatus.className = !available || train.stale ? 'state-warn'
      : !train.graphCurrent ? 'state-error' : 'state-ok';
    ui.detailMode.textContent = ({ automatic: t('Automatic'), manual: t('Manual') })[train.mode] || '--';
    const cab = train.cab;
    const modes = { SHADOW: t('Shadow (unprotected)'), ACTIVE: t('Enforced'),
      ISOLATED: t('Isolated'), BYPASS: t('Bypass'), RECOVERING: t('Recovering') };
    ui.detailAtp.textContent = cab ? (modes[cab.atpMode] || cab.atpMode || '--') : '--';
    const authority = liveAuthority()?.authorities?.find(a => a.trainId === train.trainId);
    ui.detailMa.textContent = authority?.state === 'ALLOCATED_SHADOW'
      ? t('{n} m (shadow)', { n: Math.max(0, authority.signedRemainingMeters || 0).toFixed(1) }) : t('Not allocated');
    ui.detailEoa.textContent = authority?.eoaEdgeId
      ? `${t('{n} m remaining', { n: Number(authority.signedRemainingMeters).toFixed(1) })} · ${authority.eoaEdgeId} + ${Number(authority.eoaOffsetMeters).toFixed(1)} m` : '--';
    ui.detailMaReason.textContent = code(authority?.reason || 'UNAVAILABLE');
    ui.detailMaReason.title = authority?.reason || 'UNAVAILABLE';
    ui.detailDirection.textContent = ({ forward: t('Forward'), reverse: t('Reverse') })[train.direction] || '--';
    ui.detailReverser.textContent = ({ FORWARD: t('Forward'), NEUTRAL: t('Neutral'), BACKWARD: t('Reverse') })[cab?.reverser] || '--';
    ui.detailHandle.textContent = cab ? `P${cab.powerNotch} / ${cab.emergencyBrake ? 'EB' : `B${cab.brakeNotch}`}` : '--';
    ui.detailHandle.title = t('Handle input; brake hold can override traction');
    ui.detailBrakeHold.textContent = cab ? (cab.brakeHold ? t('Active') : t('Off')) : '--';
    ui.detailDriver.textContent = train.mode === 'automatic' ? t('Automatic') : (train.driver || '--');
    ui.detailLine.textContent = train.line || '--';
    ui.detailMileage.textContent = train.currentMileageMeters != null && Number.isFinite(Number(train.currentMileageMeters))
      ? `K${(Number(train.currentMileageMeters) / 1000).toFixed(3)}` : '--';
    ui.detailSpeed.textContent = Number.isFinite(train.speedMetersPerSecond)
      ? `${(train.speedMetersPerSecond * 3.6).toFixed(1)} km/h` : '--';
    ui.detailEdge.textContent = train.edgeId ? `${train.edgeId} @ ${Number(train.edgeOffsetMeters || 0).toFixed(1)} m` : '--';
    ui.detailAge.textContent = Number.isFinite(train.ageMillis) ? t('{n} s ago', { n: (train.ageMillis / 1000).toFixed(1) }) : '--';
  }

  function showConnected(label = 'Live') {
    connectionLabel = label;
    if (state.serviceStatus && state.serviceStatus !== 'AVAILABLE') {
      ui.connectionDot.className = 'status-dot failed';
      ui.connectionText.textContent = code(state.serviceStatus);
      return;
    }
    ui.connectionDot.className = 'status-dot connected';
    ui.connectionText.textContent = t(label);
  }

  function showDisconnected(error) {
    setEventStatus('Disconnected / cached');
    for (const train of state.trains.values()) { train.stale = true; train.quality = 'SOURCE_UNAVAILABLE'; }
    ui.connectionDot.className = 'status-dot failed';
    connectionLabel = 'Disconnected';
    ui.connectionText.textContent = t(connectionLabel);
    console.error(error);
  }

  function stopPolling() {
    pollGeneration++;
    if (pollTimer !== null) clearTimeout(pollTimer);
    pollTimer = null;
  }

  function startPolling() {
    stopPolling();
    const generation = pollGeneration;
    const cycle = async () => {
      try {
        const snapshot = await fetchJson('/api/v5/trains');
        updateTrainSamples(snapshot);
        await loadGraph(false);
        showConnected(state.updateMode === 'poll' ? 'Live / Poll' : 'Live / Poll fallback');
      } catch (error) {
        showDisconnected(error);
      } finally {
        if (generation === pollGeneration) {
          pollTimer = setTimeout(cycle, state.pollIntervalMillis);
        }
      }
    };
    cycle();
  }

  function closeEventSource() {
    if (eventSource) eventSource.close();
    eventSource = null;
  }

  function scheduleSseRetry() {
    if (sseRetryTimer !== null) clearTimeout(sseRetryTimer);
    sseRetryTimer = setTimeout(() => {
      sseRetryTimer = null;
      connectSse();
    }, 10000);
  }

  function connectSse() {
    closeEventSource();
    const source = new EventSource('/api/v5/events');
    eventSource = source;
    source.addEventListener('open', () => {
      if (eventSource !== source) return;
      stopPolling();
      showConnected('Live / SSE');
    });
    source.addEventListener('snapshot', event => {
      if (eventSource !== source) return;
      try {
        updateTrainSamples(JSON.parse(event.data));
        loadGraph(false).catch(showDisconnected);
        showConnected('Live / SSE');
      } catch (error) {
        showDisconnected(error);
      }
    });
    source.addEventListener('error', event => {
      if (eventSource !== source) return;
      if (state.updateMode === 'auto') {
        closeEventSource();
        startPolling();
        scheduleSseRetry();
      } else {
        showDisconnected(event);
      }
    });
  }

  async function startDataTransport() {
    try {
      const config = await fetchJson('/api/v5/config');
      state.updateMode = ['auto', 'sse', 'poll'].includes(config.updateMode)
        ? config.updateMode : 'auto';
      state.controlEnabled = config.controlEnabled === true;
      state.pollIntervalMillis = Math.max(250,
        Math.min(10000, Number(config.pollIntervalMillis || 1000)));
    } catch (error) {
      console.warn('SkyPCC config unavailable; using auto mode.', error);
    }
    if (state.updateMode === 'poll') startPolling();
    else connectSse();
  }

  ui.lineFilter.addEventListener('change', () => {
    state.lineFilter = ui.lineFilter.value;
    renderGraph();
  });
  ui.fitButton.addEventListener('click', () => { state.followTrainId = null; fitGraph(); });
  ui.themeToggle.addEventListener('click', () => setTheme(document.documentElement.dataset.theme === 'light' ? 'dark' : 'light'));
  ui.followTrain.addEventListener('click', () => {
    state.followTrainId = state.selectedTrainId;
    if (state.lineFilter && state.trains.get(state.selectedTrainId)?.line !== state.lineFilter) {
      state.lineFilter = ''; ui.lineFilter.value = ''; renderGraph();
    }
  });
  ui.cancelFollow.addEventListener('click', () => { state.followTrainId = null; });
  function zoomAtMapCenter(factor) {
    const rect = ui.railMap.getBoundingClientRect();
    zoom(factor, rect.left + rect.width / 2, rect.top + rect.height / 2);
  }
  ui.zoomIn.addEventListener('click', () => zoomAtMapCenter(0.75));
  ui.zoomOut.addEventListener('click', () => zoomAtMapCenter(1.33));
  ui.inspectorBack.addEventListener('click', () => selectTrain(null));
  ui.eventFilter.addEventListener('change', renderOperations);
  ui.eventPanel.addEventListener('toggle', () => requestAnimationFrame(updateScaleStyles));
  document.getElementById('expandEvents').addEventListener('click', event => {
    event.preventDefault(); event.stopPropagation();
    const expanded = ui.eventPanel.classList.toggle('expanded');
    ui.eventPanel.open = true;
    event.currentTarget.textContent = expanded ? '↓' : '↑';
    event.currentTarget.setAttribute('aria-expanded', String(expanded));
    event.currentTarget.title = t(expanded ? 'Restore log' : 'Expand log');
    event.currentTarget.setAttribute('aria-label', event.currentTarget.title);
    requestAnimationFrame(updateScaleStyles);
  });
  ui.fontDecrease.addEventListener('click', () => setLabelScale(state.labelScale - 0.25));
  ui.fontIncrease.addEventListener('click', () => setLabelScale(state.labelScale + 0.25));
  ui.fontSizeValue.addEventListener('click', () => setLabelScale(1.5));
  ui.railMap.addEventListener('wheel', event => {
    event.preventDefault();
    zoom(event.deltaY < 0 ? 0.82 : 1.22, event.clientX, event.clientY);
  }, { passive: false });
  ui.railMap.addEventListener('pointerdown', event => {
    if (state.followTrainId || event.target.closest('.train-marker, .switch-hit, .infrastructure-hit')) return;
    state.dragging = { x: event.clientX, y: event.clientY, view: { ...state.view } };
    ui.railMap.classList.add('dragging');
    ui.railMap.setPointerCapture(event.pointerId);
  });
  ui.railMap.addEventListener('pointermove', event => {
    if (!state.dragging) return;
    const unit = unitPerPixel();
    state.view.x = state.dragging.view.x - (event.clientX - state.dragging.x) * unit;
    state.view.y = state.dragging.view.y - (event.clientY - state.dragging.y) * unit;
    applyView();
  });
  const endDrag = () => { state.dragging = null; ui.railMap.classList.remove('dragging'); };
  ui.railMap.addEventListener('pointerup', endDrag);
  ui.railMap.addEventListener('pointercancel', endDrag);
  window.addEventListener('resize', updateScaleStyles);

  function liveAuthority() {
    const age = Date.now() + state.clockOffset - Number(shadowMa?.emittedAtMillis || 0);
    return shadowMa?.version === 5 && Array.isArray(shadowMa.authorities)
      && shadowMa.authorities.every(a => a.state === 'ALLOCATED_SHADOW'
        ? a.NID_MESSAGE === 1003 && a.NID_PACKET === 1015
        : a.NID_MESSAGE === 2003 && a.NID_PACKET == null)
      && shadowMa.simulationOnly === true && shadowMa.executable === false
      && shadowMa.status === 'SHADOW' && shadowMa.graphRevision === state.graph?.revision
      && age >= 0 && age <= 1500 ? shadowMa : null;
  }

  function renderAuthority() {
    const live = liveAuthority();
    ui.maStatus.textContent = live ? t('SHADOW · no ATP') : t('MA unavailable / stale');
    ui.edgeLayer.querySelectorAll('.interval-overlay').forEach(e => e.remove());
    const sections = new Map((live?.sections || []).filter(s => s.fromMeters == null).map(s => [s.edgeId, s]));
    for (const line of ui.edgeLayer.querySelectorAll('.rail-edge')) {
      const section = sections.get(line.dataset.edgeId);
      const value = section?.state || (live ? 'UNALLOCATED' : 'UNKNOWN');
      line.dataset.occupancy = value;
      let title = line.querySelector('title');
      if (!title) { title = svg('title'); line.append(title); }
      title.textContent = `${code(value)}\n${t('Occupants')}: ${(section?.occupants || []).join(', ') || '--'}\n${t('Shadow reservations')}: ${(section?.reservations || []).join(', ') || '--'}`;
    }
    for (const s of live?.sections || []) {
      if (!Number.isFinite(s.fromMeters) || !Number.isFinite(s.toMeters) || s.state === 'UNALLOCATED') continue;
      const edge = state.edges.get(s.edgeId);
      if (!edge?.path?.length) continue;
      const points = [];
      for (let i = 1; i < edge.path.length; i++) {
        const a = edge.path[i - 1], b = edge.path[i];
        const lo = Math.max(s.fromMeters, a.distanceMeters), hi = Math.min(s.toMeters, b.distanceMeters);
        if (hi <= lo) continue;
        for (const d of [lo, hi]) {
          const f = (d - a.distanceMeters) / (b.distanceMeters - a.distanceMeters);
          points.push(`${a.x + (b.x - a.x) * f},${a.z + (b.z - a.z) * f}`);
        }
      }
      if (points.length < 2) continue;
      const line = lineOfEdge(edge);
      const overlay = svg('polyline', { points: points.join(' '),
        class: `rail-edge interval-overlay${state.lineFilter && line !== state.lineFilter ? ' dimmed' : ''}`,
        'data-edge-id': s.edgeId, 'data-occupancy': s.state });
      const title = svg('title');
      title.textContent = `${code(s.state)} · ${s.fromMeters.toFixed(1)}–${s.toMeters.toFixed(1)} m\n${t('Occupants')}: ${(s.occupants || []).join(', ') || '--'}\n${t('Shadow reservations')}: ${(s.reservations || []).join(', ') || '--'}`;
      overlay.append(title); ui.edgeLayer.append(overlay);
      inspectClick(overlay, 'edge', s.edgeId);
    }
    ui.authorityLayer.replaceChildren();
    const a = live?.authorities?.find(a => a.trainId === state.selectedTrainId && a.state === 'ALLOCATED_SHADOW');
    if (!a?.eoaEdgeId) return;
    let edge = state.edges.get(a.eoaEdgeId), offset = a.eoaOffsetMeters;
    if (!edge && a.eoaEdgeId.startsWith('offline:origin-reverse:')) {
      edge = state.edges.get(a.eoaEdgeId.slice('offline:origin-reverse:'.length));
      if (edge) offset = edge.distanceMeters - offset;
    }
    if (!edge?.path?.length) return;
    const points = edge.path;
    let p = points.at(-1);
    for (let i = 1; i < points.length; i++) {
      const end = points[i], start = points[i - 1];
      if (end.distanceMeters < offset) continue;
      const t = Math.max(0, Math.min(1, (offset - start.distanceMeters) / Math.max(.001, end.distanceMeters - start.distanceMeters)));
      p = { x: start.x + (end.x - start.x) * t, z: start.z + (end.z - start.z) * t }; break;
    }
    const unit = unitPerPixel();
    const marker = svg('circle', { cx: p.x, cy: p.z, r: 7 * unit, class: 'eoa-marker' });
    const title = svg('title'); title.textContent = t('Shadow EoA: {n} m remaining', { n: a.signedRemainingMeters.toFixed(1) }); marker.append(title);
    ui.authorityLayer.append(marker);
  }

  function switchClick(element, id, control = false) {
    element.classList.add('switch-hit');
    element.dataset.switchId = id;
    element.setAttribute('role', 'button'); element.setAttribute('tabindex', '0');
    element.setAttribute('aria-label', t('Inspect switch {name}', { name: state.nodes.get(id)?.name || id }));
    const open = event => {
      event.stopPropagation();
      if (!control) { selectInfrastructure('node', id); return; }
      const node = state.nodes.get(id); if (!node || switchPending) return;
      const expected = String(node.state).toLowerCase();
      switchRequest = { requestId: crypto.randomUUID(), switchId: id, graphRevision: state.graph.revision,
        expectedState: expected, targetState: expected === 'straight' ? 'diverging' : 'straight',
        position: node.rail ? { world: node.rail.world, x: node.rail.x, y: node.rail.y, z: node.rail.z } : null };
      ui.switchTitle.textContent = t('Switch {name}', { name: node.name || id });
      ui.switchTransition.textContent = `${code(expected)} → ${code(switchRequest.targetState)} | ${node.rail?.world || '?'} ${node.rail?.x}, ${node.rail?.y}, ${node.rail?.z}`;
      ui.switchToken.value = '';
      setSwitchResult({ key: state.controlEnabled ? 'Ready' : 'Remote control disabled' });
      ui.switchConfirm.disabled = !state.controlEnabled || !switchRequest.position || !['straight', 'diverging'].includes(expected);
      ui.switchDialog.showModal();
    };
    element.addEventListener('click', open);
    element.addEventListener('keydown', event => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(event); } });
  }
  ui.switchClose.addEventListener('click', () => ui.switchDialog.close());
  ui.switchDialog.addEventListener('close', () => { ui.switchToken.value = ''; });
  ui.switchConfirm.addEventListener('click', async () => {
    if (!switchRequest || switchPending) return;
    const request = { ...switchRequest }, token = ui.switchToken.value;
    ui.switchToken.value = ''; ui.switchConfirm.disabled = true; switchPending = true;
    setSwitchResult({ status: 'PENDING', reason: 'QUEUED' });
    try {
      const deadline = Date.now() + 50000;
      while (Date.now() < deadline) {
        const response = await fetch('/api/v5/switch', { method: 'POST', cache: 'no-store',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify(request), signal: AbortSignal.timeout(6000) });
        const result = await response.json();
        if (response.status !== 202) {
          setSwitchResult(result.status === 'COMPLETED' ? { key: 'Conversion confirmed' }
            : { status: result.status || response.status, reason: result.reason || 'UNCONFIRMED' });
          await loadGraph(false); return;
        }
        setSwitchResult({ status: 'PENDING', reason: result.reason || 'UNCONFIRMED' });
        await new Promise(resolve => setTimeout(resolve, 500));
      }
      setSwitchResult({ key: 'Unconfirmed. Check actual point position.' });
    } catch (_error) { setSwitchResult({ key: 'Connection lost / unconfirmed' }); }
    finally { switchPending = false; }
  });
  ui.srTrainSelect.addEventListener('change', event => {
    state.selectedSrTrainId = event.target.value || null;
    updateInspector();
  });
  ui.srClose.addEventListener('click', () => ui.srDialog.close());
  ui.srDialog.addEventListener('close', () => { ui.srToken.value = ''; });
  ui.srConfirm.addEventListener('click', async () => {
    if (!srRequest || srPending) return;
    const request = { ...srRequest }, token = ui.srToken.value;
    ui.srToken.value = ''; ui.srConfirm.disabled = true; srPending = true;
    ui.srResult.textContent = code('PENDING');
    try {
      const deadline = Date.now() + 30000;
      while (Date.now() < deadline) {
        const response = await fetch('/api/v6/sr', { method: 'POST', cache: 'no-store',
          headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
          body: JSON.stringify(request), signal: AbortSignal.timeout(6000) });
        const result = await response.json();
        if (response.status !== 202) {
          ui.srResult.textContent = result.status === 'APPLIED' ? t('SR approved')
            : `${code(result.status || 'REJECTED')}: ${code(result.reason || 'UNCONFIRMED')}`;
          return;
        }
        ui.srResult.textContent = `${code(result.status)}: ${code(result.reason)}`;
        await new Promise(resolve => setTimeout(resolve, 500));
      }
      ui.srResult.textContent = t('Unconfirmed. Check actual point position.');
    } catch (_error) { ui.srResult.textContent = t('Connection lost / unconfirmed'); }
    finally { srPending = false; updateInspector(); }
  });
  setInterval(renderAuthority, 500);

  function setEventStatus(key) {
    eventStatusKey = key;
    ui.eventStatus.textContent = t(key);
  }

  function setSwitchResult(result) {
    switchResultState = result;
    ui.switchResult.textContent = result.key ? t(result.key)
      : `${code(result.status)}: ${code(result.reason)} [${result.reason}]`;
  }

  function refreshLanguage() {
    PccI18n.apply();
    document.getElementById('languageSelect').value = PccI18n.language;
    setTheme(document.documentElement.dataset.theme || loadTheme());
    if (state.graph) { populateLines(); renderGraph(); }
    renderTrainList();
    renderPendingSr();
    updateInspector();
    renderOperations();
    renderAuthority();
    setEventStatus(eventStatusKey);
    if (connectionLabel === 'Connecting' || connectionLabel === 'Disconnected') {
      ui.connectionText.textContent = t(connectionLabel);
    } else showConnected(connectionLabel);
    ui.activeCount.textContent = t('{n} active', { n: [...state.trains.values()].filter(train => !train.stale).length });
    if (switchRequest) {
      const node = state.nodes.get(switchRequest.switchId);
      ui.switchTitle.textContent = t('Switch {name}', { name: node?.name || switchRequest.switchId });
      const p = switchRequest.position;
      ui.switchTransition.textContent = `${code(switchRequest.expectedState)} → ${code(switchRequest.targetState)} | ${p?.world || '?'} ${p?.x}, ${p?.y}, ${p?.z}`;
    }
    setSwitchResult(switchResultState);
    const expand = document.getElementById('expandEvents');
    expand.title = t(ui.eventPanel.classList.contains('expanded') ? 'Restore log' : 'Expand log');
    expand.setAttribute('aria-label', expand.title);
  }
  document.getElementById('languageSelect').addEventListener('change', event => {
    PccI18n.set(event.target.value);
    refreshLanguage();
  });
  refreshLanguage();
  setTheme(loadTheme());
  setLabelScale(state.labelScale);
  loadGraph(true).catch(showDisconnected);
  startDataTransport();
  requestAnimationFrame(renderTrains);
})();
