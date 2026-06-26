package cn.bitloom.node.terminal;

import com.techsenger.jeditermfx.core.TtyConnector;
import com.techsenger.jeditermfx.core.util.TermSize;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

/**
 * PtySession 与 JediTermFX 之间的 TtyConnector 适配器
 * 将 PtySession 的输入输出适配为 JediTermFX 要求的 TtyConnector 接口
 */
@Slf4j
public class PtySessionTtyConnector implements TtyConnector {

    private final PtySession session;
    private final Reader reader;

    public PtySessionTtyConnector(@NotNull PtySession session) {
        this.session = session;
        InputStream inputStream = session.getInputStream();
        this.reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
    }

    @Override
    public int read(char[] buf, int offset, int length) throws IOException {
        return reader.read(buf, offset, length);
    }

    @Override
    public void write(byte[] bytes) throws IOException {
        session.writeInput(new String(bytes, StandardCharsets.UTF_8));
    }

    @Override
    public void write(String string) throws IOException {
        session.writeInput(string);
    }

    @Override
    public boolean isConnected() {
        return session.isAlive();
    }

    @Override
    public void resize(@NotNull TermSize termSize) {
        session.setWinSize(termSize.getColumns(), termSize.getRows());
    }

    @Override
    public int waitFor() throws InterruptedException {
        while (session.isAlive()) {
            Thread.sleep(50);
        }
        try {
            return session.getExitCode();
        } catch (Exception e) {
            log.debug("获取退出码失败，返回 0", e);
            return 0;
        }
    }

    @Override
    public boolean ready() throws IOException {
        return reader.ready();
    }

    @Override
    public String getName() {
        return "PtySession-" + session.getSessionId();
    }

    @Override
    public void close() {
        session.close();
    }
}
