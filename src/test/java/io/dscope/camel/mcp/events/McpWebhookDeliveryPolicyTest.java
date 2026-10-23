package io.dscope.camel.mcp.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class McpWebhookDeliveryPolicyTest {
    @Test void receiptsAreClassifiedWithoutFalseFailureLabels() {
        var policy=new McpWebhookDeliveryPolicy(8);
        for(int status:new int[]{200,202,204,299}) {var d=policy.receipt(status,1);assertNull(d.failure());assertEquals(-1,d.retryDelayMs());}
        for(int status:new int[]{400,401,403,410,413}) assertEquals(-1,policy.receipt(status,1).retryDelayMs());
        for(int status:new int[]{408,429,500,503}) {
            assertTrue(policy.receipt(status,1).retryDelayMs()>=4000);
            assertEquals(-1,policy.receipt(status,8).retryDelayMs());
        }
        assertEquals(-1,policy.unavailableDelay(8));assertEquals(8,policy.maximumAttempts());
    }
    @Test void backoffIsBoundedAndConfigurationValidated() {
        var policy=new McpWebhookDeliveryPolicy(8);
        for(int i=0;i<100;i++) {long delay=policy.retryDelay(20);assertTrue(delay>=2880000&&delay<=3600000);}
        assertThrows(IllegalArgumentException.class,()->new McpWebhookDeliveryPolicy(0));
    }
}
