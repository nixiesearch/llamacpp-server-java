package ai.nixiesearch.llamacppserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class LlamacppServer implements AutoCloseable {
    public Process process;
    public File workdir;
    public CompletableFuture<Void> logStream;

    private static final Logger logger = LoggerFactory.getLogger(LlamacppServer.class);

    private static String[] CPU_LIBS = {"libggml.so", "libggml-base.so", "libggml-cpu.so", "libllama.so", "llama-server"};
    private static String[] CUDA_LIBS = {"libggml.so", "libggml-base.so", "libggml-cpu.so", "libggml-cuda.so", "libllama.so", "llama-server"};

    volatile private static boolean isStarted = false;
    private static LlamacppServer instance = null;
    private static List<File> unpackedFiles = new ArrayList<>();

    public enum LLAMACPP_BACKEND {
        GGML_CPU,
        GGML_CUDA12
    }

    LlamacppServer(Process process, File workdir, CompletableFuture<Void> logStream) {
        this.process = process;
        this.workdir = workdir;
        this.logStream = logStream;
    }

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
                            while ((line = reader.readLine()) != null) {
                                logger.info(line);
                            }
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
            );
            instance = new LlamacppServer(process, workdir, logStream);
            return instance;
        } else {
            logger.warn("Called LlamacppServer.start for the second time - it seems like a bug");
            return instance;
        }
    }

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
            logger.info("Stopping running llamacpp-server");
            process.destroy();
        } else {
            logger.info("llamacpp-server is already stopped");
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
