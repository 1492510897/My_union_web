const refresh = document.getElementById('refresh');
const Logining = document.getElementById('Logining');

//==================== 1：刷新二维码 ====================
refresh.addEventListener('click', async function (e) {
    e.preventDefault();

    try {
        const response = await fetch('/api/refresh');
        const result = await response.json();

    } catch (error) {
        console.error('Error refreshing QR code:', error);
    }
});

//==================== 2：登录状态及登录 ====================
Logining.addEventListener('click', async function (e) {
    e.preventDefault();
    try {
        const response = await fetch('/api/login');
        const result = await response.json();
    } catch (error) {
        console.error('Error during login:', error);
    }
});