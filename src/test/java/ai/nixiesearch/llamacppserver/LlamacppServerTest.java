package ai.nixiesearch.llamacppserver;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LlamacppServerTest {
    @Test
    void testUnpack() {
        Assertions.assertDoesNotThrow(() -> {
            LlamacppServer server = LlamacppServer.start(new String[]{"-h"}, LlamacppServer.LLAMACPP_BACKEND.GGML_CPU);
            //server.process.waitFor();
            server.stop();
        });
    }
}
