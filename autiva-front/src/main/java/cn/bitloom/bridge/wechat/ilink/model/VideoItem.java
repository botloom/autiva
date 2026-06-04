package cn.bitloom.bridge.wechat.ilink.model;

import lombok.Data;

@Data
public class VideoItem {
    private Object media;
    private Long videoSize;
    private Integer playLength;
    private String videoMd5;
}
