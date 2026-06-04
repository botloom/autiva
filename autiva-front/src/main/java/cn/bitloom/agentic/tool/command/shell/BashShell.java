package cn.bitloom.agentic.tool.command.shell;

import java.util.List;

public class BashShell extends AbstractPosixShell {

    public BashShell(String shellPath) {
        super(shellPath);
    }

    @Override
    public String name() {
        return "Bash";
    }

    @Override
    protected List<String> shellArgs() {
        return List.of("--noprofile", "--norc");
    }
}
