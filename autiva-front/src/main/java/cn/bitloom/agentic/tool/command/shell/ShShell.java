package cn.bitloom.agentic.tool.command.shell;

import java.util.Collections;
import java.util.List;

public class ShShell extends AbstractPosixShell {

    public ShShell(String shellPath) {
        super(shellPath);
    }

    @Override
    public String name() {
        return "POSIX sh";
    }

    @Override
    protected List<String> shellArgs() {
        return Collections.emptyList();
    }
}
