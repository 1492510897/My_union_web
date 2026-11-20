package com.web;

import gen_java.UserApi;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@WebServlet("/api/login")
public class LoginServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        resp.setContentType("text/plain;charset=UTF-8");

        String username = req.getParameter("username");
        String password = req.getParameter("password");

        TTransport transport = null;
        try {
            transport = new TSocket("127.0.0.1", 9090, 3000);
            transport.open();
            UserApi.Client client = new UserApi.Client(new TBinaryProtocol(transport));
            
            String result = client.loginLocal(username, password);
            resp.getWriter().write(result);
        } catch (Exception e) {
            e.printStackTrace();
            resp.getWriter().write("网络异常: 无法连接 Thrift Server");
        } finally {
            if (transport != null && transport.isOpen()) transport.close();
        }
    }
}