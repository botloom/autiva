package cn.bitloom.autiva;

import cn.bitloom.autiva.web.AutivaApplication;
import com.microsoft.playwright.*;
import io.modelcontextprotocol.client.McpSyncClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

/**
 * Unit test for simple App.
 *
 * @author bitloom
 */
@SpringBootTest(classes = AutivaApplication.class)
public class TestAutivaApplication {

    @Autowired
    private List<McpSyncClient> mcpSyncClients;

    @Test
    public void test() {
        System.out.println("test");
    }

    public static void main(String[] args) throws InterruptedException {
        int total = 0;
        while (total < Integer.MAX_VALUE) {
            try (Playwright playwright = Playwright.create()) {
                Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
                BrowserContext context = browser.newContext();
                Page page = context.newPage();
                page.navigate("https://api3.cls.cn/iav/vote/elite?id=1308&activeid=8");
                Locator button = page.locator(".p-a.h-100p.detail-button-vote");
                int subTotal = 0;
                while (true) {
                    subTotal++;
                    button.click();
                    Thread.sleep(2000);
                    if (subTotal == 10) {
                        total += subTotal;
                        break;
                    }
                }
            }
        }

    }
}
