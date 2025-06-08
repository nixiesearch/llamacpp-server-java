package ai.nixiesearch.llamacppserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LlamacppServer implements AutoCloseable {
    public Process process;
    public File workdir;
    public CompletableFuture<Void> logStream;
    private int port = 8080; // Default port

    private static final Logger logger = LoggerFactory.getLogger(LlamacppServer.class);

    private static String[] CPU_LIBS = {"libggml.so", "libggml-base.so", "libggml-cpu.so", "libllama.so", "llama-server", "libmtmd.so"};
    private static String[] CUDA_LIBS = {"libggml.so", "libggml-base.so", "libggml-cpu.so", "libggml-cuda.so", "libllama.so", "llama-server", "libmtmd.so"};

    volatile private static boolean isStarted = false;
    private static LlamacppServer instance = null;
    private static List<File> unpackedFiles = new ArrayList<>();

    public enum LLAMACPP_BACKEND {
        GGML_CPU,
        GGML_CUDA12
    }

    LlamacppServer(Process process, File workdir, CompletableFuture<Void> logStream, int port) {
        this.process = process;
        this.workdir = workdir;
        this.logStream = logStream;
        this.port = port;
    }

    /**
     * Starts a new LlamacppServer instance with the given arguments and backend.
     * Only one instance can be running at a time (singleton pattern).
     * 
     * @param args command line arguments to pass to llama-server
     * @param backend the backend type (CPU or CUDA)
     * @return the LlamacppServer instance
     * @throws IOException if server startup fails
     * @throws InterruptedException if startup is interrupted
     */
    public synchronized static LlamacppServer start(String[] args, LLAMACPP_BACKEND backend) throws IOException, InterruptedException {
        if (!isStarted) {
            isStarted = true;
            File workdir = unpack(backend);
            ProcessBuilder builder = new ProcessBuilder();
            List<String> commandArgs = new ArrayList<>();
            commandArgs.add(workdir + "/llama-server");
            commandArgs.addAll(Arrays.asList(args));
            builder.command(commandArgs);
            builder.redirectErrorStream(true);
            builder.directory(workdir);
            Process process = builder.start();
            CompletableFuture<Void> logStream = CompletableFuture.runAsync(() -> {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                            String line;
                            while (process.isAlive() && (line = reader.readLine()) != null) {
                                logger.info(line);
                            }
                        } catch (IOException e) {
                            if (process.isAlive()) {
                                logger.error("Log stream error: {}", e.getMessage(), e);
                            } else {
                                logger.debug("Log stream closed during shutdown: {}", e.getMessage());
                            }
                        } catch (Exception e) {
                            logger.error("Unexpected error in log stream: {}", e.getMessage(), e);
                        }
                    }
            );
            int port = extractPortFromArgs(args);
            instance = new LlamacppServer(process, workdir, logStream, port);
            return instance;
        } else {
            logger.warn("Called LlamacppServer.start for the second time - it seems like a bug");
            return instance;
        }
    }

    /**
     * Checks if the server is alive by verifying both process status and HTTP health endpoint.
     * 
     * @return true if process is running and /health endpoint returns 200
     */
    public boolean isAlive() {
        return isProcessAlive() && isHealthy();
    }

    /**
     * Checks if the underlying llama-server process is running.
     * 
     * @return true if the process exists and is alive
     */
    public boolean isProcessAlive() {
        return process != null && process.isAlive();
    }

    /**
     * Performs an HTTP health check against the server's /health endpoint.
     * 
     * @return true if the HTTP endpoint returns status 200
     */
    public boolean isHealthy() {
        if (!isProcessAlive()) {
            return false;
        }
        
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(2))
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + "/health"))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            logger.debug("Health check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Stops the server process and cleans up all resources including temporary files.
     * This method is thread-safe and can be called multiple times safely.
     * 
     * @throws Exception if shutdown fails
     */
    @Override
    public synchronized void close() throws Exception {
        if (isStarted) {
            stop();
            for (File unpacked: unpackedFiles) {
                if (unpacked.exists()) {
                    logger.info("Deleting temp file {}", unpacked);
                    unpacked.delete();
                }
            }
            isStarted = false;
            instance = null;
        } else {
            logger.warn("Called LlamacppServer.close over a closed server - it seems like a bug");
        }
    }

    private void stop() throws IOException, InterruptedException, ExecutionException {
        if (process.isAlive()) {
            logger.info("Waiting for running llamacpp-server to stop...");
            process.destroy();
            boolean exited = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
            if (!exited) {
                logger.warn("Process did not exit gracefully, forcing termination");
                process.destroyForcibly();
                process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
            }
            
            try {
                logStream.get(2, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                logger.debug("Log stream did not complete within timeout, continuing shutdown");
            }
            logger.info("llamacpp-server stopped");
        } else {
            logger.info("llamacpp-server is already stopped");
            try {
                logStream.get(1, java.util.concurrent.TimeUnit.SECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                logger.debug("Log stream cleanup timeout, continuing");
            }
        }

    }

    private static synchronized File unpack(LLAMACPP_BACKEND backend) throws IOException {
        String tmp = System.getProperty("java.io.tmpdir");
        File workdir = new File(tmp + File.separator + "llamacpp");
        if (!workdir.exists() && !workdir.mkdirs()) {
            throw new IOException("Cannot create temp dir "+workdir);
        } else {
            String os = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
            String arch = System.getProperty("os.arch", "generic").toLowerCase(Locale.ENGLISH);
            logger.info("Unpacking llamacpp-server for os={} arch={}", os, arch);
            if (os.startsWith("linux")) {
                if (arch.startsWith("amd64") || arch.startsWith("x86_64")) {
                    if (backend == LLAMACPP_BACKEND.GGML_CPU) {
                        unpackResourceList(workdir, "native/linux/x86_64/cpu", CPU_LIBS);
                    } else if (backend == LLAMACPP_BACKEND.GGML_CUDA12) {
                        unpackResourceList(workdir, "native/linux/x86_64/cu12", CUDA_LIBS);
                    }
                } else if (arch.startsWith("aarch64") || arch.startsWith("arm64")) {
                    if (backend == LLAMACPP_BACKEND.GGML_CPU) {
                        unpackResourceList(workdir,"native/linux/arm64/cpu", CPU_LIBS);
                    } else if (backend == LLAMACPP_BACKEND.GGML_CUDA12) {
                        throw new IOException("CUDA on arm64 is not supported");
                    }
                } else {
                    throw new IOException("Only aarch64/arm64 and x86_64 are supported");
                }
            } else {
                throw new IOException("Sorry, we only yet support linux builds");
            }
        }
        return workdir;
    }

    private static int extractPortFromArgs(String[] args) {
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i]) || "-p".equals(args[i])) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException e) {
                    logger.warn("Invalid port number in args: {}", args[i + 1]);
                }
            }
        }
        return 8080; // Default port
    }

    private static void unpackResourceList(File workdir, String resourceDir, String[] resourcePaths) throws IOException {
        for (String fileName: resourcePaths) {
            File dest = new File(workdir.toString() + File.separator + fileName);
            String path = resourceDir + "/" + fileName;
            if (!dest.exists()) {
                logger.info("Extracting native lib {} to {}", path, dest);
                InputStream libStream = LlamacppServer.class.getClassLoader().getResourceAsStream(path);
                OutputStream fileStream = Files.newOutputStream(dest.toPath());
                copyStream(libStream, fileStream);
                libStream.close();
                fileStream.close();
                dest.setExecutable(true);
                unpackedFiles.add(dest);
            } else {
                logger.info("Native lib {} already exists at {}", path, dest);
            }
        }
    }


    private static void copyStream(InputStream source, OutputStream target) throws IOException {
        byte[] buf = new byte[8192];
        int length;
        int bytesCopied = 0;
        while ((length = source.read(buf)) > 0) {
            target.write(buf, 0, length);
            bytesCopied += length;
        }
        logger.debug("Copied {} bytes", bytesCopied);
    }

}
