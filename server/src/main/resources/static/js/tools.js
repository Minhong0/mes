// 공구 수명: /api/tools(목록)와 /api/tools/changes(교체 이력)를 30초마다 다시 읽어 그린다.
// [교체]는 위 칸의 사유로 POST /api/tools/{toolCode}/change 를 부른다. 바꾼 사람은 서버가 로그인 토큰으로 남긴다.

const TOOLS_URL = document.body.dataset.toolsUrl;
const $ = id => document.getElementById(id);
const form = $('change');
const REFRESH_MS = 30000;

const TYPE_NAMES = { INSERT: '인서트', ENDMILL: '엔드밀', DRILL: '드릴', WHEEL: '연삭석' };
const STATE_NAMES = { OK: '정상', WARN: '교체 임박', REPLACE: '교체 필요' };

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function time(value) {
    if (!value) return '-';
    const [date, clock] = value.split('T');
    return `${date.slice(5)} ${clock.slice(0, 5)}`;   // "2026-11-03T14:53:00" → "11-03 14:53"
}

async function getJson(url, options) {
    const response = await fetch(url, options);
    const body = await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(body?.message ?? `서버 오류 (HTTP ${response.status})`);
    }
    return body;
}

function showError(message) {
    $('error').textContent = message;
    $('error').hidden = !message;
}

function showResult(message, isError) {
    const p = $('change-result');
    p.textContent = message;
    p.classList.toggle('error', isError);
    p.hidden = !message;
}

// 사용 / 수명 막대: 90%부터 주황, 100%면 빨강
function usageBar(tool) {
    const box = el('div', 'usage');
    const percent = Math.min(100, Math.round(tool.usedCount / tool.lifeCount * 100));
    const bar = el('div', `bar ${tool.state}`);
    const fill = el('span');
    fill.style.width = `${percent}%`;
    bar.append(fill);
    box.append(el('span', 'usage-text', `${tool.usedCount} / ${tool.lifeCount}개 (${percent}%)`), bar);
    return box;
}

function renderTools(tools) {
    const count = state => tools.filter(t => t.state === state).length;
    $('count-all').textContent = `${tools.length}개`;
    $('count-warn').textContent = `${count('WARN')}개`;
    $('count-replace').textContent = `${count('REPLACE')}개`;

    const rows = tools.map(tool => {
        const tr = el('tr', tool.state === 'REPLACE' ? 'over' : null);
        const equipCell = el('td');
        equipCell.append(el('span', 'strong', tool.equipCode), el('small', 'code', tool.equipName ?? ''));
        const toolCell = el('td');
        toolCell.append(el('span', null, tool.toolName), el('small', 'code', `${tool.toolCode} · ${TYPE_NAMES[tool.toolType] ?? tool.toolType}`));
        const usageCell = el('td');
        usageCell.append(usageBar(tool));
        const stateCell = el('td');
        stateCell.append(el('span', `tool-state ${tool.state}`, STATE_NAMES[tool.state] ?? tool.state));
        const actionCell = el('td', 'action');
        const button = el('button', 'row-action', '교체');
        button.type = 'button';
        button.addEventListener('click', () => change(tool, button));
        actionCell.append(button);

        tr.append(equipCell, toolCell, usageCell, el('td', 'num', `${tool.remaining}개`), stateCell,
            el('td', 'when', tool.installedAt ? time(tool.installedAt) : '세기 전'), actionCell);
        return tr;
    });
    $('rows').replaceChildren(...rows);
}

function renderHistory(changes) {
    const rows = changes.map(c => {
        const tr = el('tr');
        tr.append(el('td', 'when', time(c.changedAt)), el('td', null, c.toolCode), el('td', 'num', `${c.usedCount}개`),
            el('td', null, c.reason ?? ''), el('td', null, c.workerId ?? ''));
        return tr;
    });
    $('history-rows').replaceChildren(...rows);
    $('history-empty').hidden = changes.length > 0;
}

async function load() {
    try {
        const [tools, changes] = await Promise.all([getJson(TOOLS_URL), getJson(`${TOOLS_URL}/changes`)]);
        renderTools(tools);
        renderHistory(changes);
        showError('');
        $('refreshed-at').textContent = new Date().toLocaleTimeString('ko-KR');
    } catch (e) {
        showError(`공구 목록을 받지 못했습니다: ${e.message}`);
    }
}

async function change(tool, button) {
    const reason = form.reason.value.trim();
    if (!confirm(`${tool.toolName}(${tool.toolCode})을 바꿨습니까?\n바꾸기 전까지 가공한 수량을 이력에 남기고 0부터 다시 셉니다.`)) return;

    button.disabled = true;
    try {
        const result = await getJson(`${TOOLS_URL}/${encodeURIComponent(tool.toolCode)}/change`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ reason }),
        });
        // 서버가 바꾸기 직전에 다시 센 수량 (화면의 숫자는 최대 30초 전 것)
        showResult(`${tool.toolCode}를 바꿨습니다. 가공한 수량 ${result.change.usedCount}개를 이력에 남겼습니다.`, false);
        await load();
    } catch (e) {
        showResult(e.message, true);   // 예: "공구를 찾을 수 없습니다: T-NONE"
        button.disabled = false;
    }
}

form.addEventListener('submit', event => event.preventDefault());

load();
setInterval(load, REFRESH_MS);
