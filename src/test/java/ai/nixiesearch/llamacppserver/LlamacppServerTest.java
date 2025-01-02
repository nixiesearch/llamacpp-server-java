package ai.nixiesearch.llamacppserver;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LlamacppServerTest {
    @Test
    void testUnpack() {
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(new String[]{"--hf-repo", "Qwen/Qwen2.5-0.5B-Instruct-GGUF", "--hf-file", "qwen2.5-0.5b-instruct-q4_0.gguf"}, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            Thread.sleep(40000);
            server.stop();
        });
    }
}
