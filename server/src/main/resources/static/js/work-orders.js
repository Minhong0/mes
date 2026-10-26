// 작업지시: /api/work-orders를 한 번 읽어 상태별로 걸러 그린다 (위의 숫자는 늘 전체 기준).
// 새 작업지시 만들기, 대기 → 시작, 완료 → 마감 버튼. 상태 고르기는 주소(?status=)에도 남겨 새로 고쳐도 그대로다.

const WO_URL = document.body.dataset.woUrl;
const ITEMS_URL = document.body.dataset.itemsUrl;
const $ = id => document.getElementById(id);
const createForm = $('create');

const STATUS_NAMES = { WAIT: '대기', RUN: '진행', DONE: '완료', CLOSED: '마감' };
const OP_STATUS_NAMES = { WAIT: '대기', RUN: '진행', DONE: '완료' };

let workOrders = [];
let itemNames = {};
let status = new URLSearchParams(location.search).get('status') ?? '';

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

async function getJson(url, options) {
    const response = await fetch(url, options);
    const body = await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(body?.message ?? `서버 오류 (HTTP ${response.status})`);
    }
    return body;
}

function postJson(url, data) {
    return getJson(url, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(data ?? {}) });
}

function showError(message) {
    $('error').textContent = message;
    $('error').hidden = !message;
}

// 브라우저 시각 기준 오늘 (yyyy-MM-dd). toISOString()은 UTC라 아침 9시 전에는 어제가 된다
function today() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function isLate(wo) {
    return wo.dueDate != null && wo.dueDate < today() && (wo.status === 'WAIT' || wo.status === 'RUN');
}

// ---- 목록 ----

// 공정마다 칩 하나: "CNC 선삭 20". 완료는 옅게, 진행은 강조, 대기는 테두리만
function operationChips(wo) {
    const box = el('div', 'ops');
    wo.operations.forEach(op => {
        const chip = el('span', `op ${op.status}`);
        chip.title = `${op.processCode} ${OP_STATUS_NAMES[op.status]} · 양품 ${op.goodQty} · 불량 ${op.defectQty}`;
        chip.append(el('span', 'op-name', op.processName ?? op.processCode), el('span', 'op-qty', String(op.goodQty)));
        box.append(chip);
    });
    return box;
}

function actionButton(wo) {
    if (wo.status === 'WAIT') return button('시작', () => changeStatus(wo, 'start', `${wo.woNo}를 시작합니다.`));
    if (wo.status === 'DONE') {
        return button('마감', () => changeStatus(wo, 'close', `${wo.woNo}를 마감합니다.\n마감하면 실적을 더 받지 않고 취소도 할 수 없습니다.`));
    }
    return null;
}

function button(label, onClick) {
    const b = el('button', 'row-action', label);
    b.type = 'button';
    b.addEventListener('click', onClick);
    return b;
}

function render() {
    const counts = { WAIT: 0, RUN: 0, DONE: 0 };
    workOrders.forEach(wo => { if (wo.status in counts) counts[wo.status]++; });
    $('count-wait').textContent = `${counts.WAIT}건`;
    $('count-run').textContent = `${counts.RUN}건`;
    $('count-done').textContent = `${counts.DONE}건`;
    $('count-late').textContent = `${workOrders.filter(isLate).length}건`;

    document.querySelectorAll('#status-filter button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.status === status)));

    const shown = status ? workOrders.filter(wo => wo.status === status) : workOrders;
    const rows = shown.map(wo => {
        const tr = el('tr');
        const due = el('td', isLate(wo) ? 'late' : null, wo.dueDate ?? '-');
        if (isLate(wo)) due.append(el('span', 'late-badge', '지연'));
        const statusCell = el('td');
        statusCell.append(el('span', `wo-status ${wo.status}`, STATUS_NAMES[wo.status] ?? wo.status));
        const opsCell = el('td');
        opsCell.append(operationChips(wo));
        const actionCell = el('td', 'action');
        const action = actionButton(wo);
        if (action) actionCell.append(action);

        const itemCell = el('td');
        itemCell.append(el('span', null, itemNames[wo.itemCode] ?? wo.itemCode), el('small', 'code', wo.itemCode));
        const noCell = el('td', 'wo-no', wo.woNo);
        if (wo.note) noCell.append(el('small', 'code', wo.note));

        tr.append(noCell, itemCell, el('td', 'num', String(wo.planQty)), due, statusCell, opsCell, actionCell);
        return tr;
    });
    $('rows').replaceChildren(...rows);
    $('empty').hidden = shown.length > 0;
}

async function load() {
    try {
        workOrders = await getJson(WO_URL);
        showError('');
        render();
    } catch (e) {
        showError(`작업지시를 받지 못했습니다: ${e.message}`);
    }
}

async function changeStatus(wo, action, question) {
    if (!confirm(question)) return;
    try {
        await postJson(`${WO_URL}/${encodeURIComponent(wo.woNo)}/${action}`);
        await load();
    } catch (e) {
        showError(e.message);   // 예: "RUN 상태의 작업지시는 마감할 수 없습니다"
    }
}

document.querySelectorAll('#status-filter button').forEach(b => b.addEventListener('click', () => {
    status = b.dataset.status;
    const url = status ? `${location.pathname}?status=${status}` : location.pathname;
    history.replaceState(null, '', url);
    render();
}));

// ---- 새 작업지시 ----

function showCreateResult(message, isError) {
    const p = $('create-result');
    p.textContent = message;
    p.classList.toggle('error', isError);
    p.hidden = !message;
}

createForm.addEventListener('submit', async event => {
    event.preventDefault();
    const data = {
        itemCode: createForm.itemCode.value,
        planQty: Number(createForm.planQty.value),
        dueDate: createForm.dueDate.value || null,
        note: createForm.note.value.trim() || null,
    };
    const submit = createForm.querySelector('button[type="submit"]');
    submit.disabled = true;
    try {
        const wo = await postJson(WO_URL, data);
        showCreateResult(`${wo.woNo}를 만들었습니다. 공정 ${wo.operations.length}개 · 계획 ${wo.planQty}개`, false);
        createForm.planQty.value = '';
        createForm.note.value = '';
        await load();
    } catch (e) {
        showCreateResult(e.message, true);   // 예: "계획수량은 1 이상이어야 합니다."
    } finally {
        submit.disabled = false;
    }
});

async function loadItems() {
    try {
        const items = await getJson(ITEMS_URL);
        itemNames = Object.fromEntries(items.map(i => [i.itemCode, i.itemName]));
        // 작업지시는 완제품으로 만든다 (반제품은 완제품 라우팅의 공정에서 생긴다)
        const options = items.filter(i => i.itemType === 'FG')
            .map(i => Object.assign(el('option', null, `${i.itemCode} · ${i.itemName}`), { value: i.itemCode }));
        createForm.itemCode.append(...options);
        if (options.length === 1) createForm.itemCode.value = options[0].value;
    } catch (e) {
        showError(`품목을 받지 못했습니다: ${e.message}`);
    }
}

loadItems().then(load);
