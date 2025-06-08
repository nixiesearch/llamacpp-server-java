package ai.nixiesearch.llamacppserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class LlamacppServerRaceConditionTest {

    @RepeatedTest(10)
    void testRapidStartStop() {
        String[] args = {
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Wait briefly for server to start
            Thread.sleep(2000);
            
            // Immediately close - this should not throw stream closed exception
            server.close();
        });
    }

    @Test
    void testMultipleRapidStartStopCycles() {
        String[] args = {
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            for (int i = 0; i < 3; i++) {
                LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
                Thread.sleep(1000);
                server.close();
                Thread.sleep(500);
            }
        });
    }

    @Test
    void testConcurrentStartAndStop() {
        String[] args = {
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Start multiple concurrent operations
            CompletableFuture<Void> closer = CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(100);
                    server.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            
            CompletableFuture<Void> healthChecker = CompletableFuture.runAsync(() -> {
                var client = HttpClient.newHttpClient();
                var request = HttpRequest.newBuilder(URI.create("http://localhost:8080/health")).build();
                
                for (int i = 0; i < 5; i++) {
                    try {
                        client.send(request, HttpResponse.BodyHandlers.ofString());
                        Thread.sleep(50);
                    } catch (Exception e) {
                        // Expected as server is shutting down
                    }
                }
            });
            
            // Wait for both to complete without exceptions
            CompletableFuture.allOf(closer, healthChecker).get(30, TimeUnit.SECONDS);
        });
    }

    @Test
    void testForceKillScenario() {
        String[] args = {
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Wait for server to start properly
            Thread.sleep(3000);
            
            // Forcefully kill the underlying process to simulate crash
            if (server.process.isAlive()) {
                server.process.destroyForcibly();
            }
            
            // Now try to close normally - should handle dead process gracefully
            server.close();
        });
    }

    private boolean waitTillPortOpened(int millis, int port) throws IllegalStateException, IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/health")).build();
        boolean isOpen = false;
        long startMillis = System.currentTimeMillis();
        while (!isOpen && (System.currentTimeMillis() - startMillis < millis)) {
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    isOpen = true;
                } else {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                Thread.sleep(1000);
            }
        }
        return isOpen;
    }
}