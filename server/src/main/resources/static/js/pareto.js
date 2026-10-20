// 정지 사유 파레토: 조건을 바꿀 때마다 /api/monitoring/downtime-pareto를 불러 차트와 표를 다시 그린다.
// 위 차트 = 누적 비율(%), 아래 차트 = 사유별 정지 시간(분). 두 차트는 x 위치를 맞춘다.

const API_URL = document.body.dataset.apiUrl;
const form = document.getElementById('filters');
const css = getComputedStyle(document.documentElement);
const color = name => css.getPropertyValue(name).trim();

let charts = null;      // { cumulative, bars }: 처음 한 번 만들고, 조건이 바뀌면 데이터만 바꾼다
let latestItems = [];   // tooltip이 분·횟수를 찾을 때 쓴다

// 두 차트의 세로축 폭을 같게 고정한다. 폭이 다르면 그림 영역이 어긋나 같은 사유가 위아래로 줄을 맞추지 못한다.
const Y_AXIS_WIDTH = 56;

// 80% 기준선. Chart.js에는 기준선 기능이 따로 없어서, 선을 다 그린 뒤 직접 한 줄 긋는다.
const thresholdLine = {
    id: 'thresholdLine',
    afterDatasetsDraw(c) {
        const y = c.scales.y.getPixelForValue(80);
        const { left, right } = c.chartArea;
        const ctx = c.ctx;
        ctx.save();
        ctx.strokeStyle = color('--down');
        ctx.lineWidth = 2;
        ctx.setLineDash([6, 4]);
        ctx.beginPath();
        ctx.moveTo(left, y);
        ctx.lineTo(right, y);
        ctx.stroke();
        ctx.restore();
    },
};

function label(item) {
    return item.dtSub ? (item.dtSubName ?? item.dtSub) : (item.dtCatName ?? item.dtCat);
}

// 두 차트가 같은 설명을 보여 준다: 어느 차트에 마우스를 올려도 그 사유의 분·횟수·비율·누적
const tooltip = {
    callbacks: {
        title: ctx => {
            const item = latestItems[ctx[0].dataIndex];
            return item.dtSub ? `${item.dtCatName ?? item.dtCat} / ${label(item)}` : label(item);
        },
        label: ctx => {
            const item = latestItems[ctx.dataIndex];
            return [`${item.totalMin.toLocaleString('ko-KR')}분 · ${item.stopCount}회`,
                `비율 ${pct(item.sharePct)}% · 누적 ${pct(item.cumulativePct)}%`];
        },
    },
};

function baseOptions() {
    return {
        maintainAspectRatio: false,
        animation: false,
        interaction: { mode: 'index', intersect: false },   // 막대 칸 어디에 올려도 그 사유의 값이 나온다
        plugins: { legend: { display: false }, tooltip },    // 범례는 HTML로 차트 위에 둔다
    };
}

function yAxis(extra) {
    return {
        afterFit: axis => { axis.width = Y_AXIS_WIDTH; },
        ticks: { color: color('--muted'), ...extra.ticks },
        grid: { color: color('--line') },
        border: { display: false },
        ...extra.axis,
    };
}

function createCharts() {
    const cumulative = new Chart(document.getElementById('chart-cumulative'), {
        type: 'line',
        data: { labels: [], datasets: [{
            data: [],
            borderColor: color('--cumulative'),
            backgroundColor: color('--cumulative'),
            borderWidth: 2,
            pointRadius: 4,
            pointHoverRadius: 6,
        }] },
        plugins: [thresholdLine],
        options: {
            ...baseOptions(),
            scales: {
                y: yAxis({ axis: { min: 0, max: 100 }, ticks: { stepSize: 20, callback: v => `${v}%` } }),
                // offset: 막대 차트처럼 칸 가운데에 점을 찍는다. 아래 막대와 같은 x 위치가 된다
                x: { offset: true, ticks: { display: false }, grid: { display: false }, border: { display: false } },
            },
        },
    });
    const bars = new Chart(document.getElementById('chart-bars'), {
        type: 'bar',
        data: { labels: [], datasets: [{
            data: [],
            backgroundColor: [],
            borderRadius: { topLeft: 4, topRight: 4 },
            borderSkipped: 'bottom',
            maxBarThickness: 24,
        }] },
        options: {
            ...baseOptions(),
            scales: {
                y: yAxis({ axis: { min: 0 }, ticks: { callback: v => `${v.toLocaleString('ko-KR')}분` } }),
                x: { ticks: { color: color('--muted'), maxRotation: 45, autoSkip: false }, grid: { display: false } },
            },
        },
    });
    return { cumulative, bars };
}

function drawCharts(items) {
    charts ??= createCharts();
    const labels = items.map(label);

    charts.cumulative.data.labels = labels;
    charts.cumulative.data.datasets[0].data = items.map(i => Number(i.cumulativePct));
    charts.cumulative.update();

    charts.bars.data.labels = labels;
    charts.bars.data.datasets[0].data = items.map(i => i.totalMin);
    charts.bars.data.datasets[0].backgroundColor = items.map(i => i.vitalFew ? color('--vital') : color('--other'));
    charts.bars.update();
}

function pct(value) {
    return Number(value).toFixed(1);   // 14 → "14.0": 표의 소수 자리를 맞춘다
}

function drawTable(items) {
    const rows = items.map((item, index) => {
        const tr = document.createElement('tr');
        if (item.vitalFew) tr.className = 'vital';
        const cells = [index + 1, item.dtCatName ?? item.dtCat, item.dtSub ? label(item) : '-',
            item.stopCount, item.totalMin.toLocaleString('ko-KR'), pct(item.sharePct), pct(item.cumulativePct)];
        cells.forEach((value, col) => {
            const td = document.createElement('td');
            td.textContent = value;
            if (col >= 3) td.className = 'num';
            tr.append(td);
        });
        return tr;
    });
    document.getElementById('rows').replaceChildren(...rows);
}

function render(pareto) {
    latestItems = pareto.items;
    form.from.value = pareto.from;   // 기간을 비워 보냈으면 서버가 정한 기간으로 채운다
    form.to.value = pareto.to;

    const vital = pareto.items.filter(i => i.vitalFew).length;
    document.getElementById('total-min').textContent = `${pareto.totalMin.toLocaleString('ko-KR')}분`;
    document.getElementById('stop-count').textContent = `${pareto.stopCount}회`;
    document.getElementById('vital-few').textContent =
        pareto.items.length ? `${pareto.items.length}개 중 ${vital}개` : '-';

    document.getElementById('empty').hidden = pareto.items.length > 0;
    drawCharts(pareto.items);
    drawTable(pareto.items);
}

async function load(params) {
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(params)) {
        if (value) query.set(key, value);
    }
    history.replaceState(null, '', `${location.pathname}?${query}`);   // 주소를 복사하면 같은 조건으로 열린다
    try {
        const response = await fetch(`${API_URL}?${query}`);
        if (!response.ok) {
            throw new Error(`HTTP ${response.status}`);
        }
        render(await response.json());
        document.getElementById('error').hidden = true;
    } catch (e) {
        console.error('파레토 조회 실패', e);
        document.getElementById('error').hidden = false;   // 차트는 마지막 결과를 그대로 둔다
    }
}

function currentParams() {
    return {
        from: form.from.value,
        to: form.to.value,
        equipCode: form.equipCode.value,
        byReason: form.byReason.value,
    };
}

form.addEventListener('change', () => load(currentParams()));
form.querySelectorAll('.presets button').forEach(button => {
    button.addEventListener('click', () => {
        form.from.value = button.dataset.from;
        form.to.value = button.dataset.to;
        load(currentParams());
    });
});

// 주소에 조건이 있으면 그대로 연다 (예: /monitoring/pareto?from=2025-10-01&to=2025-10-31&equipCode=CNC03)
const initial = new URLSearchParams(location.search);
form.equipCode.value = initial.get('equipCode') ?? '';
form.byReason.value = initial.get('byReason') ?? 'true';
load({ from: initial.get('from'), to: initial.get('to'), equipCode: form.equipCode.value, byReason: form.byReason.value });
