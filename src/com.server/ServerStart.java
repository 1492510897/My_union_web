package com.server;

import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TThreadPoolServer;
import org.apache.thrift.transport.TServerSocket;
import org.apache.thrift.transport.TServerTransport;
import gen_java.UserApi; // 注意这里导的是 UserApi

public class ServerStart {
    public static void main(String[] args) {
        try {
            TServerTransport serverTransport = new TServerSocket(9090);
            // 绑定 UserServerImpl
            UserApi.Processor<UserServerImpl> processor = new UserApi.Processor<>(new UserServerImpl());
            TServer server = new TThreadPoolServer(new TThreadPoolServer.Args(serverTransport).processor(processor));

            System.out.println("🚀 User Thrift Server 启动成功，正在监听 9090 端口...");
            server.serve();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}