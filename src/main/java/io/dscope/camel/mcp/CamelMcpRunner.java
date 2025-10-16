package io.dscope.camel.mcp;

import org.apache.camel.main.Main;

public class CamelMcpRunner {
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.configure().setRoutesIncludePattern("file:src/test/resources/routes/example-mcp.yaml");
        main.run();
    }
}
