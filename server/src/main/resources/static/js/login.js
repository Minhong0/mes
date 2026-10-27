// 로그인 화면. 서버가 토큰을 HttpOnly 쿠키로 심어 주므로, 이 JS는 토큰을 만지지 않고 원래 화면으로 돌아가기만 한다.
const LOGIN_URL = document.body.dataset.loginUrl;
const form = document.getElementById('login-form');
const params = new URLSearchParams(location.search);

// 돌아갈 주소는 이 서버 안의 경로만 (다른 사이트로 보내는 링크를 막는다: "//evil.com", "/\evil.com")
function nextPath() {
    const next = params.get('next');
    return next && next.startsWith('/') && !next.startsWith('//') && !next.startsWith('/\\') ? next : '/';
}

function showError(message) {
    const p = document.getElementById('login-error');
    p.textContent = message;
    p.hidden = !message;
}

document.getElementById('expired').hidden = !params.has('expired');

form.addEventListener('submit', async event => {
    event.preventDefault();
    const button = form.querySelector('button');
    button.disabled = true;
    showError('');
    try {
        const response = await fetch(LOGIN_URL, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ workerId: form.workerId.value.trim(), password: form.password.value }),
        });
        if (!response.ok) {
            const error = await response.json().catch(() => null);
            throw new Error(error?.message ?? `HTTP ${response.status}`);   // 예: 사번 또는 비밀번호가 맞지 않습니다.
        }
        location.href = nextPath();
    } catch (e) {
        showError(e.message);
        form.password.value = '';
        form.password.focus();
    } finally {
        button.disabled = false;
    }
});
