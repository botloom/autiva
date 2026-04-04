package cn.bitloom;

import com.alibaba.opensandbox.sandbox.Sandbox;
import com.alibaba.opensandbox.sandbox.domain.models.execd.executions.Execution;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = AutivaApplication.class)
public class TestApplication {
    @Test
    public void test() {
        // 2. 使用 try-with-resources 创建 Sandbox
        try (Sandbox sandbox = Sandbox.builder()
                .image("ubuntu:20.04")
                .build()) {

            // 3. 执行 Shell 命令
            Execution execution = sandbox
                    .commands()
                    .run("echo 'Hello Sandbox!'");

            // 4. 打印输出
            System.out.println(execution.getLogs().getStdout().get(0).getText());

            // 5. 清理资源 (自动调用 sandbox.close())
            // 注意: 如果希望立即终止远程沙箱实例，仍需显式调用 kill()
            sandbox.kill();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
