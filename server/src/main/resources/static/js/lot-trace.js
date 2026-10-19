// LOT 추적: /api/lots/{lotNo}(LOT과 이력)와 /api/lots/{lotNo}/trace(역방향·정방향)를 함께 불러 그린다.
// 트리의 LOT 번호를 누르면 그 LOT으로 다시 추적하고, 주소(?lotNo=)도 바꿔 브라우저 [뒤로]로 돌아갈 수 있다.

const LOTS_URL = document.body.dataset.lotsUrl;
const ITEMS_URL = document.body.dataset.itemsUrl;
const form = document.getElementById('search');
const $ = id => document.getElementById(id);

const STATUS_NAMES = { ACTIVE: '사용 가능', HOLD: '보류', CONSUMED: '소진', SHIPPED: '출하', SCRAPPED: '폐기' };
const EVENT_NAMES = {
    RECEIVE: '입고', PRODUCE: '생산', CONSUME: '투입', MERGE: '병합', SPLIT: '분할', INSPECT: '검사',
    HOLD: '보류', RELEASE: '보류 해제', SHIP: '출하', SCRAP: '폐기', CANCEL: '취소',
};

let current = null;   // 지금 화면에 그린 LOT 번호 (늦게 온 응답이 새 결과를 덮지 않게)

function qty(value) {
    return Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 3 });   // 12.000 → 12
}

function time(value) {
    if (!value) return '-';
    const [date, clock] = value.split('T');
    return `${date.slice(5)} ${clock.slice(0, 5)}`;   // "2026-10-18T14:43:55" → "10-18 14:43"
}

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function statusBadge(status) {
    return el('span', `status ${status}`, STATUS_NAMES[status] ?? status);
}

function lotButton(lotNo) {
    const button = el('button', 'node-lot', lotNo);
    button.type = 'button';
    button.addEventListener('click', () => trace(lotNo, true));
    return button;
}

async function getJson(url) {
    const response = await fetch(url);
    const body = await response.json().catch(() => null);
    if (!response.ok) {
        throw new Error(body?.message ?? `서버 오류 (HTTP ${response.status})`);   // 404면 "LOT을 찾을 수 없습니다: ..."
    }
    return body;
}

function showError(message) {
    $('error').textContent = message;
    $('error').hidden = !message;
}

// ---- 추적 결과 ----

function renderLot(detail) {
    const lot = detail.lot;
    $('lot-no').textContent = lot.lotNo;
    $('lot-status').replaceWith(Object.assign(statusBadge(lot.status), { id: 'lot-status' }));
    $('lot-item').textContent = `${lot.itemCode} · ${detail.itemName}`;
    $('lot-qty').textContent = `${qty(lot.qty)} / ${qty(lot.initialQty)}`;
    $('lot-wo').textContent = lot.woNo ?? '- (입고 원자재)';
    $('lot-process').textContent = lot.lastProcess ? `${detail.lastProcessName ?? ''} (${lot.lastProcess})` : '-';
    $('lot-created').textContent = `${time(lot.createdAt)} · ${lot.createdBy ?? '-'}`;
    $('lot-supplier').textContent = lot.supplierLotNo ?? '-';
}

// 서버가 깊이 우선 순서로 준 줄을 그대로 쌓는다. 단계만큼 들여 써서 트리 모양이 된다.
function renderTree(listId, nodes) {
    const items = nodes.map(node => {
        const li = el('li');
        li.style.setProperty('--level', node.traceLevel - 1);

        const head = el('div', 'node-head');
        head.append(lotButton(node.lotNo), statusBadge(node.status), el('span', 'node-qty', `투입 ${qty(node.linkQty)}`));

        const sub = el('p', 'node-sub');
        const where = node.itemType === 'RM' ? el('span', 'rm', '원자재 입고') : el('span', null, node.processName ?? node.lastProcess);
        sub.append(`${node.itemName} · `, where, node.woNo ? ` · ${node.woNo}` : '');

        li.append(head, sub);
        return li;
    });
    $(listId).replaceChildren(...items);
    $(`${listId}-empty`).hidden = nodes.length > 0;
}

// 같은 LOT이 두 경로로 나와도 한 번만 센다
function distinct(nodes, filter = () => true) {
    return new Set(nodes.filter(filter).map(n => n.lotNo)).size;
}

function renderCounts(result) {
    // 입고한 원자재 LOT은 그 자신이 원자재라 셀 것이 없다
    $('count-materials').textContent = result.lot.woNo == null
        ? '- (원자재)' : `${distinct(result.backward, n => n.itemType === 'RM')}개`;
    $('count-affected').textContent = `${distinct(result.forward)}개`;
    $('count-active').textContent = `${distinct(result.forward, n => n.status === 'ACTIVE')}개`;
}

function renderHistory(history) {
    const rows = history.map(h => {
        const tr = el('tr');
        const cells = [
            time(h.eventAt),
            EVENT_NAMES[h.eventType] ?? h.eventType,
            h.processName ?? h.processCode ?? '-',
            h.qty == null ? '-' : qty(h.qty),
            h.equipCode ?? '-',
            h.workerId ?? '-',
            h.resultId ? `#${h.resultId}` : '-',
            h.note ?? '',
        ];
        cells.forEach((value, col) => tr.append(el('td', col === 3 ? 'num' : null, value)));
        return tr;
    });
    $('history-rows').replaceChildren(...rows);
}

async function trace(lotNo, push) {
    lotNo = lotNo.trim();
    if (!lotNo) return;
    current = lotNo;
    form.lotNo.value = lotNo;
    const url = `${location.pathname}?lotNo=${encodeURIComponent(lotNo)}`;
    if (push) history.pushState({ lotNo }, '', url); else history.replaceState({ lotNo }, '', url);

    const base = `${LOTS_URL}/${encodeURIComponent(lotNo)}`;
    try {
        const [detail, result] = await Promise.all([getJson(base), getJson(`${base}/trace`)]);
        if (current !== lotNo) return;   // 그 사이에 다른 LOT을 눌렀다
        renderLot(detail);
        renderCounts(result);
        renderTree('backward', result.backward);
        renderTree('forward', result.forward);
        renderHistory(detail.history);
        showError('');
        $('result').hidden = false;
        document.title = `${lotNo} - LOT 추적 - Mini-MES`;
    } catch (e) {
        if (current !== lotNo) return;
        showError(e.message);
        $('result').hidden = true;
    }
}

// ---- 품목별 최근 LOT ----

async function loadItems() {
    try {
        const items = await getJson(ITEMS_URL);
        const options = items.filter(i => i.lotTracked)
            .map(i => Object.assign(el('option', null, `${i.itemCode} · ${i.itemName}`), { value: i.itemCode }));
        form.itemCode.append(...options);
    } catch (e) {
        showError(`품목을 받지 못했습니다: ${e.message}`);
    }
}

async function loadRecent(itemCode) {
    $('recent').hidden = !itemCode;
    if (!itemCode) return;
    try {
        const lots = await getJson(`${LOTS_URL}?itemCode=${encodeURIComponent(itemCode)}`);
        const rows = lots.map(lot => {
            const tr = el('tr');
            const statusCell = el('td');
            statusCell.append(statusBadge(lot.status));
            tr.append(
                el('td', 'recent-lot', lot.lotNo),
                statusCell,
                el('td', 'num', `${qty(lot.qty)} / ${qty(lot.initialQty)}`),
                el('td', null, lot.woNo ?? '-'),
                el('td', null, lot.lastProcess ?? '입고'),
                el('td', null, time(lot.createdAt)),
            );
            tr.addEventListener('click', () => trace(lot.lotNo, true));
            return tr;
        });
        $('recent-item').textContent = `— ${form.itemCode.selectedOptions[0].textContent}, 최근 것부터 100개까지. 줄을 누르면 추적합니다`;
        $('recent-rows').replaceChildren(...rows);
        $('recent-empty').hidden = lots.length > 0;
    } catch (e) {
        showError(`LOT 목록을 받지 못했습니다: ${e.message}`);
    }
}

form.addEventListener('submit', event => {
    event.preventDefault();
    trace(form.lotNo.value, true);
});
form.itemCode.addEventListener('change', () => loadRecent(form.itemCode.value));
window.addEventListener('popstate', event => {
    if (event.state?.lotNo) trace(event.state.lotNo, false);
});

loadItems();
const initial = new URLSearchParams(location.search).get('lotNo');
if (initial) trace(initial, false);
