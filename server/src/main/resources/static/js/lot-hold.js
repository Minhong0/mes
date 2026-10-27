// 보류 LOT: /api/lots/held를 읽어 원인(검사 불합격 / 직접 보류)별로 걸러 그린다.
// LOT 번호와 원인 LOT은 추적 화면(?lotNo=)으로 가는 링크다. 해제는 추적 화면에서 한다.

const HELD_URL = document.body.dataset.heldUrl;
const TRACE_URL = document.body.dataset.traceUrl;
const $ = id => document.getElementById(id);

let held = [];
let cause = new URLSearchParams(location.search).get('cause') ?? '';

function el(tag, className, text) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text !== undefined) node.textContent = text;
    return node;
}

function qty(value) {
    return Number(value).toLocaleString('ko-KR', { maximumFractionDigits: 3 });
}

function time(value) {
    if (!value) return '-';
    const [date, clock] = value.split('T');
    return `${date.slice(5)} ${clock.slice(0, 5)}`;   // "2026-10-22T21:10:05" → "10-22 21:10"
}

function traceLink(lotNo) {
    const a = el('a', 'lot-link', lotNo);
    a.href = `${TRACE_URL}?lotNo=${encodeURIComponent(lotNo)}`;
    return a;
}

function showError(message) {
    $('error').textContent = message;
    $('error').hidden = !message;
}

function render() {
    $('count-all').textContent = `${held.length}개`;
    $('count-inspection').textContent = `${held.filter(h => h.byInspection).length}개`;
    $('count-origin').textContent = `${new Set(held.map(h => h.originLotNo)).size}개`;
    document.querySelectorAll('#cause-filter button').forEach(b => b.setAttribute('aria-pressed', String(b.dataset.cause === cause)));

    const shown = held.filter(h => cause === '' || (cause === 'inspection') === h.byInspection);
    const rows = shown.map(h => {
        const tr = el('tr');
        const lotCell = el('td', 'lot-cell');
        lotCell.append(traceLink(h.lotNo));

        const itemCell = el('td');
        itemCell.append(el('span', null, h.itemName ?? h.itemCode), el('small', 'code', h.itemCode));

        const woCell = el('td');
        woCell.append(el('span', null, h.woNo ?? '입고 원자재'), el('small', 'code', h.processName ?? ''));

        const whenCell = el('td', 'when');
        whenCell.append(el('span', null, time(h.heldAt)), el('small', 'code', h.heldBy ?? ''));

        const causeCell = el('td');
        causeCell.append(el('span', `cause ${h.byInspection ? 'inspection' : 'manual'}`, h.byInspection ? '검사 불합격' : '직접 보류'));

        const originCell = el('td', 'lot-cell');
        if (h.originLotNo === h.lotNo) originCell.append(el('span', 'muted-inline', '이 LOT'));
        else originCell.append(traceLink(h.originLotNo));
        // 원인 LOT이 여럿이면 한 원인을 해제해도 계속 보류된다
        if (h.holdCount > 1) originCell.append(el('small', 'code', `보류 건 ${h.holdCount}개`));

        tr.append(lotCell, itemCell, el('td', 'num', qty(h.qty)), woCell, whenCell, causeCell, el('td', 'reason', h.reason ?? ''), originCell);
        return tr;
    });
    $('rows').replaceChildren(...rows);
    $('empty').hidden = shown.length > 0;
    $('empty').textContent = held.length === 0 ? '보류 중인 LOT이 없습니다.' : '이 원인으로 보류된 LOT이 없습니다.';
}

async function load() {
    try {
        const response = await fetch(HELD_URL);
        if (!response.ok) throw new Error(`서버 오류 (HTTP ${response.status})`);
        held = await response.json();
        showError('');
        render();
    } catch (e) {
        showError(`보류 LOT을 받지 못했습니다: ${e.message}`);
    }
}

document.querySelectorAll('#cause-filter button').forEach(b => b.addEventListener('click', () => {
    cause = b.dataset.cause;
    history.replaceState(null, '', cause ? `${location.pathname}?cause=${cause}` : location.pathname);
    render();
}));

load();
