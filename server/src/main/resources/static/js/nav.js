// 모든 화면 공통 (fragments/nav.html이 불러온다. 화면의 JS보다 먼저 실행된다)
// 1) 화면의 API 호출이 401(로그인하지 않음·만료)이면 로그인 화면으로 보낸다. 돌아올 주소를 next에 담는다
// 2) [로그아웃]: 서버가 토큰 쿠키를 지운다 (쿠키가 HttpOnly라 JS는 지울 수 없다)
(() => {
    const originalFetch = window.fetch.bind(window);

    window.fetch = async (...args) => {
        const response = await originalFetch(...args);
        if (response.status === 401) {
            location.href = `/login?expired&next=${encodeURIComponent(location.pathname + location.search)}`;
        }
        return response;
    };

    document.getElementById('logout')?.addEventListener('click', async () => {
        try {
            await originalFetch('/api/auth/logout', { method: 'POST' });
        } finally {
            location.href = '/login';
        }
    });
})();
