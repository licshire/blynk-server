package cc.blynk.integration.tcp;

import cc.blynk.integration.IntegrationBase;
import cc.blynk.integration.model.tcp.ClientPair;
import cc.blynk.integration.model.tcp.TestAppClient;
import cc.blynk.integration.model.tcp.TestHardClient;
import cc.blynk.server.core.model.DashBoard;
import cc.blynk.server.core.protocol.model.messages.ResponseMessage;
import cc.blynk.server.core.protocol.model.messages.common.HardwareMessage;
import cc.blynk.server.servers.BaseServer;
import cc.blynk.server.servers.application.AppAndHttpsServer;
import cc.blynk.server.servers.hardware.HardwareAndHttpAPIServer;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import static cc.blynk.server.core.protocol.enums.Response.DEVICE_NOT_IN_NETWORK;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The Blynk Project.
 * Created by Dmitriy Dumanskiy.
 * Created on 5/09/2016.
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class MultiAppTest extends IntegrationBase {

    private BaseServer httpServer;
    private BaseServer appServer;
    private BaseServer hardwareServer;
    private AsyncHttpClient httpclient;
    private String httpServerUrl;
    private ClientPair clientPair;

    @Before
    public void init() throws Exception {
        httpServer = new HardwareAndHttpAPIServer(holder).start();
        appServer = new AppAndHttpsServer(holder).start();
        httpServerUrl = String.format("http://localhost:%s/", httpPort);

        httpclient = new DefaultAsyncHttpClient(
                new DefaultAsyncHttpClientConfig.Builder()
                        .setUserAgent("")
                        .setKeepAlive(true)
                        .build());

        if (clientPair == null) {
            clientPair = initAppAndHardPair(tcpAppPort, tcpHardPort, properties);
        }
        clientPair.hardwareClient.reset();
        clientPair.appClient.reset();
    }

    @After
    public void shutdown() {
        httpServer.close();
        appServer.close();
        clientPair.stop();
    }

    @Test
    public void testCreateFewAccountWithDifferentApp() throws Exception {
        TestAppClient appClient1 = new TestAppClient("localhost", tcpAppPort, properties);
        appClient1.start();
        TestAppClient appClient2 = new TestAppClient("localhost", tcpAppPort, properties);
        appClient2.start();

        String token1 = workflowForUser(appClient1, "test@blynk.cc", "a", "testapp1");
        String token2 = workflowForUser(appClient2, "test@blynk.cc", "a", "testapp2");

        appClient1.reset();
        appClient2.reset();

        TestHardClient hardClient1 = new TestHardClient("localhost", tcpHardPort);
        hardClient1.start();
        TestHardClient hardClient2 = new TestHardClient("localhost", tcpHardPort);
        hardClient2.start();

        hardClient1.login(token1);
        verify(hardClient1.responseMock, timeout(2000)).channelRead(any(), eq(ok(1)));
        verify(appClient1.responseMock, timeout(2000)).channelRead(any(), eq(hardwareConnected(1, "1-0")));
        hardClient2.login(token2);
        verify(hardClient2.responseMock, timeout(2000)).channelRead(any(), eq(ok(1)));
        verify(appClient2.responseMock, timeout(2000)).channelRead(any(), eq(hardwareConnected(1, "1-0")));

        hardClient1.send("hardware vw 1 100");
        verify(appClient1.responseMock, timeout(2000)).channelRead(any(), eq(new HardwareMessage(2, b("1-0 vw 1 100"))));
        verify(appClient2.responseMock, timeout(500).times(0)).channelRead(any(), eq(new HardwareMessage(1, b("1-0 vw 1 100"))));

        appClient1.reset();
        appClient2.reset();

        hardClient2.send("hardware vw 1 100");
        verify(appClient2.responseMock, timeout(2000)).channelRead(any(), eq(new HardwareMessage(2, b("1-0 vw 1 100"))));
        verify(appClient1.responseMock, timeout(500).times(0)).channelRead(any(), eq(new HardwareMessage(1, b("1-0 vw 1 100"))));

    }

    private String workflowForUser(TestAppClient appClient, String email, String pass, String appName) throws Exception{
        appClient.register(email, pass, appName);
        verify(appClient.responseMock, timeout(1000)).channelRead(any(), eq(ok(1)));
        appClient.login(email, pass, "Android", "1.10.4", appName);
        //we should wait until login finished. Only after that we can send commands
        verify(appClient.responseMock, timeout(1000)).channelRead(any(), eq(ok(2)));

        DashBoard dash = new DashBoard();
        dash.id = 1;
        dash.name = "test";
        appClient.createDash(dash);
        verify(appClient.responseMock, timeout(1000)).channelRead(any(), eq(ok(3)));
        appClient.activate(1);
        verify(appClient.responseMock, timeout(1000)).channelRead(any(), eq(new ResponseMessage(4, DEVICE_NOT_IN_NETWORK)));

        appClient.reset();
        appClient.getToken(1);

        String token = appClient.getBody();
        assertNotNull(token);
        return token;
    }

}
