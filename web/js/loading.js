(function () {
    'use strict';

    const qrImage = document.getElementById('qrImage');
    const qrMask = document.getElementById('qrMask');
    const refreshBtn = document.getElementById('refresh');
    const loginBtn = document.getElementById('Logining');
    const statusEl = document.getElementById('status');

    const POLL_INTERVAL = 3000;
    const IMAGE_URL = 'api/qr/image';
    const STATUS_URL = 'api/qr/status';
    const LOGIN_URL = 'api/qr/login';

    let pollTimer = null;
    let requesting = false;
    let scanned = false;
    let finished = false;

    function setStatus(text, type) {
        statusEl.textContent = text;
        statusEl.className = 'status' + (type ? ' ' + type : '');
    }

    function showMask(text) {
        qrMask.textContent = text;
        qrMask.hidden = false;
    }

    function hideMask() {
        qrMask.hidden = true;
    }

    function stopPolling() {
        if (pollTimer !== null) {
            clearInterval(pollTimer);
            pollTimer = null;
        }
    }

    // ==================== 1：加载 / 刷新二维码 ====================
    function loadQr(forceRefresh) {
        stopPolling();
        scanned = false;
        finished = false;
        loginBtn.disabled = true;
        loginBtn.textContent = '扫码后点击登录';
        hideMask();
        setStatus('正在获取二维码...');

        qrImage.src = IMAGE_URL + (forceRefresh ? '?refresh=1&t=' : '?t=') + Date.now();
    }

    qrImage.addEventListener('load', function () {
        if (finished) {
            return;
        }
        hideMask();
        setStatus('等待扫码，请在手机 4399 App 中确认');
        startPolling();
    });

    qrImage.addEventListener('error', function () {
        stopPolling();
        showMask('二维码加载失败，请点击刷新重试');
        setStatus('二维码加载失败，请点击刷新重试', 'error');
    });

    // ==================== 2：轮询扫码状态 ====================
    function startPolling() {
        stopPolling();
        pollStatus();
        pollTimer = setInterval(pollStatus, POLL_INTERVAL);
    }

    async function pollStatus() {
        if (requesting || scanned || finished) {
            return;
        }
        requesting = true;
        try {
            const response = await fetch(STATUS_URL + '?t=' + Date.now(), { cache: 'no-store' });
            const data = await response.json();

            if (data.status === 'success') {
                scanned = true;
                stopPolling();
                const uids = data.uids || data.uid || '';
                setStatus('扫码成功，4399 UID：' + uids + '\n请点击下方按钮完成登录', 'success');
                loginBtn.disabled = false;
                loginBtn.textContent = '完成登录';
            } else if (data.status === 'expired') {
                stopPolling();
                showMask('二维码已过期，请点击刷新');
                setStatus(data.message || '二维码已过期，请点击刷新', 'error');
            } else if (data.status === 'error') {
                stopPolling();
                setStatus(data.message || '扫码失败，请刷新重试', 'error');
            } else {
                setStatus(data.message || '等待扫码...');
            }
        } catch (error) {
            console.error('Error polling QR status:', error);
            setStatus('网络异常，正在重试...', 'error');
        } finally {
            requesting = false;
        }
    }

    // ==================== 3：保存 Cookie 并完成登录 ====================
    async function doLogin() {
        if (!scanned) {
            setStatus('请先用手机扫码', 'error');
            return;
        }

        loginBtn.disabled = true;
        loginBtn.textContent = '登录中...';

        const body = new URLSearchParams();
        const userId = sessionStorage.getItem('myUserId');
        if (userId) {
            body.append('userId', userId);
        }

        try {
            const response = await fetch(LOGIN_URL, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                body: body.toString()
            });
            const data = await response.json();

            if (data.status !== 'success') {
                setStatus(data.message || '登录失败，请重试', 'error');
                loginBtn.disabled = false;
                loginBtn.textContent = '完成登录';
                return;
            }

            finished = true;
            stopPolling();
            saveToSession(data);
            setStatus('登录成功，正在进入主界面...', 'success');
            setTimeout(function () {
                window.location.href = 'main.html';
            }, 800);
        } catch (error) {
            console.error('Error during QR login:', error);
            setStatus('网络异常，请稍后重试', 'error');
            loginBtn.disabled = false;
            loginBtn.textContent = '完成登录';
        }
    }

    function saveToSession(data) {
        const uids = data.uids || data.uid || '';
        const cookieText = JSON.stringify(data.cookies || {});

        if (data.userId) {
            sessionStorage.setItem('myUserId', String(data.userId));
        }
        if (data.name) {
            sessionStorage.setItem('myName', data.name);
        }
        sessionStorage.setItem('myDefaultUid', uids);
        sessionStorage.setItem('myDefaultCookie', cookieText);
        sessionStorage.setItem('myCurrent4399Uid', uids);

        let accounts = [];
        try {
            accounts = JSON.parse(sessionStorage.getItem('myAccounts') || '[]');
        } catch (error) {
            accounts = [];
        }
        if (!Array.isArray(accounts)) {
            accounts = [];
        }

        const existing = accounts.filter(function (item) {
            return item && item.uids === uids;
        })[0];

        if (existing) {
            existing.cookie = cookieText;
        } else {
            accounts.push({ uids: uids, cookie: cookieText });
        }
        sessionStorage.setItem('myAccounts', JSON.stringify(accounts));
    }

    // ==================== 事件绑定 ====================
    refreshBtn.addEventListener('click', function () {
        loadQr(true);
    });

    loginBtn.addEventListener('click', doLogin);

    document.addEventListener('visibilitychange', function () {
        if (document.hidden) {
            stopPolling();
        } else if (!scanned && !finished && qrImage.complete && qrImage.naturalWidth > 0) {
            startPolling();
        }
    });

    loadQr(false);
})();