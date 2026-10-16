// 설비 현황 대시보드.
// 평소에는 WebSocket(STOMP)으로 서버가 보내 주는 대시보드를 받아 그린다 (바뀌면 1초 안에 도착).
// 연결이 끊긴 동안에는 /api/monitoring/dashboard를 5초마다 직접 조회해서, 화면이 멈추지 않게 한다.
// 계산(경과 시간, 통신 여부, OEE)은 서버가 끝내 두었으므로, 여기서는 받은 값을 보여 주기만 한다.

const API_URL = document.body.dataset.apiUrl;
const WS_URL = `${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}${document.body.dataset.wsPath}`;
const TOPIC = '/topic/dashboard';
const POLL_MS = 5000;           // 끊긴 동안 직접 조회하는 주기
const RECONNECT_MS = 5000;      // 끊기면 이 간격으로 다시 접속을 시도한다

// 상태 코드별 화면 표시. 서버가 이름(statusName)도 주지만, 색을 정하려면 코드가 필요하다.
const STATUS_CLASS = { RUN: 'run', IDLE: 'idle', SETUP: 'setup', DOWN: 'down' };

let pollTimer = null;

async function refresh() {
    try {
        const response = await fetch(API_URL);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        render(await response.json());
        document.getElementById('error').hidden = true;
    } catch (e) {
        console.error('대시보드 조회 실패', e);
        document.getElementById('error').hidden = false;   // 화면은 마지막으로 받은 값을 그대로 둔다
    }
}

function startPolling() {
    if (pollTimer == null) {
        pollTimer = setInterval(refresh, POLL_MS);
    }
}

function stopPolling() {
    clearInterval(pollTimer);
    pollTimer = null;
}

function showConnection(live) {
    const node = document.getElementById('connection');
    node.textContent = live ? '실시간 연결' : '연결 끊김 · 5초마다 조회';
    node.className = `connection ${live ? 'live' : 'lost'}`;
}

const stomp = new StompJs.Client({
    brokerURL: WS_URL,
    reconnectDelay: RECONNECT_MS,
    onConnect: () => {
        stomp.subscribe(TOPIC, message => {
            render(JSON.parse(message.body));
            document.getElementById('error').hidden = true;
        });
        stopPolling();
        showConnection(true);
        refresh();   // 끊긴 사이에 바뀐 것이 있을 수 있으니, 구독을 건 뒤 한 번 받아 둔다
    },
    onWebSocketClose: () => {
        showConnection(false);
        startPolling();
    },
});

function render(dashboard) {
    document.getElementById('factory-now').textContent = formatDateTime(dashboard.factoryNow);
    document.getElementById('prod-date').textContent = dashboard.prodDate;
    document.getElementById('refreshed-at').textContent = new Date().toLocaleTimeString('ko-KR');

    const cards = document.getElementById('cards');
    cards.replaceChildren(...dashboard.equipment.map(renderCard));   // 기존 카드를 모두 새 카드로 바꾼다
}

function renderCard(eq) {
    const statusClass = eq.online ? (STATUS_CLASS[eq.status] ?? 'idle') : 'offline';
    const card = el('article', `card ${statusClass}`);

    // 1) 머리: 설비 코드·이름, 상태 배지
    const head = el('div', 'card-head');
    head.append(
        el('div', 'name', null, [el('strong', null, eq.equipCode), el('span', null, eq.equipName)]),
        el('span', 'badge', eq.online ? (eq.statusName ?? eq.status) : '통신 없음'));
    card.append(head);

    // 2) 상태 줄: "정지 42분째 · 설비고장 / 스핀들 이상"
    let statusLine = eq.statusMinutes == null ? '상태 이력 없음' : `${eq.statusName ?? eq.status} ${formatMinutes(eq.statusMinutes)}째`;
    if (eq.stop) {
        statusLine += ` · ${eq.stop.dtCatName ?? eq.stop.dtCat} / ${eq.stop.dtSubName ?? eq.stop.dtSub ?? '-'}`;
    }
    if (!eq.online) {
        statusLine = eq.lastSeenAt ? `마지막 소식 ${formatDateTime(eq.lastSeenAt)}` : '받은 데이터 없음';
    }
    card.append(el('p', 'status-line', statusLine));

    // 3) 센서: 진동, 온도, 누적 수량
    card.append(el('dl', 'sensors', null, [
        metric('진동', eq.spindleVibration, 'mm/s'),
        metric('온도', eq.spindleTemp, '°C'),
        metric('누적', eq.cycleCount, '개'),
    ]));

    // 4) 오늘 OEE: 큰 숫자 + 막대, 그 아래 세 지표와 근거 숫자
    card.append(renderOee(eq.today));
    return card;
}

function renderOee(today) {
    const box = el('div', 'oee');
    if (!today) {
        box.append(el('p', 'muted', '오늘 집계 전 (1분마다 집계)'));
        return box;
    }
    const bar = el('div', 'bar');
    const fill = el('div', 'bar-fill');
    fill.style.width = `${Math.min(Number(today.oee), 100)}%`;
    bar.append(fill);

    box.append(
        el('div', 'oee-head', null, [el('span', null, '오늘 OEE'), el('strong', null, `${formatPercent(today.oee)}%`)]),
        bar,
        el('dl', 'oee-parts', null, [   // OEE = 가동률 × 성능 × 품질
            metric('가동률', formatPercent(today.availability), '%'),
            metric('성능', formatPercent(today.performance), '%'),
            metric('품질', formatPercent(today.quality), '%'),
        ]),
        el('p', 'muted', `생산 ${today.prodQty}개 · 비가동 ${today.downtimeMin}분`));
    return box;
}

function metric(label, value, unit) {
    return el('div', null, null, [el('dt', null, label), el('dd', null, value == null ? '-' : `${value} ${unit}`)]);
}

// 요소 만들기 도우미. 글자는 textContent로 넣는다:
// innerHTML에 서버 값을 그대로 넣으면 값 안의 <script> 같은 태그가 실행될 수 있다 (XSS).
function el(tag, className, text, children) {
    const node = document.createElement(tag);
    if (className) node.className = className;
    if (text != null) node.textContent = text;
    if (children) node.append(...children);
    return node;
}

function formatDateTime(isoText) {
    // "2026-10-17T14:58:46.419" → "10-17 14:58"
    return isoText ? `${isoText.slice(5, 10)} ${isoText.slice(11, 16)}` : '-';
}

function formatMinutes(minutes) {
    const h = Math.floor(minutes / 60);
    const m = minutes % 60;
    return h > 0 ? `${h}시간 ${m}분` : `${m}분`;
}

function formatPercent(value) {
    return value == null ? '-' : Number(value).toFixed(1);
}

refresh();          // 접속을 기다리지 않고 첫 화면부터 그린다
stomp.activate();
