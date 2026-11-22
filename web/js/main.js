// 在页面加载时执行
window.onload = function() {
    // 1. 获取刚才存入的数据
    const userName = sessionStorage.getItem("myName");
    const defaultUid = sessionStorage.getItem("myDefaultUid");

    const statusEl = document.getElementById("loginStatusText");

    document.getElementById("welcomeTitle").innerText = "欢迎回来，" + userName;

    if (defaultUid && defaultUid !== "null" && defaultUid !== "undefined" && defaultUid !== "") {
        statusEl.innerText = "登录成功,当前使用UID: " + defaultUid + "";
    } else {
        statusEl.innerText = "暂未登录(请点击下方扫码登录按钮进行登录)";
    }
    //退出登录
    document.getElementById("quit").addEventListener("click", function() {
        if (confirm("确定要退出登录吗？")) {
            sessionStorage.clear(); // 清空所有登录状态
            window.location.href = 'index.jsp'; // 跳回登录页
        }
    });
    document.getElementById("loading").addEventListener("click",function () {
        window.location.href = 'loading.html'; // 跳回登录页
    })
}