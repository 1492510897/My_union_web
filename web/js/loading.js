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

// ==================== 需求 1：登录 ====================
loginForm.addEventListener('submit', async function (e) {
    e.preventDefault(); // 阻止表单默认刷新

    const user = loginUser.value.trim();
    const pwd  = loginPwd.value;

    // 1) 空值判断
    if (!user || !pwd) {
        alert('账号或密码不能为空');
        return;
    }

    // 2) 发送请求到后端 Servlet
    try {
        const response = await fetch('/api/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `username=${encodeURIComponent(user)}&password=${encodeURIComponent(pwd)}`
        });

        const result = await response.text(); // 接收后端返回的字符串（比如 "登录成功"）

        if (result.includes("成功")) {
            alert('用户登录成功！');
            // 登录成功后的跳转：
            // location.href = 'main.jsp';
        } else {
            alert(result); // 如果失败，把后端返回的报错弹出来
        }
    } catch (error) {
        console.error(error);
        alert('网络请求失败，请检查 Tomcat 是否启动');
    }
});

// ==================== 需求 2：注册 ====================
registerForm.addEventListener('submit', async function (e) {
    e.preventDefault();

    const user = regUser.value.trim();
    const pwd  = regPwd.value;

    // 账号规则：字母开头 + 2~6 位字母数字组合
    const userRule = /^[a-zA-Z][a-zA-Z0-9]{1,5}$/;
    // 密码：不少于 6 位
    const pwdOk = pwd.length >= 6;

    if (!userRule.test(user)) {
        alert('账号格式不正确：需字母开头，2~6位');
        return;
    }
    if (!pwdOk) {
        alert('密码不能少于6位');
        return;
    }

    // 发送请求到后端
    try {
        const response = await fetch('/api/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `username=${encodeURIComponent(user)}&password=${encodeURIComponent(pwd)}`
        });

        const result = await response.text();

        if (result.includes("成功")) {
            alert('注册成功！请登录');
            // 清空表单并切回登录页
            regUser.value = '';
            regPwd.value  = '';
            registerForm.hidden = true;
            loginForm.hidden    = false;
        } else {
            alert(result); // 提示失败原因（比如用户名已存在）
        }
    } catch (error) {
        console.error(error);
        alert('网络请求失败');
    }
});

// ==================== 需求 3：忘记密码 ====================
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