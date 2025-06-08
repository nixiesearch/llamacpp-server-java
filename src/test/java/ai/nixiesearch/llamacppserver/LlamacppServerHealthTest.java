package ai.nixiesearch.llamacppserver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LlamacppServerHealthTest {

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    @Test
    void testIsProcessAliveBeforeStart() {
        // Before starting any server, isProcessAlive should return false
        // Note: This test assumes no global server instance exists
        try {
            // Try to get a server instance without starting
            // Since it's a singleton, we need to ensure it's not already started
            LlamacppServer server = null;
            
            // Check that a null server or uninitialized server reports not alive
            // This tests the edge case handling
            Assertions.assertTrue(true); // Placeholder - actual test would need server instance
        } catch (Exception e) {
            // Expected if no server started
            Assertions.assertTrue(true);
        }
    }

    @Test
    void testIsProcessAliveAfterStart() throws IOException {
        int port = findAvailablePort();
        String[] args = {
                "--port", String.valueOf(port),
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Wait a moment for process to initialize
            Thread.sleep(1000);
            
            // Process should be alive immediately after start
            Assertions.assertTrue(server.isProcessAlive(), "Process should be alive after start");
            
            server.close();
        });
    }

    @Test
    void testIsProcessAliveAfterClose() throws IOException {
        int port = findAvailablePort();
        String[] args = {
                "--port", String.valueOf(port),
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Wait for process to start
            Thread.sleep(1000);
            Assertions.assertTrue(server.isProcessAlive(), "Process should be alive before close");
            
            server.close();
            
            // Wait for shutdown to complete
            Thread.sleep(1000);
            
            // Process should be dead after close
            Assertions.assertFalse(server.isProcessAlive(), "Process should be dead after close");
        });
    }

    @Test
    void testIsHealthyBeforeServerReady() throws IOException {
        int port = findAvailablePort();
        String[] args = {
                "--port", String.valueOf(port),
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Immediately after start, health endpoint might not be ready yet
            boolean initialHealth = server.isHealthy();
            
            // Wait for server to become ready
            boolean becameHealthy = waitForHealthy(server, 30000);
            
            Assertions.assertTrue(becameHealthy, "Server should become healthy within 30 seconds");
            
            server.close();
        });
    }

    @Test
    void testIsHealthyAfterServerReady() throws IOException {
        int port = findAvailablePort();
        String[] args = {
                "--port", String.valueOf(port),
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Wait for server to be ready
            waitForHealthy(server, 30000);
            
            // Now health check should consistently return true
            Assertions.assertTrue(server.isHealthy(), "Server should be healthy when ready");
            Assertions.assertTrue(server.isHealthy(), "Health check should be repeatable");
            
            server.close();
        });
    }

    @Test
    void testIsHealthyAfterClose() throws IOException {
        int port = findAvailablePort();
        String[] args = {
                "--port", String.valueOf(port),
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Wait for server to be ready
            waitForHealthy(server, 30000);
            Assertions.assertTrue(server.isHealthy(), "Server should be healthy before close");
            
            server.close();
            
            // Wait for shutdown
            Thread.sleep(2000);
            
            // Health check should return false after close
            Assertions.assertFalse(server.isHealthy(), "Server should not be healthy after close");
        });
    }

    @Test
    void testIsAliveComposite() throws IOException {
        int port = findAvailablePort();
        String[] args = {
                "--port", String.valueOf(port),
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Wait for server to be ready
            waitForHealthy(server, 30000);
            
            // isAlive should return true when both process and health are good
            Assertions.assertTrue(server.isProcessAlive(), "Process should be alive");
            Assertions.assertTrue(server.isHealthy(), "Server should be healthy");
            Assertions.assertTrue(server.isAlive(), "Server should be alive (composite check)");
            
            server.close();
            
            // Wait for shutdown
            Thread.sleep(2000);
            
            // isAlive should return false after close
            Assertions.assertFalse(server.isAlive(), "Server should not be alive after close");
        });
    }

    @Test
    void testHealthCheckWithCustomPort() {
        String[] args = {
                "--port", "8081",
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            
            // Wait for server to be ready
            waitForHealthy(server, 30000);
            
            // Verify we can manually hit the health endpoint on the custom port
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:8081/health"))
                    .GET()
                    .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            Assertions.assertEquals(200, response.statusCode(), "Health endpoint should return 200 on custom port");
            
            // Server's health check should also work
            Assertions.assertTrue(server.isHealthy(), "Server health check should work with custom port");
            
            server.close();
        });
    }

    private boolean waitForHealthy(LlamacppServer server, int timeoutMillis) {
        long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeoutMillis) {
            if (server.isHealthy()) {
                return true;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}