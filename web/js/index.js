// ==================== 元素引用 ====================
const loginForm    = document.getElementById('loginForm');
const registerForm = document.getElementById('registerForm');
const loginUser    = document.getElementById('loginUser');
const loginPwd     = document.getElementById('loginPwd');
const regUser      = document.getElementById('regUser');
const regPwd       = document.getElementById('regPwd');
const forgotLink   = document.getElementById('forgotLink');
const toRegister   = document.getElementById('toRegister');
const toLogin      = document.getElementById('toLogin');

// ==================== 1：登录 ====================
loginForm.addEventListener('submit', async function (e) {
    e.preventDefault();
    const user = loginUser.value.trim();
    const pwd  = loginPwd.value;

    if (!user || !pwd) { alert('不能为空'); return; }

    try {
        const response = await fetch('api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `username=${encodeURIComponent(user)}&password=${encodeURIComponent(pwd)}`
        });

        // 解析 JSON 数据（先取文本，便于解析失败时显示真实响应）
        const text = await response.text();
        let data;
        try {
            data = JSON.parse(text);
        } catch (parseError) {
            console.error('服务器返回非 JSON:', text);
            alert('服务器返回异常，无法解析：\n' + text.slice(0, 200));
            return;
        }

        if (data.status === "success") {
            // 👇 因为后端字段变了，这里提示语也可以改得更丰富
            let msg = '登录成功！用户ID: ' + data.id;
            if (data.default_uids) {
                msg += '\n当前默认绑定的 4399 账号: ' + data.default_uids;
            }
            alert(msg);

            // 👇 存入 sessionStorage (字段名要跟后端对齐)
            sessionStorage.setItem("myUserId", data.id);
            sessionStorage.setItem("myName", data.name);

            // 存入默认绑定的 4399 账号和 Cookie
            sessionStorage.setItem("myDefaultUid", data.default_uids || "");
            sessionStorage.setItem("myDefaultCookie", data.default_cookie || "");

            // 存入所有的绑定账号列表 (转成字符串存)
            sessionStorage.setItem("myAccounts", JSON.stringify(data.accounts || []));

            // 跳转到主页
            window.location.href = 'main.html';
        } else {
            // 登录失败，弹出后端的报错信息
            alert(data.message || '账号或密码错误');
        }
    } catch (error) {
        console.error(error);
        alert('网络请求失败，请检查后台是否启动');
    }
});

// ==================== 2：注册 ====================
registerForm.addEventListener('submit', async function (e) {
    e.preventDefault();

    const user = regUser.value.trim();
    const pwd  = regPwd.value;

    const userRule = /^[a-zA-Z][a-zA-Z0-9]{1,5}$/;
    const pwdOk = pwd.length >= 6;

    if (!userRule.test(user)) {
        alert('账号格式不正确：需字母开头，2~6位');
        return;
    }
    if (!pwdOk) {
        alert('密码不能少于6位');
        return;
    }

    try {
        const response = await fetch('api/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `username=${encodeURIComponent(user)}&password=${encodeURIComponent(pwd)}`
        });

        // 注册接口后端返回的是纯文本
        const result = await response.text();

        if (result.includes("成功")) {
            alert('注册成功！请登录');
            regUser.value = '';
            regPwd.value  = '';
            registerForm.hidden = true;
            loginForm.hidden    = false;
        } else {
            alert(result);
        }
    } catch (error) {
        console.error(error);
        alert('网络请求失败');
    }
});

// ==================== 3：忘记密码 ====================
forgotLink.addEventListener('click', function (e) {
    e.preventDefault();
    alert('请联系管理员重置密码');
});

// ==================== 登录 / 注册 切换 ====================
toRegister.addEventListener('click', function (e) {
    e.preventDefault();
    loginForm.hidden    = true;
    registerForm.hidden = false;
});

toLogin.addEventListener('click', function (e) {
    e.preventDefault();
    registerForm.hidden = true;
    loginForm.hidden    = false;
});