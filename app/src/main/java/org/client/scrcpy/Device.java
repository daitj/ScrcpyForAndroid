package org.client.scrcpy;
import android.content.Context;

import android.util.Log;

import java.io.File;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dadb.AdbKeyPair;
import dadb.AdbShellResponse;
import dadb.Dadb;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributes;
import java.io.IOException;

public class Device {
    private final String host;
    private final int port;
    private final Context context;
    private Dadb manager;
    private final ExecutorService executorService;

    public Device(String host, int port, Context context) {
        this.host = host;
        this.port = port;
        this.context = context;
        this.executorService = Executors.newSingleThreadExecutor();
    }

    public void attach() throws Exception {
        
        Future<Dadb> future = executorService.submit(() -> Dadb.create(host, port, keygen(false)));
        try {
            manager = future.get(5000, java.util.concurrent.TimeUnit.MILLISECONDS);
            try {
                if (!"success\n".equals(manager.shell("echo success").getAllOutput())) {
                    throw new Exception("First connection to device shell failed");
                }
            } catch (Throwable e) {
                throw new Exception("Error occurred when connecting to device", e);
            }
        } catch (ExecutionException e) {
            throw new Exception("Target machine is waiting for authorization, authorize and retry", e);
        } catch (java.util.concurrent.TimeoutException e) {
            future.cancel(true);
            throw new Exception("Timeout while trying to attach to the device", e);
        }
    }

    public void detach() {
        if (manager != null) {
            try {
                manager.close();
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public String gather(String distant) throws Exception {
        File created = new File(context.getCacheDir(), new File(distant).getName());
        manager.pull(new File(distant), created.getPath());
        return created.exists() ? created.getPath() : null;
    }

    public AdbShellResponse invoke(String command) throws Exception {
        return manager.shell(command);
    }

    public AdbKeyPair keygen(boolean refresh) throws Exception {
        File deposit = context.getCacheDir();
        File pvtFile = new File(deposit, "adbkey");
        File pubFile = new File(deposit, "adbkey.pub");
        if (refresh || !pubFile.exists() || !pvtFile.exists()) {
            if (pubFile.exists()) pubFile.delete();
            if (pvtFile.exists()) pvtFile.delete();
            AdbKeyPair.generate(pvtFile, pubFile);
        }
        return AdbKeyPair.read(pvtFile, pubFile);
    }
    public void forward(int localPort, int serverPort, String invokeAfterStart) throws Exception {
        try (AutoCloseable forwarder = manager.tcpForward(localPort, serverPort)) {
            Log.i("Scrcpy", "TCP fowarded, running server in the device");
            manager.shell(invokeAfterStart);
            Log.i("Scrcpy", "Ran server in the device");
            while (true) {
                try {
                    Log.d("Scrcpy TCP Forward", "Sleeping");
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Log.d("Scrcpy TCP Forward", "Interrupted", e);
                    Thread.currentThread().interrupt(); 
                    break; 
                }
            }
            return; 
        } catch (Exception e) {
            Log.d("Scrcpy TCP Forward", "Exception", e);
        }
    }
    public void upload(File file, String distant) throws Exception {
        manager.push(file, distant, readMode(file), file.lastModified());
    }

    private static int readMode(File file) {
        try {
            Path path = file.toPath();
            PosixFileAttributes attrs = Files.readAttributes(path, PosixFileAttributes.class);
            return attrs.permissions().stream()
                    .mapToInt(permission -> permission.ordinal())
                    .reduce(0, (a, b) -> a | b); // Combine permissions into a single integer
        } catch (IOException e) {
            throw new RuntimeException("Unable to read file mode", e);
        }
    }
    public void shutdown() {
        executorService.shutdown();
    }
}