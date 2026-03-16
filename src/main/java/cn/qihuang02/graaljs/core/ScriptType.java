package cn.qihuang02.graaljs.core;

public enum ScriptType {
    STARTUP("startup_scripts"),
    SERVER("server_scripts"),
    CLIENT("client_scripts");

    public final String directory;

    ScriptType(String directory) {
        this.directory = directory;
    }
}
