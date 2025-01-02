package ai.nixiesearch.llamacppserver;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class LlamacppServerTest {
    @Test
    void testUnpack() {
        String[] args = {
                "--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"
        };
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(args, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            waitTillPortOpened(60000, 8080);
            server.stop();
        });
    }

    private boolean waitTillPortOpened(int millis, int port) throws IllegalStateException, IOException, InterruptedException {
        var client = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:8080/health")).build();
        boolean isOpen = false;
        long startMillis = System.currentTimeMillis();
        while (!isOpen && (System.currentTimeMillis() - startMillis < millis)) {
            try {
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    System.out.println("Got 200 code, port open");
                    isOpen = true;
                } else {
                    Thread.sleep(1000);
                }
            } catch (Exception e) {
                System.out.println("Waiting "+(System.currentTimeMillis() - startMillis)+" millis (Exception: "+e.getMessage()+")");
                Thread.sleep(1000);
            }
        }
        return isOpen;
    }
}
