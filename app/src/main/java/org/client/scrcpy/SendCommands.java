package org.client.scrcpy;


import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import org.client.scrcpy.utils.ThreadUtils;

import java.io.File;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import dadb.AdbShellResponse;


public class SendCommands {

    private Context context;
    private int status;
    private Device device;


    public SendCommands() {
    }

    public int SendAdbCommands(Context context, final String ip, int port, int forwardport, String localip, int bitrate, int size) {
        this.context = context;
        status = 1;
        String[] commands = new String[]{
                "CLASSPATH=/data/local/tmp/scrcpy-server.jar",
                "app_process",
                "/",
                "org.server.scrcpy.Server",
                "/" + localip,
                Long.toString(size),
                Long.toString(bitrate) + ";"
        };
        this.device = new Device(ip, port, context);
        ThreadUtils.execute(() -> {
            try {
                newAdbServerStart(context, forwardport, commands);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        int count = 0;
        while (status == 1 && count < 50) {
            Log.e("ADB", "Connecting...");
            try {
                Thread.sleep(100);
                count++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (count >= 50) {
            status = 2;
            return status;
        }
        if (status == 0) {
            count = 0;
            //  检测程序是否已经启动，如果启动了，该文件会被删除
            while (status == 0 && count < 10) {
                String serverJarLsResponse = "";
                try{
                    serverJarLsResponse = this.device.invoke("ls -alh /data/local/tmp/scrcpy-server.jar").getOutput().trim();
                }catch(Exception e){
                }
                Log.i("Scrcpy", "Checking server jar exists or not");
                if (TextUtils.isEmpty(serverJarLsResponse)) {
                    break;
                } else {
                    try {
                        Thread.sleep(100);
                        count++;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return status;
    }


    private void newAdbServerStart(Context context, int forwardport, String[] commands) {
        try {
            this.device.attach();
            Log.i("Scrcpy", "connected device: " + this.device.invoke("getprop ro.product.model").getAllOutput().trim());
            // 复制server端到可执行目录
            this.device.upload(new File(context.getExternalFilesDir("scrcpy"), "scrcpy-server.jar"), "/data/local/tmp/scrcpy-server.jar");
            Log.i("Scrcpy", "Push successful");

            if (TextUtils.isEmpty(this.device.invoke("ls -alh /data/local/tmp/scrcpy-server.jar").getOutput().trim())) {
                status = 2;
                return;
            }

            // 开启本地端口 forward 转发
            Log.i("Scrcpy", "开启本地端口转发");
            this.device.forward(forwardport, 7007, String.join(" ", commands));
            status = 0;
            // 执行启动命令
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
