package cn.bitloom.bridge.wechat.ilink.model;

import lombok.Data;

@Data
public class FileItem {
    private Object media;
    private String fileName;
    private String len;
    private String md5;
}
