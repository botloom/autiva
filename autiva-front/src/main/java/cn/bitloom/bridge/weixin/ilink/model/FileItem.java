package cn.bitloom.bridge.weixin.ilink.model;

import lombok.Data;

@Data
public class FileItem {
    private Object media;
    private String fileName;
    private String len;
    private String md5;
}
