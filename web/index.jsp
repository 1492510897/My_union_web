
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>登录界面</title>
    <link rel="stylesheet" href="css/index.css">
</head>
<body>
<div class="container">

    <!-- ===== 登录卡片 ===== -->
    <form class="login-box" id="loginForm">
        <h2>登录</h2>
        <input type="text"     id="loginUser" placeholder="用户名" autocomplete="username">
        <input type="password" id="loginPwd"  placeholder="密码"   autocomplete="current-password">

        <button type="submit">登录</button>

        <p class="links">
            <a href="#" id="forgotLink">忘记密码</a>
            <a href="#" id="toRegister">注册</a>
        </p>
    </form>

    <!-- ===== 注册卡片 ===== -->
    <form class="login-box" id="registerForm" hidden>
        <h2>注册</h2>
        <input type="text"     id="regUser" placeholder="用户名（字母开头，2-6位字母数字）">
        <input type="password" id="regPwd"  placeholder="密码（不少于6位）">
        <button type="submit">注册</button>

        <p class="links">
            <a href="#" id="toLogin">返回登录</a>
        </p>
    </form>

</div>
<script src="js/index.js"></script>
</body>
</html>
