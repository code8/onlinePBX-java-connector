package info.code8.pbx;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertNotNull;

/**
 * Created by code8 on 12/11/15.
 */

public class OnlinePBXTest {
    private static OnlinePBXConnector connector;

    @BeforeClass
    public static void setup() throws IOException {
        connector = new OnlinePBXConnector(null, null);
    }

    @Test
    public void secretKeyTest() {
        AuthKey authKey = connector.getAuthKey();
        assertNotNull(authKey);
    }
}
